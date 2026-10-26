package io.github.sukimiya.agileagent.memory.summary;

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

import com.fasterxml.jackson.databind.ObjectMapper;

import io.github.sukimiya.agileagent.memory.ConversationRepository;
import io.github.sukimiya.agileagent.memory.MemoryMetrics;
import io.github.sukimiya.agileagent.memory.MemoryModel.TokenUsage;
import io.github.sukimiya.agileagent.memory.MemoryProperties;
import io.github.sukimiya.agileagent.memory.MemoryScope;
import io.github.sukimiya.agileagent.memory.context.TokenEstimator;
import io.github.sukimiya.agileagent.memory.jobs.ClaimedJob;
import io.github.sukimiya.agileagent.memory.jobs.ConsolidationQuota;
import io.github.sukimiya.agileagent.memory.jobs.JobType;
import io.github.sukimiya.agileagent.memory.jobs.MemoryJobWorker;
import io.github.sukimiya.agileagent.service.LlmClient;
import io.github.sukimiya.agileagent.service.TenantLlmClientResolver;
import io.github.sukimiya.agileagent.service.TenantLlmConfigRequiredException;
import io.github.sukimiya.agileagent.util.NonBlockingIds;
import io.r2dbc.spi.ConnectionFactory;
import reactor.core.publisher.Mono;

/**
 * Runs {@link JobType#SUMMARIZE} jobs (M2a Task 4, D18–D23): writes one {@code memory-record-v1} record for each
 * completed turn after the conversation's record position, up to the job's {@code throughSequence}. Jobs come
 * from {@link IdleConversationScanner} once a conversation has been idle (D19).
 * <ul>
 * <li>Registered on the job worker only with {@code app.memory.summary.enabled=true} (default off, D4); without a
 * handler SUMMARIZE jobs wait unclaimed. The worker runs jobs one after another on the single instance, so at
 * most one record call runs at a time (D4 global concurrency 1).</li>
 * <li>One model call per turn (D20), oldest first. Input is capped per turn ({@code max-source-tokens}, default
 * 12,000 estimated tokens, and never more than the tenant model's window leaves): a longer turn is cut to the
 * cap and still counts as recorded. Only COMPLETED turns are read.</li>
 * <li>The call uses the tenant's model in JSON mode with {@code max_tokens = max-output-tokens} (default 800) and
 * a timeout ({@code job-timeout}, default 120 s). {@link MemoryRecordValidator} checks the schema, drops entities
 * and key facts not found verbatim in the turn, and masks secrets.</li>
 * <li>Each record and the new position commit together as a compare-and-set on the position
 * ({@link SummaryRepository#save}); a repeated or raced job writes nothing. A missing tenant model, model error
 * or timeout leaves the position as it was and the worker retries with backoff.</li>
 * <li>Invalid output (D23) fails the attempt the same way and is counted per turn; the
 * {@code max-invalid-attempts}-th (default 3) consecutive invalid output for one turn skips it: no record, the
 * position moves past it ({@code memory.summary.skipped}), and the job continues with the next turn.</li>
 * <li>The job's lease is renewed before each turn, so a long backlog never outlives the 5 minute lease; a lost
 * lease stops the job.</li>
 * <li>A deleted conversation is skipped without a model call; its rows are removed by the cleanup job.</li>
 * <li>Records are a search index only and never enter prompt assembly.</li>
 * </ul>
 */
@Component
public class SummaryJobHandler {

    private static final Logger log = LoggerFactory.getLogger(SummaryJobHandler.class);
    private static final ObjectMapper JSON = new ObjectMapper();

    /** The model a tenant's summaries are written with; {@code contextWindowTokens} null means the default. */
    public record SummaryModel(LlmClient client, String model, Integer contextWindowTokens, String presetId) {}

    private final DatabaseClient db;
    private final TransactionalOperator transactions;
    private final SummaryRepository summaries;
    private final ConversationRepository conversations;
    private final Function<String, Mono<SummaryModel>> models;
    private final MemoryProperties props;
    private final MemoryMetrics metrics;
    private final MemoryJobWorker worker;
    private final ConsolidationQuota quota;

