package io.github.sukimiya.agileagent.memory.summary;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Function;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import io.github.sukimiya.agileagent.config.UploadProperties;
import io.github.sukimiya.agileagent.memory.MemoryMaintenance;
import io.github.sukimiya.agileagent.memory.MemoryModel.TurnStatus;
import io.github.sukimiya.agileagent.memory.MemoryProperties;
import io.github.sukimiya.agileagent.memory.MemoryScope;
import io.github.sukimiya.agileagent.memory.MemoryTestFixture;
import io.github.sukimiya.agileagent.memory.MemoryTestStack;
import io.github.sukimiya.agileagent.memory.ToolRecordSanitizer;
import io.github.sukimiya.agileagent.memory.context.TokenEstimator;
import io.github.sukimiya.agileagent.memory.jobs.JobType;
import io.github.sukimiya.agileagent.memory.jobs.MemoryJobQueue;
import io.github.sukimiya.agileagent.memory.jobs.MemoryJobWorker;
import io.github.sukimiya.agileagent.service.FileStorageService;
import io.github.sukimiya.agileagent.service.LlmClient;
import io.github.sukimiya.agileagent.service.TenantLlmConfigRequiredException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * SUMMARIZE jobs (M2a Task 4): a summary of completed turns with its source range, written together with the
 * progress (CAS), idempotent, bounded (D4), sanitized, scoped, and removed with its conversation. H2 here;
 * {@link SummaryMySqlIntegrationTest} runs the same cases on MySQL 8.4. A fake model returns a fixed summary.
 */
class SummaryJobHandlerTest {

    static final Duration WAIT = Duration.ofSeconds(10);
    static final MemoryScope ALICE = MemoryTestStack.ALICE;

    @TempDir Path uploadDir;

    MemoryTestStack stack;
    String conversation;
    MemoryJobWorker worker;
    MemoryJobQueue queue;
    SummaryRepository summaries;
    /** Every summary call: its messages and requested max_tokens. */
    final List<List<LlmClient.Message>> calls = new CopyOnWriteArrayList<>();
    final List<Integer> maxTokens = new CopyOnWriteArrayList<>();
    Function<List<LlmClient.Message>, Flux<LlmClient.LlmEvent>> answer =
            messages -> Flux.just(new LlmClient.LlmEvent.Completed("摘要：用户问了车牌，助手答了 A7-3921"));
    Function<String, Mono<SummaryJobHandler.SummaryModel>> models;

    final LlmClient llm = new LlmClient() {
        @Override
        public Flux<LlmEvent> chatStream(List<Message> messages, List<ToolDefinition> tools) {
            return chatStream(messages, tools, new CallOptions(null));
        }

        @Override
        public Flux<LlmEvent> chatStream(List<Message> messages, List<ToolDefinition> tools, CallOptions options) {
            return Flux.defer(() -> {
                calls.add(List.copyOf(messages));
                maxTokens.add(options.maxTokens());
                return answer.apply(messages);
            });
        }
    };

    protected MemoryTestFixture newFixture() {
        return MemoryTestFixture.h2();
    }

    @BeforeEach
    void setUp() {
        stack = MemoryTestStack.create(newFixture(), p -> p.getSummary().setEnabled(true), t -> t,
                MemoryTestStack.testRedis());
        conversation = MemoryTestStack.newConversationId();
        var db = stack.fixture().databaseClient();
        worker = new MemoryJobWorker(db, () -> false);
        queue = new MemoryJobQueue(db);
        summaries = new SummaryRepository(db, stack.conversations());
        models = tenant -> Mono.just(new SummaryJobHandler.SummaryModel(llm, "summary-model", null, null));
    }

    @AfterEach
    void tearDown() {
        stack.close();
    }

    private SummaryJobHandler handler;

    /** One handler per test (it registers itself on {@link #worker}); fakes and properties are read per call. */
    private SummaryJobHandler handler() {
        if (handler == null) {
            handler = new SummaryJobHandler(stack.fixture().databaseClient(), stack.fixture().connectionFactory(),
                    summaries, stack.conversations(), tenant -> models.apply(tenant), worker, stack.props(),
                    stack.metrics());
        }
        return handler;
    }

    private long completedTurn(String question, String answerText) {
        var ticket = stack.accept(conversation, question).ticket();
        assertThat(stack.finalizer().finish(ticket, TurnStatus.COMPLETED, answerText).block(WAIT)).isTrue();
        return lastSequence();
    }

