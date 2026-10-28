package io.github.sukimiya.agileagent.memory.judgement;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeoutException;
import java.util.function.Function;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.r2dbc.connection.R2dbcTransactionManager;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.reactive.TransactionalOperator;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import io.github.sukimiya.agileagent.memory.MemoryMetrics;
import io.github.sukimiya.agileagent.memory.MemoryModel.TokenUsage;
import io.github.sukimiya.agileagent.memory.MemoryProperties;
import io.github.sukimiya.agileagent.memory.MemoryScope;
import io.github.sukimiya.agileagent.memory.context.TokenEstimator;
import io.github.sukimiya.agileagent.memory.items.ItemStatus;
import io.github.sukimiya.agileagent.memory.items.MemoryItem;
import io.github.sukimiya.agileagent.memory.items.MemoryItemRepository;
import io.github.sukimiya.agileagent.memory.items.MemoryItemSourceRepository;
import io.github.sukimiya.agileagent.memory.items.RebuildProfileJobHandler;
import io.github.sukimiya.agileagent.memory.jobs.ClaimedJob;
import io.github.sukimiya.agileagent.memory.jobs.ConsolidationQuota;
import io.github.sukimiya.agileagent.memory.jobs.JobType;
import io.github.sukimiya.agileagent.memory.jobs.MemoryJobQueue;
import io.github.sukimiya.agileagent.memory.jobs.MemoryJobWorker;
import io.github.sukimiya.agileagent.memory.judgement.JudgementJobHandler.JudgeModel;
import io.github.sukimiya.agileagent.memory.judgement.JudgementValidator.RejectedException;
import io.github.sukimiya.agileagent.memory.retention.RecallReferences;
import io.github.sukimiya.agileagent.memory.summary.SummaryJobHandler;
import io.github.sukimiya.agileagent.service.LlmClient;
import io.github.sukimiya.agileagent.service.TenantLlmClientResolver;
import io.github.sukimiya.agileagent.service.TenantLlmConfigRequiredException;
import io.github.sukimiya.agileagent.util.NonBlockingIds;
import io.r2dbc.spi.ConnectionFactory;
import reactor.core.publisher.Mono;

/**
 * Rules versioned replay (M2b Task 7, D13=A, user decisions 2026-10-28). Never automatic: a platform admin
 * requests a replay for one tenant ({@link #request}); a {@link JobType#REPLAY_RULES} job then re-judges, one
 * model call per item, every {@code INFERRED} item judged under an older Rules version (at least
 * {@code fromRulesVersion} when given) that is ACTIVE, CANDIDATE or CONFLICT, still valid, and that its owner never
 * touched ({@code memory_audit_log} has no row with the owner as actor for it).
 * <ul>
 * <li>The model gets the current Rules, the item's source turns and the item itself as the existing item. Only
 * the first action whose {@code targetItemId} is the item counts; any other output is ignored.</li>
 * <li>Keep or rewrite (REMEMBER/UPDATE/MERGE/SUPERSEDE, and CONFLICT which keeps the item content as a CONFLICT
 * item) that passes {@link JudgementValidator}: a new item under the current Rules version takes over the old
 * item's conversation, recall time and effective recall count ({@code inherited_recall_count}; recall records
 * are not copied); the old item becomes SUPERSEDED pointing to it.</li>
 * <li>No longer supported (IGNORE/HOLD/DEPRIORITIZE, no output for it, or rejected by validation): the old item
 * becomes INACTIVE (not deleted), with no new item.</li>
 * <li>No live source turn (its conversation was deleted), or the item changed meanwhile: left alone, SKIPPED.</li>
 * </ul>
 * Each handled item is recorded in {@code memory_replay_items} (old item, new item or none, conversation,
 * outcome) for audit, which also makes the job resumable: a retried or continued job only takes items not yet
 * recorded. Each item reserves the daily token quota before its call; over budget the replay is POSTPONED and a
 * continuation job is queued for the next day.
 */
@Component
public class RulesReplayHandler {

    private static final Logger log = LoggerFactory.getLogger(RulesReplayHandler.class);
    private static final ObjectMapper JSON = new ObjectMapper();

    /** Job subject of replay jobs: a replay spans every user of the tenant. */
    public static final String JOB_SUBJECT = "system:replay";