    @Autowired
    public SummaryJobHandler(DatabaseClient db, ConnectionFactory connectionFactory, SummaryRepository summaries,
            ConversationRepository conversations, TenantLlmClientResolver resolver, MemoryJobWorker worker,
            MemoryProperties props, MemoryMetrics metrics, ConsolidationQuota quota) {
        this(db, connectionFactory, summaries, conversations, tenant -> resolver.resolveModel(tenant)
                .zipWith(resolver.resolvePresetAndKey(tenant), (model, preset) -> new SummaryModel(model.client(),
                        preset.preset().model(), model.contextWindowTokens(), model.presetId())),
                worker, props, metrics, quota);
    }

    public SummaryJobHandler(DatabaseClient db, ConnectionFactory connectionFactory, SummaryRepository summaries,
            ConversationRepository conversations, Function<String, Mono<SummaryModel>> models,
            MemoryJobWorker worker, MemoryProperties props, MemoryMetrics metrics) {
        this(db, connectionFactory, summaries, conversations, models, worker, props, metrics, null);
    }

    public SummaryJobHandler(DatabaseClient db, ConnectionFactory connectionFactory, SummaryRepository summaries,
            ConversationRepository conversations, Function<String, Mono<SummaryModel>> models,
            MemoryJobWorker worker, MemoryProperties props, MemoryMetrics metrics, ConsolidationQuota quota) {
        this.db = db;
        this.transactions = TransactionalOperator.create(new R2dbcTransactionManager(connectionFactory));
        this.summaries = summaries;
        this.conversations = conversations;
        this.models = models;
        this.props = props;
        this.metrics = metrics;
        this.worker = worker;
        this.quota = quota;
        if (props.getSummary().isEnabled()) {
            worker.register(JobType.SUMMARIZE, this::handle);
        }
    }

    /** One job per conversation and end position. */
    public static String dedupeKey(String conversationId, long throughSequence) {
        return "summarize:" + conversationId + ":" + throughSequence;
    }

    public static String payload(long throughSequence) {
        return "{\"throughSequence\":" + throughSequence + "}";
    }

    private Mono<Void> handle(ClaimedJob job) {
        var usageAcc = new long[3]; // prompt, completion, total; total > 0 ⇒ observed
        return Mono.fromCallable(() -> JSON.readTree(job.payload()).path("throughSequence").asLong(-1))
                .flatMap(through -> through < 0
                        ? Mono.error(new IllegalArgumentException("SUMMARIZE payload without throughSequence"))
                        : summarize(job.scope(), job.conversationId(), through, Mono.defer(() -> worker.renewLease(job)
                                .flatMap(held -> held ? Mono.<Void>empty()
                                        : Mono.error(new IllegalStateException("SUMMARIZE job lease lost")))),
                                usageAcc))
                .then(Mono.defer(() -> settleUsage(job.scope().tenantId(), usageAcc)));
    }

    /** Replaces the worker's reservation with observed totals when the provider reported usage. */
    private Mono<Void> settleUsage(String tenantId, long[] usageAcc) {
        if (quota == null || usageAcc[2] <= 0) {
            return Mono.empty();
        }
        return quota.settle(tenantId, props.getConsolidation().getReserveTokens(),
                new TokenUsage(true, usageAcc[0], usageAcc[1], usageAcc[2]));
    }

    /** Records every completed turn after the current position, through at most {@code throughSequence}. */
    public Mono<Void> summarize(MemoryScope scope, String conversationId, long throughSequence) {
        return summarize(scope, conversationId, throughSequence, Mono.empty(), null);
    }

    /** As above; {@code beforeTurn} runs before each turn's model call (the job's lease renewal). */
    private Mono<Void> summarize(MemoryScope scope, String conversationId, long throughSequence, Mono<Void> beforeTurn,
            long[] usageAcc) {
        return summaries.summarizedThrough(scope, conversationId).flatMap(position -> position >= throughSequence
                ? Mono.<Void>empty()
                : summaries.isLive(scope, conversationId).flatMap(live -> !live ? Mono.<Void>empty()
                        : model(scope).flatMap(model -> sourceTurns(scope, conversationId, position, throughSequence)
                                .flatMap(turns -> recordTurns(scope, conversationId, model, turns, 0, position,
                                        throughSequence, beforeTurn, usageAcc)))));
    }