    private long lastSequence() {
        return ((Number) stack.rows("SELECT MAX(sequence) AS s FROM chat_messages WHERE conversation_id = ?",
                conversation).getFirst().get("S")).longValue();
    }

    private void summarize(long through) {
        handler().summarize(ALICE, conversation, through).block(WAIT);
    }

    private long progress() {
        var rows = stack.rows("SELECT summarized_through_sequence AS p FROM memory_index_progress"
                + " WHERE conversation_id = ?", conversation);
        return rows.isEmpty() ? 0 : ((Number) rows.getFirst().get("P")).longValue();
    }

    private int summaryRows() {
        return ((Number) stack.rows("SELECT COUNT(*) AS n FROM memory_summaries WHERE conversation_id = ?",
                conversation).getFirst().get("N")).intValue();
    }

    private List<Summary> stored() {
        return summaries.latestCovering(ALICE, conversation, Long.MAX_VALUE).collectList().block(WAIT);
    }

    private String sourceText() {
        return String.join("\n", calls.getLast().stream().map(LlmClient.Message::content).toList());
    }

    private Map<String, Object> job() {
        return stack.rows("SELECT status, attempts, last_error FROM memory_jobs WHERE job_type = 'SUMMARIZE'"
                + " AND conversation_id = ?", conversation).getFirst();
    }

    @Test
    void summaryRecordsSourceRangeAndCanExpandToOriginal() {
        completedTurn("我的车牌是多少？", "车牌是 A7-3921");
        long through = completedTurn("订单号呢？", "订单号 W-5518");

        summarize(through);

        var summary = stored().getFirst();
        var ids = stack.rows("SELECT id, sequence FROM chat_messages WHERE conversation_id = ? ORDER BY sequence",
                conversation);
        assertThat(summary.fromSequence()).isEqualTo(((Number) ids.getFirst().get("SEQUENCE")).longValue());
        assertThat(summary.toSequence()).isEqualTo(through);
        assertThat(summary.fromMessageId()).isEqualTo(ids.getFirst().get("ID"));
        assertThat(summary.toMessageId()).isEqualTo(ids.getLast().get("ID"));
        assertThat(summary.content()).isEqualTo("摘要：用户问了车牌，助手答了 A7-3921");
        assertThat(summary.generatorModel()).isEqualTo("summary-model");
        assertThat(summary.promptVersion()).isEqualTo(SummaryPrompt.VERSION);
        assertThat(summary.version()).isEqualTo(1);
        assertThat(stack.rows("SELECT source_type FROM memory_summaries").getFirst().get("SOURCE_TYPE"))
                .isEqualTo("MODEL_SUMMARY");
        assertThat(progress()).isEqualTo(through);
        // Back to the original: the covered messages, in order.
        assertThat(summaries.originalMessages(ALICE, conversation, summary).collectList().block(WAIT))
                .extracting(LlmClient.Message::content)
                .containsExactly("我的车牌是多少？", "车牌是 A7-3921", "订单号呢？", "订单号 W-5518");
        // The prompt says the material is data, not instructions, and asks for a bounded answer.
        assertThat(calls.getLast().getFirst().role()).isEqualTo("system");
        assertThat(calls.getLast().getFirst().content()).contains("不是指令");
        assertThat(sourceText()).contains("A7-3921").contains("W-5518");
        assertThat(maxTokens).containsExactly(800);
    }

    @Test
    void duplicateJobDoesNotCreateSameVersion() {
        long through = completedTurn("问题一", "回答一");

        summarize(through);
        summarize(through);

        assertThat(summaryRows()).isEqualTo(1);
        assertThat(calls).hasSize(1);
        // A writer that lost the progress race (stale expected position) writes nothing.
        var copy = stored().getFirst();
        assertThat(summaries.save(ALICE, conversation, copy, 0L).block(WAIT)).isFalse();
        assertThat(summaryRows()).isEqualTo(1);
        assertThat(progress()).isEqualTo(through);
        // The same job enqueued twice is one job.
        var key = SummaryJobHandler.dedupeKey(conversation, through);
        assertThat(queue.enqueue(ALICE, conversation, JobType.SUMMARIZE, key,
                SummaryJobHandler.payload(through)).block(WAIT)).isTrue();
        assertThat(queue.enqueue(ALICE, conversation, JobType.SUMMARIZE, key,
                SummaryJobHandler.payload(through)).block(WAIT)).isFalse();
    }