    public enum Status { PENDING, RUNNING, POSTPONED, COMPLETED }

    public record Replay(String id, String tenantId, Integer fromRulesVersion, int toRulesVersion, String status,
            String requestedBy, long superseded, long inactivated, long skipped, LocalDateTime createdAt,
            LocalDateTime updatedAt, LocalDateTime finishedAt) {

        public long processed() {
            return superseded + inactivated + skipped;
        }
    }

    private record Candidate(String id, MemoryScope scope, String conversationId, String itemType, String content,
            ItemStatus status, MemoryItem.Assertion assertion, long revision, LocalDateTime lastRecalledAt,
            long effectiveRecallCount, Double importance, Double confidence) {}

    private record TurnMsg(String id, String conversationId, String role, String content) {}

    private enum Step { NEXT, DONE, POSTPONED }

    private final DatabaseClient db;
    private final TransactionalOperator transactions;
    private final JudgementValidator validator;
    private final MemoryItemRepository items;
    private final GlobalRules rules;
    private final Function<String, Mono<JudgeModel>> models;
    private final MemoryJobWorker worker;
    private final MemoryProperties props;
    private final MemoryMetrics metrics;
    private final ConsolidationQuota quota;
    private final MemoryJobQueue jobs;
    private final MemoryItemSourceRepository sources;

    @Autowired
    public RulesReplayHandler(DatabaseClient db, ConnectionFactory connectionFactory, JudgementValidator validator,
            MemoryItemRepository items, GlobalRules rules, TenantLlmClientResolver resolver, MemoryJobWorker worker,
            MemoryProperties props, MemoryMetrics metrics, ConsolidationQuota quota) {
        this(db, connectionFactory, validator, items, rules,
                tenant -> resolver.resolveModel(tenant)
                        .zipWith(resolver.resolvePresetAndKey(tenant),
                                (model, preset) -> new JudgeModel(model.client(), preset.preset().model())),
                worker, props, metrics, quota);
    }

    public RulesReplayHandler(DatabaseClient db, ConnectionFactory connectionFactory, JudgementValidator validator,
            MemoryItemRepository items, GlobalRules rules, Function<String, Mono<JudgeModel>> models,
            MemoryJobWorker worker, MemoryProperties props, MemoryMetrics metrics, ConsolidationQuota quota) {
        this.db = db;
        this.transactions = TransactionalOperator.create(new R2dbcTransactionManager(connectionFactory));
        this.validator = validator;
        this.items = items;
        this.rules = rules;
        this.models = models;
        this.worker = worker;
        this.props = props;
        this.metrics = metrics;
        this.quota = quota;
        this.jobs = new MemoryJobQueue(db);
        this.sources = new MemoryItemSourceRepository(db);
        worker.register(JobType.REPLAY_RULES, this::handle);
    }

    static String payload(String replayId) {
        return "{\"replayId\":\"" + replayId + "\"}";
    }

    /** Records a PENDING replay of {@code tenantId} to the current Rules version and queues its job. */
    public Mono<Replay> request(String tenantId, Integer fromRulesVersion, String requestedBy) {
        String id = NonBlockingIds.uuid();
        int to = rules.current().version();
        return Mono.defer(() -> {
            var insert = db.sql("INSERT INTO memory_replays (id, tenant_id, from_rules_version, to_rules_version,"
                            + " status, requested_by, created_at, updated_at) VALUES (:id, :tenantId, :from, :to,"
                            + " 'PENDING', :by, CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6))")
                    .bind("id", id).bind("tenantId", tenantId).bind("to", to).bind("by", requestedBy);
            insert = fromRulesVersion == null ? insert.bindNull("from", Integer.class)
                    : insert.bind("from", fromRulesVersion);
            return insert.fetch().rowsUpdated()
                    .then(jobs.enqueue(new MemoryScope(tenantId, JOB_SUBJECT), null, JobType.REPLAY_RULES,
                            "replay:" + id, payload(id)))
                    .as(transactions::transactional);
        }).doOnSuccess(x -> log.info("memory.replay requested replayId={} tenantId={} from={} to={} by={}", id,
                tenantId, fromRulesVersion, to, requestedBy))
                .then(Mono.defer(() -> find(id)))
                .map(Optional::orElseThrow);
    }

