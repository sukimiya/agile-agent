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
import io.github.sukimiya.agileagent.memory.MemoryProperties;
import io.github.sukimiya.agileagent.memory.MemoryScope;
import io.github.sukimiya.agileagent.memory.ToolRecordSanitizer;
import io.github.sukimiya.agileagent.memory.context.TokenEstimator;
import io.github.sukimiya.agileagent.memory.jobs.ClaimedJob;
import io.github.sukimiya.agileagent.memory.jobs.JobType;
import io.github.sukimiya.agileagent.memory.jobs.MemoryJobWorker;
import io.github.sukimiya.agileagent.service.LlmClient;
import io.github.sukimiya.agileagent.service.TenantLlmClientResolver;
import io.github.sukimiya.agileagent.service.TenantLlmConfigRequiredException;
import io.github.sukimiya.agileagent.util.NonBlockingIds;
import io.r2dbc.spi.ConnectionFactory;
import reactor.core.publisher.Mono;

/**
 * Runs {@link JobType#SUMMARIZE} jobs (M2a Task 4): summarizes the completed messages after the conversation's
 * summary position, up to the job's {@code throughSequence}.
 * <ul>
 * <li>Registered on the job worker only with {@code app.memory.summary.enabled=true} (default off, D4); without a
 * handler SUMMARIZE jobs wait unclaimed. The worker runs jobs one after another on the single instance, so at
 * most one summary call runs at a time (D4 global concurrency 1).</li>
 * <li>Input is capped per job (D4/D5, {@code max-source-tokens}, default 12,000 estimated tokens, and never more
 * than the tenant model's window leaves): whole turns oldest first; a single turn above the cap is cut to it and
 * still counts as covered. The rest of the range is left for the next job. Only COMPLETED turns are read.</li>
 * <li>The call uses the tenant's model with {@code max_tokens = max-output-tokens} (default 800) and a timeout
 * ({@code job-timeout}, default 120 s). The text is sanitized before it is stored.</li>
 * <li>The summary and the new position commit together as a compare-and-set on the position
 * ({@link SummaryRepository#save}); a repeated or raced job writes nothing. Any failure (missing tenant model,
 * model error, timeout) leaves the position as it was and the worker retries with backoff.</li>
 * <li>A deleted conversation is skipped without a model call; its rows are removed by the cleanup job.</li>
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

    @Autowired
    public SummaryJobHandler(DatabaseClient db, ConnectionFactory connectionFactory, SummaryRepository summaries,
            ConversationRepository conversations, TenantLlmClientResolver resolver, MemoryJobWorker worker,
            MemoryProperties props, MemoryMetrics metrics) {
        this(db, connectionFactory, summaries, conversations, tenant -> resolver.resolveModel(tenant)
                .zipWith(resolver.resolvePresetAndKey(tenant), (model, preset) -> new SummaryModel(model.client(),
                        preset.preset().model(), model.contextWindowTokens(), model.presetId())),
                worker, props, metrics);
    }

    public SummaryJobHandler(DatabaseClient db, ConnectionFactory connectionFactory, SummaryRepository summaries,
            ConversationRepository conversations, Function<String, Mono<SummaryModel>> models,
            MemoryJobWorker worker, MemoryProperties props, MemoryMetrics metrics) {
        this.db = db;
        this.transactions = TransactionalOperator.create(new R2dbcTransactionManager(connectionFactory));
        this.summaries = summaries;
        this.conversations = conversations;
        this.models = models;
        this.props = props;
        this.metrics = metrics;
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
        return Mono.fromCallable(() -> JSON.readTree(job.payload()).path("throughSequence").asLong(-1))
                .flatMap(through -> through < 0
                        ? Mono.error(new IllegalArgumentException("SUMMARIZE payload without throughSequence"))
                        : summarize(job.scope(), job.conversationId(), through));
    }

    /** Summarizes the next capped range after the current position, through at most {@code throughSequence}. */
    public Mono<Void> summarize(MemoryScope scope, String conversationId, long throughSequence) {
        return summaries.summarizedThrough(scope, conversationId).flatMap(position -> position >= throughSequence
                ? Mono.<Void>empty()
                : summaries.isLive(scope, conversationId).flatMap(live -> !live ? Mono.<Void>empty()
                        : model(scope).flatMap(model -> sourceTurns(scope, conversationId, position, throughSequence)
                                .flatMap(turns -> turns.isEmpty()
                                        ? transactions.transactional(summaries.skip(scope, conversationId, position,
                                                throughSequence)).then()
                                        : summarizeRange(scope, conversationId, position, model, turns)))));
    }

    private Mono<SummaryModel> model(MemoryScope scope) {
        return Mono.defer(() -> models.apply(scope.tenantId()))
                .switchIfEmpty(Mono.error(new TenantLlmConfigRequiredException()))
                .doOnError(e -> metrics.summaryFailed(e instanceof TenantLlmConfigRequiredException
                        ? "MODEL_MISSING" : "MODEL_ERROR"));
    }

    private record SourceMessage(String id, long sequence, String turnId, LlmClient.Message message) {}

    private Mono<List<List<SourceMessage>>> sourceTurns(MemoryScope scope, String conversationId, long after,
            long through) {
        return db.sql("SELECT m.id, m.sequence, m.turn_id, m.payload FROM chat_messages m"
                        + " JOIN chat_turns t ON t.id = m.turn_id WHERE m.conversation_id = :c"
                        + " AND m.tenant_id = :tenantId AND m.subject = :subject AND t.status = 'COMPLETED'"
                        + " AND m.sequence > :after AND m.sequence <= :through ORDER BY m.sequence")
                .bind("c", conversationId).bind("tenantId", scope.tenantId()).bind("subject", scope.subject())
                .bind("after", after).bind("through", through)
                .map(row -> new SourceMessage(row.get("id", String.class), SummaryRepository.number(
                        row.get("sequence")), row.get("turn_id", String.class),
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

    private Mono<Void> summarizeRange(MemoryScope scope, String conversationId, long position, SummaryModel model,
            List<List<SourceMessage>> turns) {
        int cap = sourceCap(model);
        var included = new ArrayList<SourceMessage>();
        var transcript = new StringBuilder();
        for (var turn : turns) {
            var text = String.join("\n", turn.stream().map(m -> SummaryPrompt.line(m.message())).toList());
            var candidate = transcript.isEmpty() ? text : transcript + "\n" + text;
            if (TokenEstimator.estimate(candidate) > cap) {
                if (included.isEmpty()) {
                    // One turn alone exceeds the cap: summarize its beginning, the turn still counts as covered.
                    transcript.append(truncate(text, cap));
                    included.addAll(turn);
                }
                break;
            }
            transcript.setLength(0);
            transcript.append(candidate);
            included.addAll(turn);
        }
        var first = included.getFirst();
        var last = included.getLast();
        return call(model, transcript.toString())
                .flatMap(content -> transactions.transactional(summaries.save(scope, conversationId,
                        new Summary(NonBlockingIds.uuid(), first.sequence(), last.sequence(), first.id(), last.id(),
                                content, model.model(), SummaryPrompt.VERSION, 1), position)))
                .doOnNext(saved -> {
                    if (saved) {
                        metrics.summaryGenerated();
                        log.info("memory.summary written conversationId={} from={} to={} messages={}",
                                conversationId, first.sequence(), last.sequence(), included.size());
                    }
                })
                .then();
    }

    /**
     * The largest transcript (raw estimate) one job sends: {@code max-source-tokens}, and no more than the
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

    private Mono<String> call(SummaryModel model, String transcript) {
        var options = new LlmClient.CallOptions(props.getSummary().getMaxOutputTokens());
        return model.client().chatStream(SummaryPrompt.messages(transcript), List.of(), options)
                .ofType(LlmClient.LlmEvent.Completed.class)
                .next()
                .map(LlmClient.LlmEvent.Completed::fullText)
                .filter(text -> text != null && !text.isBlank())
                .switchIfEmpty(Mono.error(new IllegalStateException("empty summary")))
                .timeout(props.getSummary().getJobTimeout())
                .map(ToolRecordSanitizer::sanitize)
                .doOnError(e -> metrics.summaryFailed(e instanceof TimeoutException ? "TIMEOUT" : "MODEL_ERROR"));
    }
}
