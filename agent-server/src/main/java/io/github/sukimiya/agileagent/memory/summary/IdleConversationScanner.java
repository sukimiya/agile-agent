package io.github.sukimiya.agileagent.memory.summary;

import java.util.function.BooleanSupplier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.SmartLifecycle;
import org.springframework.core.NestedExceptionUtils;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Component;

import io.github.sukimiya.agileagent.memory.InstanceGuard;
import io.github.sukimiya.agileagent.memory.MemoryMetrics;
import io.github.sukimiya.agileagent.memory.MemoryProperties;
import io.github.sukimiya.agileagent.memory.MemoryScope;
import io.github.sukimiya.agileagent.memory.jobs.JobType;
import io.github.sukimiya.agileagent.memory.jobs.MemoryJobQueue;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * The idle trigger of turn memory records (M2a Task 4 / M2b D10+D19). Every
 * {@code app.memory.summary.idle-scan-interval} (default 30s) it looks for conversations that are idle: live, no
 * active turn, no turn created or finished within the effective cutoff {@code min(idle-timeout, idle-deadline)}
 * (defaults 1m / 30m, database time; a recent failed or cancelled turn also counts as a new message), and with
 * completed messages after the record position. Each one gets a SUMMARIZE job with dedupe key
 * {@code summarize:<conversationId>:<last completed sequence>}: one job per idle period (D19); M2b judgement
 * shares this idle detection (no accumulate-4-turns trigger). At most {@code idle-scan-batch} conversations per
 * scan. Runs only with {@code app.memory.summary.enabled=true} (default off, D4) and pauses while the
 * single-instance guard reports a peer.
 */
@Component
public class IdleConversationScanner implements SmartLifecycle {

    private static final Logger log = LoggerFactory.getLogger(IdleConversationScanner.class);

    private final DatabaseClient db;
    private final MemoryJobQueue queue;
    private final MemoryProperties props;
    private final MemoryMetrics metrics;
    private final BooleanSupplier conflict;
    private volatile Disposable schedule;

    @Autowired
    public IdleConversationScanner(DatabaseClient db, MemoryJobQueue queue, MemoryProperties props,
            MemoryMetrics metrics, InstanceGuard guard) {
        this(db, queue, props, metrics, guard::conflict);
    }

    /** {@code conflict} answers whether scanning must pause (another live instance in the namespace). */
    public IdleConversationScanner(DatabaseClient db, MemoryJobQueue queue, MemoryProperties props,
            MemoryMetrics metrics, BooleanSupplier conflict) {
        this.db = db;
        this.queue = queue;
        this.props = props;
        this.metrics = metrics;
        this.conflict = conflict;
    }

    private record Idle(MemoryScope scope, String conversationId, long lastSequence, long turns,
            long summarizedThrough, long judgedThrough) {}