    public Mono<Optional<Replay>> find(String replayId) {
        return db.sql("SELECT id, tenant_id, from_rules_version, to_rules_version, status, requested_by,"
                        + " superseded_count, inactivated_count, skipped_count, created_at, updated_at, finished_at"
                        + " FROM memory_replays WHERE id = :id")
                .bind("id", replayId)
                .map((row, meta) -> new Replay(row.get("id", String.class), row.get("tenant_id", String.class),
                        row.get("from_rules_version") == null ? null
                                : ((Number) row.get("from_rules_version")).intValue(),
                        ((Number) row.get("to_rules_version")).intValue(), row.get("status", String.class),
                        row.get("requested_by", String.class), ((Number) row.get("superseded_count")).longValue(),
                        ((Number) row.get("inactivated_count")).longValue(),
                        ((Number) row.get("skipped_count")).longValue(), row.get("created_at", LocalDateTime.class),
                        row.get("updated_at", LocalDateTime.class), row.get("finished_at", LocalDateTime.class)))
                .one()
                .map(Optional::of)
                .defaultIfEmpty(Optional.empty());
    }

    // --- job ---------------------------------------------------------------------------------------------------

    private Mono<Void> handle(ClaimedJob job) {
        return Mono.fromCallable(() -> JSON.readTree(job.payload()).path("replayId").asText(""))
                .flatMap(id -> id.isBlank()
                        ? Mono.error(new IllegalArgumentException("REPLAY_RULES payload without replayId"))
                        : find(id))
                .flatMap(found -> {
                    if (found.isEmpty() || Status.COMPLETED.name().equals(found.get().status())) {
                        return Mono.empty();
                    }
                    var replay = found.get();
                    var model = Mono.defer(() -> models.apply(replay.tenantId()))
                            .switchIfEmpty(Mono.error(new TenantLlmConfigRequiredException()))
                            .cache();
                    return setStatus(replay.id(), Status.RUNNING, false)
                            .then(loop(job, replay, model))
                            .flatMap(step -> step == Step.POSTPONED
                                    ? postpone(job, replay)
                                    : setStatus(replay.id(), Status.COMPLETED, true)
                                            .doOnSuccess(x -> log.info("memory.replay completed replayId={}",
                                                    replay.id())))
                            .then(refundJobReservation(job));
                });
    }

    private Mono<Step> loop(ClaimedJob job, Replay replay, Mono<JudgeModel> model) {
        return worker.renewLease(job)
                .flatMap(held -> held ? Mono.<Void>empty()
                        : Mono.error(new IllegalStateException("REPLAY_RULES lease lost")))
                .then(nextCandidate(replay))
                .flatMap(c -> replayItem(replay, c, model))
                .defaultIfEmpty(Step.DONE)
                .flatMap(step -> step == Step.NEXT ? loop(job, replay, model) : Mono.just(step));
    }

    private Mono<Void> postpone(ClaimedJob job, Replay replay) {
        if (quota != null) {
            quota.postponed();
        }
        log.info("memory.replay postponed by the daily token quota replayId={} tenantId={}", replay.id(),
                replay.tenantId());
        return setStatus(replay.id(), Status.POSTPONED, false)
                .then(jobs.enqueue(job.scope(), null, JobType.REPLAY_RULES, null, payload(replay.id()), true))
                .then();
    }

    /** The job's own claim-time reservation is replaced by the per-item reservations, so give it back. */
    private Mono<Void> refundJobReservation(ClaimedJob job) {
        if (quota == null) {
            return Mono.empty();
        }
        return quota.settle(job.scope().tenantId(), props.getConsolidation().getReserveTokens(),
                new TokenUsage(true, 0, 0, 0));
    }

    private Mono<Void> setStatus(String replayId, Status status, boolean finished) {
        return db.sql("UPDATE memory_replays SET status = :status, updated_at = CURRENT_TIMESTAMP(6)"
                        + (finished ? ", finished_at = CURRENT_TIMESTAMP(6)" : "") + " WHERE id = :id")
                .bind("status", status.name()).bind("id", replayId)
                .fetch().rowsUpdated().then();
    }