    private Mono<SummaryModel> model(MemoryScope scope) {
        return Mono.defer(() -> models.apply(scope.tenantId()))
                .switchIfEmpty(Mono.error(new TenantLlmConfigRequiredException()))
                .doOnError(e -> metrics.summaryFailed(e instanceof TenantLlmConfigRequiredException
                        ? "MODEL_MISSING" : "MODEL_ERROR"));
    }

    private record SourceMessage(String id, long sequence, String turnId, long ageMicros, LlmClient.Message message) {}

    private Mono<List<List<SourceMessage>>> sourceTurns(MemoryScope scope, String conversationId, long after,
            long through) {
        return db.sql("SELECT m.id, m.sequence, m.turn_id, m.payload,"
                        + " TIMESTAMPDIFF(MICROSECOND, t.finished_at, CURRENT_TIMESTAMP(6)) AS age_us FROM chat_messages m"
                        + " JOIN chat_turns t ON t.id = m.turn_id WHERE m.conversation_id = :c"
                        + " AND m.tenant_id = :tenantId AND m.subject = :subject AND t.status = 'COMPLETED'"
                        + " AND m.sequence > :after AND m.sequence <= :through ORDER BY m.sequence")
                .bind("c", conversationId).bind("tenantId", scope.tenantId()).bind("subject", scope.subject())
                .bind("after", after).bind("through", through)
                .map(row -> new SourceMessage(row.get("id", String.class), SummaryRepository.number(
                        row.get("sequence")), row.get("turn_id", String.class),
                        row.get("age_us") == null ? 0 : SummaryRepository.number(row.get("age_us")),
                        conversations.deserialize(row.get("payload", String.class))))
                .all()
                .collectList()
                .map(SummaryJobHandler::groupByTurn);
    }