    /** One scan; emits how many jobs were newly enqueued. */
    public Mono<Integer> scan() {
        return Mono.defer(() -> {
            boolean summarize = props.getSummary().isEnabled();
            boolean judge = props.getJudgement().isEnabled();
            if ((!summarize && !judge) || conflict.getAsBoolean()) {
                return Mono.just(0);
            }
            long idleMicros = Math.max(1, props.getSummary().effectiveIdleCutoff().toNanos() / 1_000);
            var cutoff = "TIMESTAMPADD(MICROSECOND, -%s, CURRENT_TIMESTAMP(6))";
            // Idle conversations with completed messages pending either record or judgement work.
            return db.sql("SELECT t.conversation_id AS c, t.tenant_id AS tenant_id, t.subject AS subject,"
                            + " MAX(m.sequence) AS last_seq, COUNT(DISTINCT t.id) AS turns,"
                            + " COALESCE(MAX(p.summarized_through_sequence), 0) AS summarized_through,"
                            + " COALESCE(MAX(p.judged_through_sequence), 0) AS judged_through"
                            + " FROM chat_turns t"
                            + " JOIN chat_messages m ON m.turn_id = t.id"
                            + " JOIN sessions se ON se.id = t.conversation_id AND se.tenant_id = t.tenant_id"
                            + " AND se.user_id = t.subject AND se.deleted_at IS NULL"
                            + " LEFT JOIN memory_index_progress p ON p.conversation_id = t.conversation_id"
                            + " AND p.tenant_id = t.tenant_id AND p.subject = t.subject"
                            + " WHERE t.status = 'COMPLETED'"
                            + " AND NOT EXISTS (SELECT 1 FROM chat_turns r WHERE r.conversation_id = t.conversation_id"
                            + " AND (r.status IN ('ACCEPTED', 'RUNNING') OR r.created_at > " + cutoff.formatted(":idleA")
                            + " OR r.finished_at > " + cutoff.formatted(":idleB") + "))"
                            + " GROUP BY t.conversation_id, t.tenant_id, t.subject"
                            + " HAVING MAX(m.sequence) > COALESCE(MAX(p.summarized_through_sequence), 0)"
                            + " OR MAX(m.sequence) > COALESCE(MAX(p.judged_through_sequence), 0)"
                            + " ORDER BY MAX(t.finished_at) LIMIT :limit")
                    .bind("idleA", idleMicros).bind("idleB", idleMicros)
                    .bind("limit", props.getSummary().getIdleScanBatch())
                    .map(row -> new Idle(new MemoryScope(row.get("tenant_id", String.class),
                            row.get("subject", String.class)), row.get("c", String.class),
                            SummaryRepository.number(row.get("last_seq")), SummaryRepository.number(row.get("turns")),
                            SummaryRepository.number(row.get("summarized_through")),
                            SummaryRepository.number(row.get("judged_through"))))
                    .all()
                    .collectList()
                    .flatMap(idle -> {
                        if (summarize) {
                            metrics.summaryBacklog(idle.stream()
                                    .filter(c -> c.lastSequence() > c.summarizedThrough())
                                    .mapToLong(Idle::turns).sum());
                        }
                        return Flux.fromIterable(idle)
                                .concatMap(c -> {
                                    Mono<Boolean> sum = summarize && c.lastSequence() > c.summarizedThrough()
                                            ? queue.enqueue(c.scope(), c.conversationId(), JobType.SUMMARIZE,
                                                    SummaryJobHandler.dedupeKey(c.conversationId(), c.lastSequence()),
                                                    SummaryJobHandler.payload(c.lastSequence()))
                                            : Mono.just(false);
                                    Mono<Boolean> jud = judge && c.lastSequence() > c.judgedThrough()
                                            ? queue.enqueue(c.scope(), c.conversationId(), JobType.JUDGE_MEMORY,
                                                    io.github.sukimiya.agileagent.memory.judgement.JudgementJobHandler
                                                            .dedupeKey(c.conversationId(), c.lastSequence()),
                                                    io.github.sukimiya.agileagent.memory.judgement.JudgementJobHandler
                                                            .payload(c.lastSequence()))
                                            : Mono.just(false);
                                    return sum.zipWith(jud, (a, b) -> (a ? 1 : 0) + (b ? 1 : 0));
                                })
                                .reduce(0, Integer::sum);
                    });
        });
    }

    @Override
    public void start() {
        var every = props.getSummary().getIdleScanInterval();
        schedule = Flux.interval(every, every)
                .onBackpressureDrop()
                .concatMap(tick -> scan().onErrorResume(e -> {
                    log.warn("memory.record idle scan failed: {}",
                            NestedExceptionUtils.getMostSpecificCause(e).toString());
                    return Mono.just(0);
                }))
                .subscribe(count -> {
                    if (count > 0) {
                        log.info("memory.record idle scan enqueued {} conversation(s)", count);
                    }
                });
    }

    @Override
    public void stop() {
        var current = schedule;
        if (current != null) {
            current.dispose();
        }
        schedule = null;
    }

    @Override
    public boolean isRunning() {
        var current = schedule;
        return current != null && !current.isDisposed();
    }

    /** Scheduled only when records are enabled and background work is on (as for recovery and cleanup). */
    @Override
    public boolean isAutoStartup() {
        return props.isRecoveryEnabled()
                && (props.getSummary().isEnabled() || props.getJudgement().isEnabled());
    }
}