    private Mono<Candidate> nextCandidate(Replay replay) {
        var spec = db.sql("SELECT i.id, i.subject, i.conversation_id, i.item_type, i.content, i.status, i.assertion,"
                        + " i.revision, i.last_recalled_at, i.importance, i.confidence, i.recall_ref_offset AS o, "
                        + RecallReferences.itemReferencesSql("i") + " AS n FROM memory_items i"
                        + " WHERE i.tenant_id = :tenantId AND i.assertion = 'INFERRED'"
                        + " AND i.status IN ('ACTIVE', 'CANDIDATE', 'CONFLICT') AND i.is_valid = 1"
                        + " AND i.rules_version < :to"
                        + (replay.fromRulesVersion() == null ? "" : " AND i.rules_version >= :from")
                        + " AND NOT EXISTS (SELECT 1 FROM memory_audit_log a WHERE a.tenant_id = i.tenant_id"
                        + " AND a.subject = i.subject AND a.actor = i.subject AND a.target_id = i.id)"
                        + " AND NOT EXISTS (SELECT 1 FROM memory_replay_items r WHERE r.replay_id = :replayId"
                        + " AND r.item_id = i.id)"
                        + " ORDER BY i.id LIMIT 1")
                .bind("tenantId", replay.tenantId()).bind("to", replay.toRulesVersion())
                .bind("replayId", replay.id());
        if (replay.fromRulesVersion() != null) {
            spec = spec.bind("from", replay.fromRulesVersion());
        }
        return spec.map((row, meta) -> new Candidate(row.get("id", String.class),
                        new MemoryScope(replay.tenantId(), row.get("subject", String.class)),
                        row.get("conversation_id", String.class), row.get("item_type", String.class),
                        row.get("content", String.class), ItemStatus.valueOf(row.get("status", String.class)),
                        MemoryItem.Assertion.valueOf(row.get("assertion", String.class)),
                        ((Number) row.get("revision")).longValue(), row.get("last_recalled_at", LocalDateTime.class),
                        Math.max(0, ((Number) row.get("n")).longValue() - ((Number) row.get("o")).longValue()),
                        row.get("importance", Double.class), row.get("confidence", Double.class)))
                .one();
    }

    private Mono<Step> replayItem(Replay replay, Candidate c, Mono<JudgeModel> model) {
        return sourceTurns(c).flatMap(turns -> {
            if (turns.isEmpty()) {
                return record(replay, c, null, "SKIPPED", "NO_LIVE_SOURCE").thenReturn(Step.NEXT);
            }
            long reserve = quota == null ? 0 : props.getConsolidation().getReserveTokens();
            Mono<Boolean> reserved = quota == null ? Mono.just(true) : quota.reserve(c.scope().tenantId(), reserve);
            return reserved.flatMap(ok -> !ok ? Mono.just(Step.POSTPONED)
                    : model.flatMap(m -> judge(c, m, turns, reserve)
                            .flatMap(action -> action.isPresent() ? apply(replay, c, m, action.get())
                                    : noOutput(replay, c)))
                            .thenReturn(Step.NEXT));
        });
    }

    /** The model's action for this item, if any. Settles this item's reservation. */
    private Mono<Optional<JudgementAction>> judge(Candidate c, JudgeModel model, List<TurnMsg> turns, long reserved) {
        var transcript = new StringBuilder();
        for (var m : turns) {
            if (!transcript.isEmpty()) {
                transcript.append('\n');
            }
            transcript.append(MemoryJudgementPrompt.line(m.id(), m.role(), m.content()));
        }
        String text = transcript.toString();
        int cap = props.getJudgement().getMaxSourceTokens();
        if (TokenEstimator.estimate(text) > cap) {
            text = SummaryJobHandler.truncate(text, cap);
        }
        var usage = new long[3];
        var options = new LlmClient.CallOptions(props.getJudgement().getMaxOutputTokens(), true);
        var messages = MemoryJudgementPrompt.messages(rules.current().text(), text, existing(c));
        return model.client().chatStream(messages, List.of(), options)
                .collectList()
                .map(events -> {
                    String out = "";
                    for (var event : events) {
                        if (event instanceof LlmClient.LlmEvent.Completed done) {
                            out = done.fullText() == null ? "" : done.fullText();
                        } else if (event instanceof LlmClient.LlmEvent.Usage u) {
                            usage[0] += u.promptTokens();
                            usage[1] += u.completionTokens();
                            usage[2] += u.totalTokens();
                        }
                    }
                    return out;
                })
                .defaultIfEmpty("")
                .timeout(props.getJudgement().getJobTimeout())
                .doOnError(e -> metrics.judgementFailed(e instanceof TimeoutException ? "TIMEOUT" : "MODEL_ERROR"))
                .flatMap(raw -> settle(c, reserved, usage).thenReturn(raw))
                .onErrorResume(e -> settle(c, reserved, usage).then(Mono.error(e)))
                .map(raw -> targeting(raw, c.id()));
    }