    private static List<List<SourceMessage>> groupByTurn(List<SourceMessage> rows) {
        var turns = new ArrayList<List<SourceMessage>>();
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

    /** Records {@code turns[index..]} one after another; stops early when a write loses the position race. */
    private Mono<Void> recordTurns(MemoryScope scope, String conversationId, SummaryModel model,
            List<List<SourceMessage>> turns, int index, long position, long through, Mono<Void> beforeTurn,
            long[] usageAcc) {
        if (index == turns.size()) {
            // Trailing messages of turns that did not complete: nothing to record, move past them.
            return position < through
                    ? transactions.transactional(summaries.skip(scope, conversationId, position, through)).then()
                    : Mono.empty();
        }
        return beforeTurn.then(Mono.defer(() -> recordTurn(scope, conversationId, model, turns.get(index), position,
                        usageAcc)))
                .flatMap(next -> recordTurns(scope, conversationId, model, turns, index + 1, next, through,
                        beforeTurn, usageAcc));
    }

    /** Emits the new position after the turn, or completes empty when the position moved meanwhile. */
    private Mono<Long> recordTurn(MemoryScope scope, String conversationId, SummaryModel model,
            List<SourceMessage> turn, long position, long[] usageAcc) {
        var first = turn.getFirst();
        var last = turn.getLast();
        var text = String.join("\n", turn.stream().map(m -> SummaryPrompt.line(m.message())).toList());
        int cap = sourceCap(model);
        var transcript = TokenEstimator.estimate(text) > cap ? truncate(text, cap) : text;
        return call(model, transcript).flatMap(out -> {
            if (usageAcc != null && out.usage().observed()) {
                usageAcc[0] += out.usage().promptTokens();
                usageAcc[1] += out.usage().completionTokens();
                usageAcc[2] += out.usage().totalTokens();
            }
            MemoryRecordValidator.Content content;
            try {
                content = MemoryRecordValidator.validate(out.text(), transcript);
            } catch (MemoryRecordValidator.InvalidRecordException e) {
                return invalid(scope, conversationId, first.turnId(), position, last.sequence(), e);
            }
            var record = new MemoryRecord(NonBlockingIds.uuid(), first.turnId(), first.sequence(), last.sequence(),
                    first.id(), last.id(), content.summary(), content.entities(), content.keyFacts(),
                    content.decision(), content.keywords(), content.memoryType(), model.model(), SummaryPrompt.VERSION,
                    1);
            return transactions.transactional(summaries.save(scope, conversationId, record, position))
                    .flatMap(saved -> {
                        if (!saved) {
                            return Mono.<Long>empty();
                        }
                        metrics.summaryGenerated();
                        metrics.summaryLag(java.time.Duration.ofNanos(first.ageMicros() * 1_000));
                        log.info("memory.record written conversationId={} turnId={} from={} to={}", conversationId,
                                first.turnId(), first.sequence(), last.sequence());
                        return Mono.just(last.sequence());
                    });
        });
    }

    /** D23: count the invalid output; fail the attempt (backoff), or skip the turn at the limit. */
    private Mono<Long> invalid(MemoryScope scope, String conversationId, String turnId, long position, long end,
            MemoryRecordValidator.InvalidRecordException error) {
        metrics.summaryFailed("INVALID_OUTPUT");
        return transactions.transactional(summaries.recordInvalid(scope, conversationId, turnId))
                .flatMap(attempts -> attempts < props.getSummary().getMaxInvalidAttempts() ? Mono.<Long>error(error)
                        : transactions.transactional(summaries.skip(scope, conversationId, position, end))
                                .flatMap(moved -> {
                                    if (!moved) {
                                        return Mono.<Long>empty();
                                    }
                                    metrics.summarySkipped();
                                    log.warn("memory.record skipped after {} invalid outputs conversationId={}"
                                            + " turnId={} reason={}", attempts, conversationId, turnId,
                                            error.getMessage());
                                    return Mono.just(end);
                                }));
    }

    /**
     * The largest transcript (raw estimate) one call sends: {@code max-source-tokens}, and no more than the
     * tenant window leaves after the prompt and the output reserve (with the context safety factor).
     */
    private int sourceCap(SummaryModel model) {
        int cap = props.getSummary().getMaxSourceTokens();
        if (model.contextWindowTokens() != null) {
            double factor = props.getContext().getEstimateFactor();
            int byWindow = (int) Math.floor((model.contextWindowTokens() - props.getSummary().getMaxOutputTokens())
                    / factor) - (int) TokenEstimator.estimate(SummaryPrompt.SYSTEM) - 16;
            cap = Math.max(1, Math.min(cap, byWindow));
        }
        return cap;
    }

    /** The longest prefix of {@code text} whose estimate stays within {@code cap}. */
    static String truncate(String text, int cap) {
        int low = 0;
        int high = text.length();
        while (low < high) {
            int mid = (low + high + 1) >>> 1;
            if (TokenEstimator.estimate(text.substring(0, mid)) <= cap) {
                low = mid;
            } else {
                high = mid - 1;
            }
        }
        return text.substring(0, low);
    }

    private record ModelOutput(String text, TokenUsage usage) {}

    /** The model's raw output (possibly blank: the validator decides); errors are counted by reason. */
    private Mono<ModelOutput> call(SummaryModel model, String transcript) {
        var options = new LlmClient.CallOptions(props.getSummary().getMaxOutputTokens(), true);
        return model.client().chatStream(SummaryPrompt.messages(transcript), List.of(), options)
                .collectList()
                .map(events -> {
                    String text = "";
                    TokenUsage usage = TokenUsage.notObserved();
                    for (var event : events) {
                        if (event instanceof LlmClient.LlmEvent.Completed completed) {
                            text = completed.fullText() == null ? "" : completed.fullText();
                        } else if (event instanceof LlmClient.LlmEvent.Usage u) {
                            usage = new TokenUsage(true, u.promptTokens(), u.completionTokens(), u.totalTokens());
                        }
                    }
                    return new ModelOutput(text, usage);
                })
                .defaultIfEmpty(new ModelOutput("", TokenUsage.notObserved()))
                .timeout(props.getSummary().getJobTimeout())
                .doOnError(e -> metrics.summaryFailed(e instanceof TimeoutException ? "TIMEOUT" : "MODEL_ERROR"));
    }

}