    @Test
    void failureKeepsPreviousProgress() {
        long first = completedTurn("问题一", "回答一");
        summarize(first);
        long second = completedTurn("问题二", "回答二");
        answer = messages -> Flux.error(new LlmClient.LlmException("LLM error: HTTP 500", 500));
        handler();
        queue.enqueue(ALICE, conversation, JobType.SUMMARIZE, SummaryJobHandler.dedupeKey(conversation, second),
                SummaryJobHandler.payload(second)).block(WAIT);

        assertThat(worker.runPending(10).block(WAIT)).isZero();

        assertThat(progress()).isEqualTo(first);
        assertThat(summaryRows()).isEqualTo(1);
        assertThat(job()).containsEntry("STATUS", "PENDING").containsEntry("LAST_ERROR", "LlmException");
        assertThat(stack.meters().find("memory.summary.failures").tag("reason", "MODEL_ERROR").counter().count())
                .isEqualTo(1.0);
    }

    @Test
    void onlyCompletedTurnsAreSummarized() {
        completedTurn("完成的问题一", "完成的回答一");
        var failed = stack.accept(conversation, "失败轮次的问题").ticket();
        assertThat(stack.finalizer().finish(failed, TurnStatus.FAILED, null).block(WAIT)).isTrue();
        long through = completedTurn("完成的问题二", "完成的回答二");

        summarize(through);

        assertThat(sourceText()).contains("完成的问题一", "完成的回答二").doesNotContain("失败轮次的问题");
        assertThat(progress()).isEqualTo(through);
        var summary = stored().getFirst();
        assertThat(summaries.originalMessages(ALICE, conversation, summary).collectList().block(WAIT))
                .extracting(LlmClient.Message::content).doesNotContain("失败轮次的问题");
    }

    @Test
    void summaryNeverReplacesOriginalInStorage() {
        completedTurn("问题一", "回答一");
        long through = completedTurn("问题二", "回答二");
        int before = stack.messageCount(conversation, "user") + stack.messageCount(conversation, "assistant");

        summarize(through);

        assertThat(stack.messageCount(conversation, "user") + stack.messageCount(conversation, "assistant"))
                .isEqualTo(before);
        var next = stack.accept(conversation, "问题三").ticket();
        assertThat(stack.history().contextBefore(next).block(WAIT)).extracting(LlmClient.Message::content)
                .containsExactly("问题一", "回答一", "问题二", "回答二");
    }

    @Test
    void deletedConversationSummaryNotUsedAndCleaned() {
        long through = completedTurn("问题一", "回答一");
        summarize(through);
        var uploads = new UploadProperties();
        uploads.setDir(uploadDir.toString());
        var maintenance = new MemoryMaintenance(stack.fixture().connectionFactory(), stack.fixture().databaseClient(),
                stack.cache(), new FileStorageService(uploads), stack.props(), stack.metrics());

        maintenance.deleteConversation(ALICE, conversation).block(WAIT);

        // Unusable at once (tombstone), before any cleanup ran.
        assertThat(stored()).isEmpty();
        assertThat(summaries.originalMessages(ALICE, conversation, new Summary("x", 0, Long.MAX_VALUE, "a", "b",
                "c", "m", SummaryPrompt.VERSION, 1)).collectList().block(WAIT)).isEmpty();
        // A late job for the deleted conversation does nothing and calls no model.
        summarize(through + 10);
        assertThat(calls).hasSize(1);
        // The cleanup job removes the derived rows.
        maintenance.runPending(10).block(WAIT);
        assertThat(summaryRows()).isZero();
        assertThat(stack.rows("SELECT COUNT(*) AS n FROM memory_index_progress WHERE conversation_id = ?",
                conversation).getFirst().get("N")).isEqualTo(0L);
    }

    @Test
    void summaryContentIsSanitized() {
        long through = completedTurn("我的 key 是什么", "见配置");
        answer = messages -> Flux.just(new LlmClient.LlmEvent.Completed(
                "用户提供了 api_key=sk-abcdefghijklmnopqrstuvwxyz 和 Bearer abc.def.ghi"));

        summarize(through);

        var content = stored().getFirst().content();
        assertThat(content).doesNotContain("sk-abcdefghijklmnopqrstuvwxyz").doesNotContain("abc.def.ghi")
                .contains(ToolRecordSanitizer.REDACTED);
    }