    private Mono<Void> settle(Candidate c, long reserved, long[] usage) {
        if (quota == null) {
            return Mono.empty();
        }
        return quota.settle(c.scope().tenantId(), reserved, new TokenUsage(true, usage[0], usage[1], usage[2]));
    }

    /** First action naming the item; blank output or an empty array means no output for it. */
    static Optional<JudgementAction> targeting(String raw, String itemId) {
        String text = raw == null ? "" : raw.trim();
        if (text.isEmpty()) {
            return Optional.empty();
        }
        JsonNode node;
        try {
            node = JSON.readTree(text);
        } catch (Exception e) {
            throw new IllegalArgumentException("replay judgement output is not JSON", e);
        }
        if (!node.isArray()) {
            throw new IllegalArgumentException("replay judgement output must be a JSON array");
        }
        for (JsonNode el : node) {
            if (itemId.equals(el.path("targetItemId").asText(null))) {
                try {
                    return Optional.of(JSON.treeToValue(el, JudgementAction.class));
                } catch (Exception e) {
                    return Optional.empty();
                }
            }
        }
        return Optional.empty();
    }

    private static String existing(Candidate c) {
        ObjectNode o = JSON.createObjectNode();
        o.put("id", c.id());
        o.put("revision", c.revision());
        o.put("itemType", c.itemType());
        o.put("content", c.content());
        o.put("assertion", c.assertion().name());
        o.put("status", c.status().name());
        var arr = JSON.createArrayNode();
        arr.add(o);
        return arr.toString();
    }

    private Mono<Void> apply(Replay replay, Candidate c, JudgeModel model, JudgementAction action) {
        return validator.validate(c.scope(), action, rules.current())
                .flatMap(v -> switch (v.kind()) {
                    case IGNORE, HOLD, DEPRIORITIZE -> inactivate(replay, c, v.kind().name());
                    default -> supersede(replay, c, model, v);
                })
                .onErrorResume(RejectedException.class, e -> {
                    metrics.judgementRejected(e.reason);
                    return inactivate(replay, c, "REJECTED_" + e.reason);
                });
    }

    /** No output for the item. */
    private Mono<Void> noOutput(Replay replay, Candidate c) {
        return inactivate(replay, c, "NO_OUTPUT");
    }

    private Mono<Void> inactivate(Replay replay, Candidate c, String reason) {
        return transactions.transactional(items.casUpdate(c.scope(), c.id(), c.revision(), ItemStatus.INACTIVE, null,
                        null, null, null)
                .flatMap(ok -> ok
                        ? record(replay, c, null, "INACTIVATED", reason).then(rebuild(c))
                        : record(replay, c, null, "SKIPPED", "CHANGED")));
    }

    private Mono<Void> supersede(Replay replay, Candidate c, JudgeModel model, ValidatedAction v) {
        boolean conflict = v.kind() == JudgementAction.Kind.CONFLICT;
        var neu = MemoryItem.newItem(c.scope(), c.conversationId(),
                conflict || v.itemType() == null ? c.itemType() : v.itemType(),
                conflict || v.content() == null || v.content().isBlank() ? c.content() : v.content(),
                conflict ? ItemStatus.CONFLICT : v.status() == null ? c.status() : v.status(),
                v.assertion() == null ? c.assertion() : v.assertion(),
                v.importance() == null ? c.importance() : v.importance(),
                v.confidence() == null ? c.confidence() : v.confidence(),
                rules.current().version(), model.model(), v.reason());
        var newSources = v.sources() == null || v.sources().isEmpty()
                ? db.sql("INSERT INTO memory_item_sources (item_id, message_id, conversation_id, source_type,"
                                + " evidence_excerpt) SELECT :neu, message_id, conversation_id, source_type,"
                                + " evidence_excerpt FROM memory_item_sources WHERE item_id = :old")
                        .bind("neu", neu.id()).bind("old", c.id()).fetch().rowsUpdated().then()
                : sources.saveAll(neu.id(), v.sources());
        var inherit = db.sql("UPDATE memory_items SET last_recalled_at = :at, inherited_recall_count = :n"
                        + " WHERE id = :id")
                .bind("n", c.effectiveRecallCount()).bind("id", neu.id());
        inherit = c.lastRecalledAt() == null ? inherit.bindNull("at", LocalDateTime.class)
                : inherit.bind("at", c.lastRecalledAt());
        var inheritSpec = inherit;
        return transactions.transactional(items.casUpdate(c.scope(), c.id(), c.revision(), ItemStatus.SUPERSEDED,
                        null, null, neu.id(), null)
                .flatMap(ok -> !ok ? record(replay, c, null, "SKIPPED", "CHANGED")
                        : items.save(neu).then(newSources).then(inheritSpec.fetch().rowsUpdated())
                                .then(record(replay, c, neu.id(), "SUPERSEDED", v.kind().name()))
                                .then(rebuild(c))));
    }

