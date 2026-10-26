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
 * The idle trigger of turn memory records (M2a Task 4, D19). Every {@code app.memory.summary.idle-scan-interval}
 * (default 5 min) it looks for conversations that are done: live, no active turn, no turn created or finished
 * within {@code idle-timeout} (default 30 min, database time; a recent failed or cancelled turn also counts as a
 * new message), and with completed messages after the record position. Each one gets a SUMMARIZE job with dedupe
 * key {@code summarize:<conversationId>:<last completed sequence>}: one job per idle period, since a key is never
 * reused (D19); new messages give the next period a new key. At most {@code idle-scan-batch} conversations per
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

    private record Idle(MemoryScope scope, String conversationId, long lastSequence, long turns) {}

    /** One scan; emits how many jobs were newly enqueued. */
    public Mono<Integer> scan() {
        return Mono.defer(() -> {
            if (!props.getSummary().isEnabled() || conflict.getAsBoolean()) {
                return Mono.just(0);
            }
            long idleMicros = Math.max(1, props.getSummary().getIdleTimeout().toNanos() / 1_000);
            var cutoff = "TIMESTAMPADD(MICROSECOND, -%s, CURRENT_TIMESTAMP(6))";
            return db.sql("SELECT t.conversation_id AS c, t.tenant_id AS tenant_id, t.subject AS subject,"
                            + " MAX(m.sequence) AS last_seq, COUNT(DISTINCT t.id) AS turns FROM chat_turns t"
                            + " JOIN chat_messages m ON m.turn_id = t.id"
                            + " JOIN sessions se ON se.id = t.conversation_id AND se.tenant_id = t.tenant_id"
                            + " AND se.user_id = t.subject AND se.deleted_at IS NULL"
                            + " LEFT JOIN memory_index_progress p ON p.conversation_id = t.conversation_id"
                            + " AND p.tenant_id = t.tenant_id AND p.subject = t.subject"
                            + " WHERE t.status = 'COMPLETED'"
                            + " AND m.sequence > COALESCE(p.summarized_through_sequence, 0)"
                            + " AND NOT EXISTS (SELECT 1 FROM chat_turns r WHERE r.conversation_id = t.conversation_id"
                            + " AND (r.status IN ('ACCEPTED', 'RUNNING') OR r.created_at > " + cutoff.formatted(":idleA")
                            + " OR r.finished_at > " + cutoff.formatted(":idleB") + "))"
                            + " GROUP BY t.conversation_id, t.tenant_id, t.subject"
                            + " ORDER BY MAX(t.finished_at) LIMIT :limit")
                    .bind("idleA", idleMicros).bind("idleB", idleMicros)
                    .bind("limit", props.getSummary().getIdleScanBatch())
                    .map(row -> new Idle(new MemoryScope(row.get("tenant_id", String.class),
                            row.get("subject", String.class)), row.get("c", String.class),
                            SummaryRepository.number(row.get("last_seq")), SummaryRepository.number(row.get("turns"))))
                    .all()
                    .collectList()
                    .flatMap(idle -> {
                        metrics.summaryBacklog(idle.stream().mapToLong(Idle::turns).sum());
                        return Flux.fromIterable(idle)
                                .concatMap(c -> queue.enqueue(c.scope(), c.conversationId(), JobType.SUMMARIZE,
                                        SummaryJobHandler.dedupeKey(c.conversationId(), c.lastSequence()),
                                        SummaryJobHandler.payload(c.lastSequence())))
                                .filter(Boolean::booleanValue)
                                .count()
                                .map(Long::intValue);
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
        return props.isRecoveryEnabled() && props.getSummary().isEnabled();
    }
}