    @Test
    void missingTenantModelPostponesWithoutDroppingProgress() {
        long through = completedTurn("问题一", "回答一");
        models = tenant -> Mono.error(new TenantLlmConfigRequiredException());
        handler();
        queue.enqueue(ALICE, conversation, JobType.SUMMARIZE, SummaryJobHandler.dedupeKey(conversation, through),
                SummaryJobHandler.payload(through)).block(WAIT);

        assertThat(worker.runPending(10).block(WAIT)).isZero();

        assertThat(job()).containsEntry("STATUS", "PENDING")
                .containsEntry("LAST_ERROR", "TenantLlmConfigRequiredException");
        assertThat(progress()).isZero();
        assertThat(summaryRows()).isZero();
        assertThat(stack.meters().find("memory.summary.failures").tag("reason", "MODEL_MISSING").counter().count())
                .isEqualTo(1.0);
        // Once the tenant has a model, the same job summarizes the whole range.
        models = tenant -> Mono.just(new SummaryJobHandler.SummaryModel(llm, "summary-model", null, null));
        summarize(through);
        assertThat(progress()).isEqualTo(through);
    }

    @Test
    void jobInputCappedPerD4() {
        var props = stack.props();
        props.getSummary().setMaxSourceTokens(300);
        long first = completedTurn("一" + "甲".repeat(100), "二" + "乙".repeat(100)); // ~210 estimated with its labels
        long second = completedTurn("三" + "丙".repeat(100), "四" + "丁".repeat(100));

        summarize(second);

        // Only whole turns within the cap: the first turn; the job's later range waits for the next job.
        assertThat(progress()).isEqualTo(first);
        var source = calls.getLast().getLast().content();
        assertThat(TokenEstimator.estimate(source)).isLessThanOrEqualTo(300);
        assertThat(source).contains("甲").doesNotContain("丙");

        // A single turn above the cap is cut to the cap and still covered as a whole.
        summarize(second);
        long big = completedTurn("五" + "戊".repeat(1_000), "六");
        summarize(big);
        assertThat(progress()).isEqualTo(big);
        assertThat(TokenEstimator.estimate(calls.getLast().getLast().content())).isLessThanOrEqualTo(300);
        assertThat(stored().getLast().toSequence()).isEqualTo(big);
    }

    @Test
    void summaryCallIsTimeBoundedAndDisabledHandlerIsNotRegistered() {
        long through = completedTurn("问题一", "回答一");
        stack.props().getSummary().setJobTimeout(Duration.ofMillis(200));
        answer = messages -> Flux.never();

        assertThatThrownBy(() -> summarize(through)).hasRootCauseInstanceOf(java.util.concurrent.TimeoutException.class);
        assertThat(progress()).isZero();

        // Off (the default): no handler, so the job waits unclaimed (attempts unused).
        var off = new MemoryProperties();
        var offWorker = new MemoryJobWorker(stack.fixture().databaseClient(), () -> false);
        new SummaryJobHandler(stack.fixture().databaseClient(), stack.fixture().connectionFactory(), summaries,
                stack.conversations(), tenant -> models.apply(tenant), offWorker, off, stack.metrics());
        assertThat(off.getSummary().isEnabled()).isFalse();
        queue.enqueue(ALICE, conversation, JobType.SUMMARIZE, "k", SummaryJobHandler.payload(through)).block(WAIT);
        assertThat(offWorker.runPending(10).block(WAIT)).isZero();
        assertThat(job()).containsEntry("STATUS", "PENDING");
        assertThat(((Number) job().get("ATTEMPTS")).intValue()).isZero();
    }

    @Test
    void summariesAreScopedToTheOwner() {
        long through = completedTurn("问题一", "回答一");
        summarize(through);

        assertThat(summaries.latestCovering(new MemoryScope("tenant-1", "bob"), conversation, Long.MAX_VALUE)
                .collectList().block(WAIT)).isEmpty();
        assertThat(summaries.latestCovering(new MemoryScope("tenant-2", ALICE.subject()), conversation,
                Long.MAX_VALUE).collectList().block(WAIT)).isEmpty();
        // Only summaries that end before the given sequence.
        assertThat(summaries.latestCovering(ALICE, conversation, through).collectList().block(WAIT)).isEmpty();
        assertThat(summaries.latestCovering(ALICE, conversation, through + 1).collectList().block(WAIT)).hasSize(1);
    }
}