    private Mono<Void> rebuild(Candidate c) {
        return c.conversationId() != null ? Mono.empty()
                : jobs.enqueue(c.scope(), null, JobType.REBUILD_PROFILE, RebuildProfileJobHandler.dedupeKey(c.scope()),
                        "{}").then();
    }

    private Mono<Void> record(Replay replay, Candidate c, String newItemId, String outcome, String reason) {
        String counter = switch (outcome) {
            case "SUPERSEDED" -> "superseded_count";
            case "INACTIVATED" -> "inactivated_count";
            default -> "skipped_count";
        };
        var insert = db.sql("INSERT INTO memory_replay_items (replay_id, item_id, new_item_id, tenant_id, subject,"
                        + " conversation_id, outcome, reason, created_at) VALUES (:replayId, :itemId, :newItemId,"
                        + " :tenantId, :subject, :c, :outcome, :reason, CURRENT_TIMESTAMP(6))")
                .bind("replayId", replay.id()).bind("itemId", c.id()).bind("tenantId", c.scope().tenantId())
                .bind("subject", c.scope().subject()).bind("outcome", outcome)
                .bind("reason", reason.length() > 64 ? reason.substring(0, 64) : reason);
        insert = newItemId == null ? insert.bindNull("newItemId", String.class) : insert.bind("newItemId", newItemId);
        insert = c.conversationId() == null ? insert.bindNull("c", String.class) : insert.bind("c", c.conversationId());
        return insert.fetch().rowsUpdated()
                .then(db.sql("UPDATE memory_replays SET " + counter + " = " + counter + " + 1,"
                                + " updated_at = CURRENT_TIMESTAMP(6) WHERE id = :id")
                        .bind("id", replay.id()).fetch().rowsUpdated())
                .doOnSuccess(x -> metrics.replayItem(outcome))
                .then();
    }

    /** The turns (live conversation, completed, not a memory command) holding the item's source messages. */
    private Mono<List<TurnMsg>> sourceTurns(Candidate c) {
        return db.sql("SELECT m.id, m.conversation_id, m.role, m.payload FROM chat_messages m"
                        + " JOIN chat_turns t ON t.id = m.turn_id"
                        + " JOIN sessions s ON s.id = m.conversation_id AND s.tenant_id = m.tenant_id"
                        + " AND s.user_id = m.subject"
                        + " WHERE m.tenant_id = :tenantId AND m.subject = :subject AND m.is_valid = 1"
                        + " AND t.status = 'COMPLETED' AND t.memory_command IS NULL AND s.is_valid = 1"
                        + " AND m.turn_id IN (SELECT src.turn_id FROM chat_messages src"
                        + " JOIN memory_item_sources ms ON ms.message_id = src.id WHERE ms.item_id = :id)"
                        + " ORDER BY m.conversation_id, m.sequence")
                .bind("tenantId", c.scope().tenantId()).bind("subject", c.scope().subject()).bind("id", c.id())
                .map((row, meta) -> new TurnMsg(row.get("id", String.class), row.get("conversation_id", String.class),
                        row.get("role", String.class),
                        JudgementJobHandler.payloadContent(row.get("payload", String.class))))
                .all()
                .collectList()
                .map(ArrayList::new);
    }
}
