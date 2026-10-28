package io.github.sukimiya.agileagent.memory.judgement;

import java.util.ArrayList;
import java.util.List;
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

import io.github.sukimiya.agileagent.memory.MemoryMetrics;
import io.github.sukimiya.agileagent.memory.MemoryModel.TokenUsage;
import io.github.sukimiya.agileagent.memory.MemoryProperties;
import io.github.sukimiya.agileagent.memory.MemoryScope;
import io.github.sukimiya.agileagent.memory.context.TokenEstimator;
import io.github.sukimiya.agileagent.memory.items.DecayPolicy;
import io.github.sukimiya.agileagent.memory.items.ItemStatus;
import io.github.sukimiya.agileagent.memory.items.MemoryItem;
import io.github.sukimiya.agileagent.memory.items.MemoryItemRepository;
import io.github.sukimiya.agileagent.memory.items.MemoryItemSourceRepository;
import io.github.sukimiya.agileagent.memory.jobs.ClaimedJob;
import io.github.sukimiya.agileagent.memory.jobs.ConsolidationQuota;
import io.github.sukimiya.agileagent.memory.jobs.JobType;
import io.github.sukimiya.agileagent.memory.jobs.MemoryJobQueue;
import io.github.sukimiya.agileagent.memory.jobs.MemoryJobWorker;
import io.github.sukimiya.agileagent.memory.items.RebuildProfileJobHandler;
import io.github.sukimiya.agileagent.memory.judgement.JudgementValidator.RejectedException;
import io.github.sukimiya.agileagent.memory.recall.MemorySettingsService;
import io.github.sukimiya.agileagent.memory.summary.SummaryJobHandler;
import io.github.sukimiya.agileagent.service.LlmClient;
import io.github.sukimiya.agileagent.service.TenantLlmClientResolver;
import io.github.sukimiya.agileagent.service.TenantLlmConfigRequiredException;
import io.r2dbc.spi.ConnectionFactory;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Runs {@link JobType#JUDGE_MEMORY} jobs (M2b Task 3, D10): after idle, judges completed turns after the
 * judgement watermark with the tenant model + classpath Rules, validates each action, and writes items with
 * provenance. Cross-conversation off → session-scoped items only ({@code conversation_id} set).
 */
@Component
public class JudgementJobHandler {

    private static final Logger log = LoggerFactory.getLogger(JudgementJobHandler.class);
    private static final ObjectMapper JSON = new ObjectMapper();

    public record JudgeModel(LlmClient client, String model) {}

    private final DatabaseClient db;
    private final TransactionalOperator transactions;
    private final JudgementProgress progress;
    private final JudgementValidator validator;
    private final MemoryItemRepository items;
    private final MemoryItemSourceRepository sources;
    private final GlobalRules rules;
    private final MemorySettingsService settings;
    private final Function<String, Mono<JudgeModel>> models;
    private final MemoryProperties props;
    private final MemoryMetrics metrics;
    private final MemoryJobWorker worker;
    private final ConsolidationQuota quota;
    private final MemoryJobQueue jobs;

    @Autowired
    public JudgementJobHandler(DatabaseClient db, ConnectionFactory connectionFactory, JudgementProgress progress,
            JudgementValidator validator, MemoryItemRepository items, MemoryItemSourceRepository sources,
            GlobalRules rules, MemorySettingsService settings, TenantLlmClientResolver resolver,
            MemoryJobWorker worker, MemoryProperties props, MemoryMetrics metrics, ConsolidationQuota quota) {
        this(db, connectionFactory, progress, validator, items, sources, rules, settings,
                tenant -> resolver.resolveModel(tenant)
                        .zipWith(resolver.resolvePresetAndKey(tenant),
                                (model, preset) -> new JudgeModel(model.client(), preset.preset().model())),
                worker, props, metrics, quota);
    }

    public JudgementJobHandler(DatabaseClient db, ConnectionFactory connectionFactory, JudgementProgress progress,
            JudgementValidator validator, MemoryItemRepository items, MemoryItemSourceRepository sources,
            GlobalRules rules, MemorySettingsService settings, Function<String, Mono<JudgeModel>> models,
            MemoryJobWorker worker, MemoryProperties props, MemoryMetrics metrics, ConsolidationQuota quota) {
        this.db = db;
        this.transactions = TransactionalOperator.create(new R2dbcTransactionManager(connectionFactory));
        this.progress = progress;
        this.validator = validator;
        this.items = items;
        this.sources = sources;
        this.rules = rules;
        this.settings = settings;
        this.models = models;
        this.props = props;
        this.metrics = metrics;
        this.worker = worker;
        this.quota = quota;
        this.jobs = new MemoryJobQueue(db);
        if (props.getJudgement().isEnabled()) {
            worker.register(JobType.JUDGE_MEMORY, this::handle);
        }
    }

    public static String dedupeKey(String conversationId, long throughSequence) {
        return "judge:" + conversationId + ":" + throughSequence;
    }

    public static String payload(long throughSequence) {
        return "{\"throughSequence\":" + throughSequence + "}";
    }

    private Mono<Void> handle(ClaimedJob job) {
        var usageAcc = new long[3];
        return Mono.fromCallable(() -> JSON.readTree(job.payload()).path("throughSequence").asLong(-1))
                .flatMap(through -> through < 0
                        ? Mono.error(new IllegalArgumentException("JUDGE_MEMORY payload without throughSequence"))
                        : judge(job.scope(), job.conversationId(), through,
                                Mono.defer(() -> worker.renewLease(job)
                                        .flatMap(held -> held ? Mono.<Void>empty()
                                                : Mono.error(new IllegalStateException("JUDGE_MEMORY lease lost")))),
                                usageAcc))
                .then(Mono.defer(() -> settleUsage(job.scope().tenantId(), usageAcc)));
    }

    private Mono<Void> settleUsage(String tenantId, long[] usageAcc) {
        if (quota == null || usageAcc[2] <= 0) {
            return Mono.empty();
        }
        return quota.settle(tenantId, props.getConsolidation().getReserveTokens(),
                new TokenUsage(true, usageAcc[0], usageAcc[1], usageAcc[2]));
    }

    public Mono<Void> judge(MemoryScope scope, String conversationId, long throughSequence) {
        return judge(scope, conversationId, throughSequence, Mono.empty(), null);
    }

    private Mono<Void> judge(MemoryScope scope, String conversationId, long throughSequence, Mono<Void> beforeTurn,
            long[] usageAcc) {
        return progress.judgedThrough(scope, conversationId).flatMap(position -> position >= throughSequence
                ? Mono.empty()
                : isLive(scope, conversationId).flatMap(live -> !live ? Mono.empty()
                        : settings.crossConversationEnabled(scope).flatMap(cross -> model(scope)
                                .flatMap(model -> sourceTurns(scope, conversationId, position, throughSequence)
                                        .flatMap(turns -> judgeTurns(scope, conversationId, model, turns, 0,
                                                position, throughSequence, cross, beforeTurn, usageAcc))))));
    }

    private Mono<Boolean> isLive(MemoryScope scope, String conversationId) {
        return db.sql("SELECT COUNT(*) AS n FROM sessions WHERE id = :c AND tenant_id = :tenantId"
                        + " AND user_id = :subject AND is_valid = 1")
                .bind("c", conversationId).bind("tenantId", scope.tenantId()).bind("subject", scope.subject())
                .map((row, meta) -> ((Number) row.get("n")).longValue() == 1)
                .one()
                .defaultIfEmpty(false);
    }

    private Mono<JudgeModel> model(MemoryScope scope) {
        return Mono.defer(() -> models.apply(scope.tenantId()))
                .switchIfEmpty(Mono.error(new TenantLlmConfigRequiredException()))
                .doOnError(e -> metrics.judgementFailed(e instanceof TenantLlmConfigRequiredException
                        ? "MODEL_MISSING" : "MODEL_ERROR"));
    }

    private record TurnMsg(String id, long sequence, String turnId, String role, String content) {}

    private Mono<List<List<TurnMsg>>> sourceTurns(MemoryScope scope, String conversationId, long after, long through) {
        return db.sql("SELECT m.id, m.sequence, m.turn_id, m.role, m.payload FROM chat_messages m"
                        + " JOIN chat_turns t ON t.id = m.turn_id WHERE m.conversation_id = :c"
                        + " AND m.tenant_id = :tenantId AND m.subject = :subject AND t.status = 'COMPLETED' AND m.is_valid = 1"
                        + " AND t.memory_command IS NULL"
                        + " AND m.sequence > :after AND m.sequence <= :through ORDER BY m.sequence")
                .bind("c", conversationId).bind("tenantId", scope.tenantId()).bind("subject", scope.subject())
                .bind("after", after).bind("through", through)
                .map((row, meta) -> new TurnMsg(row.get("id", String.class),
                        ((Number) row.get("sequence")).longValue(), row.get("turn_id", String.class),
                        row.get("role", String.class), payloadContent(row.get("payload", String.class))))
                .all()
                .collectList()
                .map(JudgementJobHandler::groupByTurn);
    }

    private static List<List<TurnMsg>> groupByTurn(List<TurnMsg> rows) {
        var turns = new ArrayList<List<TurnMsg>>();
        String current = null;
        for (var row : rows) {
            if (!row.turnId().equals(current)) {
                current = row.turnId();
                turns.add(new ArrayList<>());
            }
            turns.getLast().add(row);
        }
        return turns;
    }

    private Mono<Void> judgeTurns(MemoryScope scope, String conversationId, JudgeModel model,
            List<List<TurnMsg>> turns, int index, long position, long through, boolean cross,
            Mono<Void> beforeTurn, long[] usageAcc) {
        if (index == turns.size()) {
            return position < through
                    ? progress.advance(scope, conversationId, position, through).then()
                    : Mono.empty();
        }
        var turn = turns.get(index);
        long end = turn.getLast().sequence();
        return beforeTurn.then(Mono.defer(() -> judgeTurn(scope, conversationId, model, turn, cross, usageAcc)))
                .then(Mono.defer(() -> progress.advance(scope, conversationId, position, end)))
                .flatMap(ok -> ok
                        ? judgeTurns(scope, conversationId, model, turns, index + 1, end, through, cross,
                                beforeTurn, usageAcc)
                        : Mono.empty());
    }

    private Mono<Void> judgeTurn(MemoryScope scope, String conversationId, JudgeModel model, List<TurnMsg> turn,
            boolean cross, long[] usageAcc) {
        var transcript = buildTranscript(turn);
        int cap = props.getJudgement().getMaxSourceTokens();
        if (TokenEstimator.estimate(transcript) > cap) {
            transcript = SummaryJobHandler.truncate(transcript, cap);
        }
        String existing = "[]";
        var rulesVersion = rules.current();
        String finalTranscript = transcript;
        return call(model, rulesVersion.text(), finalTranscript, existing, usageAcc)
                .flatMap(raw -> parseActions(raw)
                        .flatMapMany(Flux::fromIterable)
                        .concatMap(action -> validator.validate(scope, action, rulesVersion)
                                .flatMap(v -> apply(scope, conversationId, v, cross, model.model(),
                                        rulesVersion.version()))
                                .onErrorResume(RejectedException.class, e -> {
                                    metrics.judgementRejected(e.reason);
                                    return Mono.empty();
                                }))
                        .then())
                .onErrorResume(e -> {
                    if (e instanceof IllegalArgumentException) {
                        metrics.judgementFailed("INVALID_OUTPUT");
                        return Mono.error(e);
                    }
                    return Mono.error(e);
                });
    }

    private Mono<Void> apply(MemoryScope scope, String conversationId, ValidatedAction v, boolean cross,
            String judgeModel, int rulesVersion) {
        if (v.kind() == JudgementAction.Kind.HOLD || v.kind() == JudgementAction.Kind.IGNORE) {
            return Mono.empty();
        }
        String itemConversationId = cross ? null : conversationId;
        // Session-scoped when cross is off: always set conversation_id.
        if (!cross) {
            itemConversationId = conversationId;
        } else if (v.sources() != null && !v.sources().isEmpty()) {
            // User-level items: conversation_id null.
            itemConversationId = null;
        }
        if (v.kind() == JudgementAction.Kind.DEPRIORITIZE) {
            return items.casUpdate(scope, v.targetItemId(), v.expectedRevision(), ItemStatus.INACTIVE, null, null,
                    null, v.reason()).then();
        }
        if (v.kind() == JudgementAction.Kind.CONFLICT) {
            return items.casUpdate(scope, v.targetItemId(), v.expectedRevision(), ItemStatus.CONFLICT, null, null,
                    null, v.reason()).then();
        }
        if (v.kind() == JudgementAction.Kind.UPDATE || v.kind() == JudgementAction.Kind.MERGE) {
            return items.casUpdate(scope, v.targetItemId(), v.expectedRevision(), v.status(), v.content(),
                    v.assertion(), null, v.reason()).flatMap(ok -> {
                        if (!ok) {
                            metrics.judgementRejected("STALE_REVISION");
                            return Mono.empty();
                        }
                        return sources.saveAll(v.targetItemId(), v.sources())
                                .then(reconfirmed(scope, v))
                                .then(enqueueRebuild(scope, conversationId, cross));
                    });
        }
        if (v.kind() == JudgementAction.Kind.SUPERSEDE) {
            var neu = MemoryItem.newItem(scope, itemConversationId, v.itemType(), v.content(), v.status(),
                    v.assertion() == MemoryItem.Assertion.USER_STATED
                            ? MemoryItem.Assertion.USER_CONFIRMED
                            : v.assertion(),
                    v.importance(), v.confidence(), rulesVersion, judgeModel, v.reason());
            // Correction path (D16): USER_CONFIRMED new item when superseding with user assertion.
            if (v.assertion() == MemoryItem.Assertion.USER_CONFIRMED
                    || v.assertion() == MemoryItem.Assertion.USER_STATED) {
                neu = MemoryItem.newItem(scope, itemConversationId, v.itemType(), v.content(), ItemStatus.ACTIVE,
                        MemoryItem.Assertion.USER_CONFIRMED, v.importance(), v.confidence(), rulesVersion,
                        judgeModel, v.reason());
            }
            MemoryItem created = neu;
            return transactions.transactional(
                    items.casUpdate(scope, v.targetItemId(), v.expectedRevision(), ItemStatus.SUPERSEDED, null, null,
                            created.id(), v.reason())
                            .flatMap(ok -> {
                                if (!ok) {
                                    metrics.judgementRejected("STALE_REVISION");
                                    return Mono.<Void>empty();
                                }
                                return items.save(created).then(sources.saveAll(created.id(), v.sources()))
                                        .doOnSuccess(x -> metrics.judgementWritten())
                                        .then(enqueueRebuild(scope, conversationId, cross));
                            }));
        }
        // REMEMBER of an existing item (targetItemId, validated to exist and not be forgotten or deleted): no
        // duplicate, only a user re-confirmation refreshes its recall time (user decision 2026-10-28).
        if (v.targetItemId() != null && !v.targetItemId().isBlank()) {
            return reconfirmed(scope, v);
        }
        // REMEMBER
        var item = MemoryItem.newItem(scope, itemConversationId, v.itemType(), v.content(), v.status(),
                v.assertion(), v.importance(), v.confidence(), rulesVersion, judgeModel, v.reason());
        return transactions.transactional(items.save(item).then(sources.saveAll(item.id(), v.sources())))
                .doOnSuccess(x -> metrics.judgementWritten())
                .then(enqueueRebuild(scope, conversationId, cross));
    }


    /**
     * A user re-confirmation of an existing item (UPDATE / MERGE / REMEMBER naming {@code targetItemId}, final
     * assertion USER_STATED or USER_CONFIRMED) refreshes its recall time (M2b Task 6).
     */
    private Mono<Void> reconfirmed(MemoryScope scope, ValidatedAction v) {
        if (v.targetItemId() == null || v.targetItemId().isBlank() || !DecayPolicy.exempt(v.assertion())) {
            return Mono.empty();
        }
        return items.touchRecalled(scope, List.of(v.targetItemId())).then();
    }

    private Mono<Void> enqueueRebuild(MemoryScope scope, String conversationId, boolean cross) {
        if (!cross) {
            return Mono.empty();
        }
        return jobs.enqueue(scope, conversationId, JobType.REBUILD_PROFILE,
                RebuildProfileJobHandler.dedupeKey(scope), "{}").then();
    }

    private Mono<String> call(JudgeModel model, String rulesText, String transcript, String existing,
            long[] usageAcc) {
        var options = new LlmClient.CallOptions(props.getJudgement().getMaxOutputTokens(), true);
        return model.client().chatStream(MemoryJudgementPrompt.messages(rulesText, transcript, existing), List.of(),
                        options)
                .collectList()
                .map(events -> {
                    String text = "";
                    for (var event : events) {
                        if (event instanceof LlmClient.LlmEvent.Completed c) {
                            text = c.fullText() == null ? "" : c.fullText();
                        } else if (event instanceof LlmClient.LlmEvent.Usage u && usageAcc != null) {
                            usageAcc[0] += u.promptTokens();
                            usageAcc[1] += u.completionTokens();
                            usageAcc[2] += u.totalTokens();
                        }
                    }
                    return text;
                })
                .defaultIfEmpty("")
                .timeout(props.getJudgement().getJobTimeout())
                .doOnError(e -> metrics.judgementFailed(e instanceof TimeoutException ? "TIMEOUT" : "MODEL_ERROR"));
    }

    private Mono<List<JudgementAction>> parseActions(String raw) {
        return Mono.fromCallable(() -> {
            String text = raw == null ? "" : raw.trim();
            if (text.isEmpty()) {
                throw new IllegalArgumentException("empty judgement output");
            }
            JsonNode node = JSON.readTree(text);
            if (!node.isArray()) {
                throw new IllegalArgumentException("judgement output must be a JSON array");
            }
            List<JudgementAction> out = new ArrayList<>();
            for (JsonNode el : node) {
                out.add(JSON.treeToValue(el, JudgementAction.class));
            }
            return out;
        });
    }

    private static String buildTranscript(List<TurnMsg> turn) {
        var sb = new StringBuilder();
        for (var m : turn) {
            if (!sb.isEmpty()) {
                sb.append('\n');
            }
            sb.append(MemoryJudgementPrompt.line(m.id(), m.role(), m.content()));
        }
        return sb.toString();
    }

    private static String payloadContent(String payloadJson) {
        if (payloadJson == null) {
            return "";
        }
        try {
            var node = JSON.readTree(payloadJson);
            var c = node.path("content");
            return c.isMissingNode() || c.isNull() ? "" : c.asText("");
        } catch (Exception e) {
            return payloadJson;
        }
    }
}
