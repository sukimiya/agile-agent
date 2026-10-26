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

import com.fasterxml.jackson.databind.ObjectMapper;

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
import io.github.sukimiya.agileagent.memory.summary.MemoryRecord.MemoryType;
import io.github.sukimiya.agileagent.service.FileStorageService;
import io.github.sukimiya.agileagent.service.LlmClient;
import io.github.sukimiya.agileagent.service.TenantLlmConfigRequiredException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * SUMMARIZE jobs (M2a Task 4, D18–D23): one {@code memory-record-v1} record per completed turn, written with the
 * progress (CAS), idempotent, bounded (D4), validated verbatim, sanitized, scoped, searchable, never injected into
 * the prompt, and removed with its conversation. H2 here; {@link SummaryMySqlIntegrationTest} runs the same cases
 * on MySQL 8.4 (ngram FULLTEXT). A fake model answers with a record built from the transcript it receives.
 */
class SummaryJobHandlerTest {

    static final Duration WAIT = Duration.ofSeconds(10);
    static final MemoryScope ALICE = MemoryTestStack.ALICE;
    static final ObjectMapper JSON = new ObjectMapper();

    @TempDir Path uploadDir;

    MemoryTestStack stack;
    String conversation;
    MemoryJobWorker worker;
    MemoryJobQueue queue;
    SummaryRepository summaries;
    /** Every record call: its messages and request options. */
    final List<List<LlmClient.Message>> calls = new CopyOnWriteArrayList<>();
    final List<LlmClient.CallOptions> options = new CopyOnWriteArrayList<>();
    /** Default answer: a valid record naming the plate code when the turn mentions it. */
    Function<List<LlmClient.Message>, Flux<LlmClient.LlmEvent>> answer = messages -> completed(
            record("本轮对话：" + messages.getLast().content().lines().findFirst().orElse(""),
                    messages.getLast().content().contains("A7-3921") ? "A7-3921" : null));
    Function<String, Mono<SummaryJobHandler.SummaryModel>> models;

    final LlmClient llm = new LlmClient() {
        @Override
        public Flux<LlmEvent> chatStream(List<Message> messages, List<ToolDefinition> tools) {
            return chatStream(messages, tools, new CallOptions(null));
        }

        @Override
        public Flux<LlmEvent> chatStream(List<Message> messages, List<ToolDefinition> tools, CallOptions opts) {
            return Flux.defer(() -> {
                calls.add(List.copyOf(messages));
                options.add(opts);
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
        models = tenant -> Mono.just(new SummaryJobHandler.SummaryModel(llm, "record-model", null, null));
    }

    @AfterEach
    void tearDown() {
        stack.close();
    }

    static Flux<LlmClient.LlmEvent> completed(String text) {
        return Flux.just(new LlmClient.LlmEvent.Completed(text));
    }

    /** A valid record; {@code fact} (when not null) becomes a CODE key fact. */
    static String record(String summary, String fact) {
        var facts = fact == null ? "[]" : "[{\"text\":\"" + fact + "\",\"kind\":\"CODE\"}]";
        return "{\"schema\":\"memory-record-v1\",\"summary\":\"" + summary.replace("\"", "") + "\",\"entities\":[],"
                + "\"key_facts\":" + facts + ",\"decision\":false,\"keywords\":[\"测试\"],\"memory_type\":\"EVENT\"}";
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

    private String lastTurnId() {
        return (String) stack.rows("SELECT turn_id FROM chat_messages WHERE conversation_id = ? ORDER BY sequence DESC",
                conversation).getFirst().get("TURN_ID");
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

    private int recordRows() {
        return ((Number) stack.rows("SELECT COUNT(*) AS n FROM memory_summaries WHERE conversation_id = ?",
                conversation).getFirst().get("N")).intValue();
    }

    private List<MemoryRecord> stored() {
        return summaries.recordsOf(ALICE, conversation).collectList().block(WAIT);
    }

    private String sourceText() {
        return calls.getLast().getLast().content();
    }

    private Map<String, Object> job() {
        return stack.rows("SELECT status, attempts, last_error FROM memory_jobs WHERE job_type = 'SUMMARIZE'"
                + " AND conversation_id = ?", conversation).getFirst();
    }

    private void enqueue(long through) {
        queue.enqueue(ALICE, conversation, JobType.SUMMARIZE, SummaryJobHandler.dedupeKey(conversation, through),
                SummaryJobHandler.payload(through)).block(WAIT);
    }

    /** Makes the (backed-off) job due again and runs the worker once. */
    private int runDue() {
        stack.sql("UPDATE memory_jobs SET available_at = TIMESTAMPADD(SECOND, -1, CURRENT_TIMESTAMP(6))"
                + " WHERE status = 'PENDING'");
        return worker.runPending(10).block(WAIT);
    }

    private double counter(String name, String reason) {
        var search = stack.meters().find(name);
        if (reason != null) {
            search = search.tag("reason", reason);
        }
        var c = search.counter();
        return c == null ? 0 : c.count();
    }

    @Test
    void recordIsStructuredJsonWithSourceTurn() throws Exception {
        completedTurn("我的车牌是多少？", "车牌是 A7-3921");
        String firstTurn = lastTurnId();
        long through = completedTurn("订单号呢？", "订单号 W-5518");

        summarize(through);

        // One model call and one record per completed turn, in order.
        assertThat(calls).hasSize(2);
        var records = stored();
        assertThat(records).hasSize(2);
        var first = records.getFirst();
        var ids = stack.rows("SELECT id, sequence FROM chat_messages WHERE conversation_id = ? ORDER BY sequence",
                conversation);
        assertThat(first.turnId()).isEqualTo(firstTurn);
        assertThat(first.fromSequence()).isEqualTo(((Number) ids.get(0).get("SEQUENCE")).longValue());
        assertThat(first.toSequence()).isEqualTo(((Number) ids.get(1).get("SEQUENCE")).longValue());
        assertThat(first.fromMessageId()).isEqualTo(ids.get(0).get("ID"));
        assertThat(first.toMessageId()).isEqualTo(ids.get(1).get("ID"));
        assertThat(first.keyFacts()).extracting(MemoryRecord.KeyFact::text).containsExactly("A7-3921");
        assertThat(first.memoryType()).isEqualTo(MemoryType.EVENT);
        assertThat(first.generatorModel()).isEqualTo("record-model");
        assertThat(first.schemaVersion()).isEqualTo(SummaryPrompt.VERSION).isEqualTo("memory-record-v1");
        assertThat(first.version()).isEqualTo(1);
        assertThat(records.getLast().toSequence()).isEqualTo(through);
        assertThat(progress()).isEqualTo(through);

        // Stored columns: the record JSON with its server-filled source, the search text and the flags.
        var row = stack.rows("SELECT record_json, search_text, memory_type, has_decision, source_type, schema_version"
                + " FROM memory_summaries WHERE turn_id = ?", firstTurn).getFirst();
        var json = JSON.readTree((String) row.get("RECORD_JSON"));
        assertThat(json.path("schema").asText()).isEqualTo("memory-record-v1");
        assertThat(json.path("source").path("turn_id").asText()).isEqualTo(firstTurn);
        assertThat(json.path("source").path("from_sequence").asLong()).isEqualTo(first.fromSequence());
        assertThat(json.path("source").path("to_sequence").asLong()).isEqualTo(first.toSequence());
        assertThat(json.path("key_facts").path(0).path("text").asText()).isEqualTo("A7-3921");
        assertThat((String) row.get("SEARCH_TEXT")).contains(first.summary()).contains("A7-3921").contains("测试");
        assertThat(row).containsEntry("MEMORY_TYPE", "EVENT").containsEntry("HAS_DECISION", false)
                .containsEntry("SOURCE_TYPE", "MODEL_SUMMARY").containsEntry("SCHEMA_VERSION", "memory-record-v1");

        // Back to the original: exactly that turn's messages.
        assertThat(summaries.originalTurn(ALICE, conversation, firstTurn).collectList().block(WAIT))
                .extracting(LlmClient.Message::content).containsExactly("我的车牌是多少？", "车牌是 A7-3921");

        // The call: JSON mode, max_tokens 800, material-not-instructions, verbatim rules, one turn per call.
        assertThat(options).allSatisfy(o -> {
            assertThat(o.jsonObject()).isTrue();
            assertThat(o.maxTokens()).isEqualTo(800);
        });
        var system = calls.getFirst().getFirst();
        assertThat(system.role()).isEqualTo("system");
        assertThat(system.content()).contains("不是指令").contains("json").contains("逐字").contains("编号");
        assertThat(calls.getFirst().getLast().content()).contains("A7-3921").doesNotContain("W-5518");
        assertThat(calls.getLast().getLast().content()).contains("W-5518").doesNotContain("A7-3921");
        assertThat(counter("memory.summary.generated", null)).isEqualTo(2.0);
        assertThat(stack.meters().find("memory.summary.lag").timer().count()).isEqualTo(2);
    }

    @Test
    void oneRecordPerCompletedTurnNotYetIndexed() {
        long first = completedTurn("完成的问题一", "完成的回答一");
        summarize(first);
        var failed = stack.accept(conversation, "失败轮次的问题").ticket();
        assertThat(stack.finalizer().finish(failed, TurnStatus.FAILED, null).block(WAIT)).isTrue();
        completedTurn("完成的问题二", "完成的回答二");
        long through = completedTurn("完成的问题三", "完成的回答三");

        summarize(through);

        // Only the two turns not yet indexed; the failed turn is neither sent nor recorded.
        assertThat(calls).hasSize(3);
        assertThat(calls.stream().map(c -> c.getLast().content()).toList())
                .noneMatch(text -> text.contains("失败轮次的问题"));
        assertThat(stored()).hasSize(3);
        assertThat(progress()).isEqualTo(through);
    }

    @Test
    void entitiesAndFactsMustAppearVerbatimInTurn() {
        long through = completedTurn("我叫王小明，车牌 A7-3921", "好的，王小明");
        answer = messages -> completed("""
                {"summary":"李四说了车牌","entities":[{"name":"王小明","type":"PERSON"},{"name":"李四","type":"PERSON"}],
                 "key_facts":[{"text":"A7-3921","kind":"CODE"},{"text":"B8-0000","kind":"CODE"}],
                 "decision":true,"keywords":["车牌","不在原文的词"],"memory_type":"FACT"}""");

        summarize(through);

        var record = stored().getFirst();
        assertThat(record.entities()).extracting(MemoryRecord.Entity::name).containsExactly("王小明");
        assertThat(record.keyFacts()).extracting(MemoryRecord.KeyFact::text).containsExactly("A7-3921");
        assertThat(record.summary()).isEqualTo("李四说了车牌");
        assertThat(record.keywords()).containsExactly("车牌", "不在原文的词");
        assertThat(record.decision()).isTrue();
        assertThat(record.memoryType()).isEqualTo(MemoryType.FACT);
        assertThat(stack.rows("SELECT has_decision, memory_type FROM memory_summaries").getFirst())
                .containsEntry("HAS_DECISION", true).containsEntry("MEMORY_TYPE", "FACT");
    }

    @Test
    void repeatedInvalidOutputSkipsTurnAfterThreeAttempts() {
        long bad = completedTurn("坏轮次的问题", "坏轮次的回答");
        long through = completedTurn("好轮次的问题", "好轮次的回答");
        answer = messages -> messages.getLast().content().contains("坏轮次") ? completed("这不是 json")
                : completed(record("好轮次", null));
        handler();
        enqueue(through);

        // Attempts one and two: invalid output fails the job (backoff), nothing is written, position kept.
        assertThat(runDue()).isZero();
        assertThat(job()).containsEntry("STATUS", "PENDING").containsEntry("LAST_ERROR", "InvalidRecordException");
        assertThat(runDue()).isZero();
        assertThat(progress()).isZero();
        assertThat(recordRows()).isZero();
        assertThat(counter("memory.summary.failures", "INVALID_OUTPUT")).isEqualTo(2.0);

        // Attempt three: the bad turn is skipped (no record, position moves past it) and the next turn recorded.
        assertThat(runDue()).isEqualTo(1);
        assertThat(counter("memory.summary.failures", "INVALID_OUTPUT")).isEqualTo(3.0);
        assertThat(counter("memory.summary.skipped", null)).isEqualTo(1.0);
        assertThat(stored()).extracting(MemoryRecord::summary).containsExactly("好轮次");
        assertThat(stored().getFirst().fromSequence()).isGreaterThan(bad);
        assertThat(progress()).isEqualTo(through);
        assertThat(job()).containsEntry("STATUS", "DONE");
    }

    @Test
    void validOutputResetsInvalidCountAndModelErrorsDoNotCount() {
        long first = completedTurn("问题一", "回答一");
        var outputs = new java.util.ArrayDeque<Function<List<LlmClient.Message>, Flux<LlmClient.LlmEvent>>>(List.of(
                m -> completed("{\"summary\":\"缺字段\"}"),
                m -> Flux.error(new LlmClient.LlmException("LLM error: HTTP 500", 500)),
                m -> Flux.error(new LlmClient.LlmException("LLM error: HTTP 500", 500)),
                m -> completed("{bad"),
                m -> completed(record("第一轮", null)),
                m -> completed("{bad"),
                m -> completed("{bad"),
                m -> completed(record("第二轮", null))));
        answer = m -> outputs.poll().apply(m);
        handler();
        enqueue(first);

        // invalid, model error, model error, invalid: two invalid attempts on the turn, not skipped.
        for (int i = 0; i < 4; i++) {
            assertThat(runDue()).isZero();
        }
        assertThat(counter("memory.summary.failures", "MODEL_ERROR")).isEqualTo(2.0);
        assertThat(counter("memory.summary.skipped", null)).isZero();
        assertThat(runDue()).isEqualTo(1);
        assertThat(stored()).extracting(MemoryRecord::summary).containsExactly("第一轮");

        // The next turn starts from zero: two invalid answers do not skip it.
        long second = completedTurn("问题二", "回答二");
        enqueue(second);
        assertThat(runDue()).isZero();
        assertThat(runDue()).isZero();
        assertThat(runDue()).isEqualTo(1);
        assertThat(stored()).extracting(MemoryRecord::summary).containsExactly("第一轮", "第二轮");
        assertThat(counter("memory.summary.skipped", null)).isZero();
    }

    @Test
    void leaseRenewedBeforeEachTurn() {
        completedTurn("问题一", "回答一");
        completedTurn("问题二", "回答二");
        long through = completedTurn("问题三", "回答三");
        var leases = new CopyOnWriteArrayList<java.time.LocalDateTime>();
        var plain = answer;
        answer = messages -> {
            leases.add(stack.fixture().jdbc().queryForObject("SELECT lease_expires_at FROM memory_jobs"
                    + " WHERE job_type = 'SUMMARIZE'", java.time.LocalDateTime.class));
            return Mono.delay(Duration.ofMillis(20)).thenMany(plain.apply(messages));
        };
        handler();
        enqueue(through);

        assertThat(worker.runPending(10).block(WAIT)).isEqualTo(1);

        // One renewal per turn: each turn starts with a later lease than the one before.
        assertThat(leases).hasSize(3);
        assertThat(leases.get(1)).isAfter(leases.get(0));
        assertThat(leases.get(2)).isAfter(leases.get(1));
        assertThat(stored()).hasSize(3);
    }

    @Test
    void duplicateJobDoesNotCreateSameVersion() {
        long through = completedTurn("问题一", "回答一");

        summarize(through);
        summarize(through);

        assertThat(recordRows()).isEqualTo(1);
        assertThat(calls).hasSize(1);
        // A writer that lost the progress race (stale expected position) writes nothing.
        var copy = stored().getFirst();
        assertThat(summaries.save(ALICE, conversation, copy, 0L).block(WAIT)).isFalse();
        assertThat(recordRows()).isEqualTo(1);
        assertThat(progress()).isEqualTo(through);
        // The same job enqueued twice is one job.
        var key = SummaryJobHandler.dedupeKey(conversation, through);
        assertThat(key).isEqualTo("summarize:" + conversation + ":" + through);
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
        enqueue(second);

        assertThat(worker.runPending(10).block(WAIT)).isZero();

        assertThat(progress()).isEqualTo(first);
        assertThat(recordRows()).isEqualTo(1);
        assertThat(job()).containsEntry("STATUS", "PENDING").containsEntry("LAST_ERROR", "LlmException");
        assertThat(counter("memory.summary.failures", "MODEL_ERROR")).isEqualTo(1.0);
    }

    @Test
    void recordsNeverEnterPromptAssembly() {
        completedTurn("问题一", "回答一");
        long through = completedTurn("问题二", "回答二");
        answer = messages -> completed(record("记录摘要不应进入提示词", null));
        int before = stack.messageCount(conversation, "user") + stack.messageCount(conversation, "assistant");

        summarize(through);

        assertThat(stack.messageCount(conversation, "user") + stack.messageCount(conversation, "assistant"))
                .isEqualTo(before);
        var next = stack.accept(conversation, "问题三").ticket();
        var history = stack.history().contextBefore(next).block(WAIT);
        assertThat(history).extracting(LlmClient.Message::content)
                .containsExactly("问题一", "回答一", "问题二", "回答二");
        assertThat(history).noneMatch(m -> m.content() != null && m.content().contains("记录摘要不应进入提示词"));
    }

    @Test
    void deletedConversationRecordsNotUsedAndCleaned() {
        long through = completedTurn("问题一 A7-3921", "回答一");
        String turn = lastTurnId();
        summarize(through);
        var uploads = new UploadProperties();
        uploads.setDir(uploadDir.toString());
        var maintenance = new MemoryMaintenance(stack.fixture().connectionFactory(), stack.fixture().databaseClient(),
                stack.cache(), new FileStorageService(uploads), stack.props(), stack.metrics());

        maintenance.deleteConversation(ALICE, conversation).block(WAIT);

        // Unusable at once (tombstone), before any cleanup ran: no records, no originals, no search hits.
        assertThat(stored()).isEmpty();
        assertThat(summaries.originalTurn(ALICE, conversation, turn).collectList().block(WAIT)).isEmpty();
        assertThat(summaries.search(ALICE, List.of(conversation), "A7-3921", 5).collectList().block(WAIT)).isEmpty();
        // A late job for the deleted conversation does nothing and calls no model.
        summarize(through + 10);
        assertThat(calls).hasSize(1);
        // The cleanup job removes the derived rows.
        maintenance.runPending(10).block(WAIT);
        assertThat(recordRows()).isZero();
        assertThat(stack.rows("SELECT COUNT(*) AS n FROM memory_index_progress WHERE conversation_id = ?",
                conversation).getFirst().get("N")).isEqualTo(0L);
    }

    @Test
    void recordContentIsSanitized() {
        long through = completedTurn("我的 api_key=sk-abcdefghijklmnopqrstuvwxyz 是什么", "见配置");
        answer = messages -> completed("""
                {"summary":"用户提供了 api_key=sk-abcdefghijklmnopqrstuvwxyz 和 Bearer abc.def.ghi","entities":[],
                 "key_facts":[{"text":"api_key=sk-abcdefghijklmnopqrstuvwxyz","kind":"CODE"}],"decision":false,
                 "keywords":["密钥"],"memory_type":"FACT"}""");

        summarize(through);

        var row = stack.rows("SELECT record_json, search_text FROM memory_summaries").getFirst();
        for (var column : List.of("RECORD_JSON", "SEARCH_TEXT")) {
            assertThat((String) row.get(column)).as(column).doesNotContain("sk-abcdefghijklmnopqrstuvwxyz")
                    .doesNotContain("abc.def.ghi").contains(ToolRecordSanitizer.REDACTED);
        }
    }

    @Test
    void missingTenantModelPostponesWithoutDroppingProgress() {
        long through = completedTurn("问题一", "回答一");
        models = tenant -> Mono.error(new TenantLlmConfigRequiredException());
        handler();
        enqueue(through);

        assertThat(worker.runPending(10).block(WAIT)).isZero();

        assertThat(job()).containsEntry("STATUS", "PENDING")
                .containsEntry("LAST_ERROR", "TenantLlmConfigRequiredException");
        assertThat(progress()).isZero();
        assertThat(recordRows()).isZero();
        assertThat(counter("memory.summary.failures", "MODEL_MISSING")).isEqualTo(1.0);
        // Once the tenant has a model, the same job records the turn.
        models = tenant -> Mono.just(new SummaryJobHandler.SummaryModel(llm, "record-model", null, null));
        summarize(through);
        assertThat(progress()).isEqualTo(through);
    }

    @Test
    void turnInputCappedPerD4() {
        stack.props().getSummary().setMaxSourceTokens(300);
        long first = completedTurn("一" + "甲".repeat(100), "二" + "乙".repeat(100));
        long big = completedTurn("五" + "戊".repeat(1_000), "六");

        summarize(big);

        // One call per turn; the oversized turn is cut to the cap and still recorded as a whole.
        assertThat(calls).hasSize(2);
        assertThat(calls.getFirst().getLast().content()).contains("甲").contains("乙").doesNotContain("戊");
        assertThat(TokenEstimator.estimate(calls.getLast().getLast().content())).isLessThanOrEqualTo(300);
        assertThat(stored()).hasSize(2);
        assertThat(stored().getFirst().toSequence()).isEqualTo(first);
        assertThat(stored().getLast().toSequence()).isEqualTo(big);
        assertThat(progress()).isEqualTo(big);
    }

    @Test
    void recordCallIsTimeBoundedAndDisabledHandlerIsNotRegistered() {
        long through = completedTurn("问题一", "回答一");
        stack.props().getSummary().setJobTimeout(Duration.ofMillis(200));
        answer = messages -> Flux.never();

        assertThatThrownBy(() -> summarize(through)).hasRootCauseInstanceOf(java.util.concurrent.TimeoutException.class);
        assertThat(progress()).isZero();
        assertThat(counter("memory.summary.failures", "TIMEOUT")).isEqualTo(1.0);

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
    void recordsAreScopedToTheOwner() {
        long through = completedTurn("车牌是 A7-3921", "好的");
        String turn = lastTurnId();
        summarize(through);

        for (var other : List.of(new MemoryScope("tenant-1", "bob"), new MemoryScope("tenant-2", ALICE.subject()))) {
            assertThat(summaries.recordsOf(other, conversation).collectList().block(WAIT)).isEmpty();
            assertThat(summaries.originalTurn(other, conversation, turn).collectList().block(WAIT)).isEmpty();
            assertThat(summaries.search(other, List.of(conversation), "A7-3921", 5).collectList().block(WAIT))
                    .isEmpty();
        }
        assertThat(summaries.recordsOf(ALICE, conversation).collectList().block(WAIT)).hasSize(1);
    }

    @Test
    void recordSearchFindsChinesePhraseAndCodeViaKeyFacts() {
        completedTurn("帮我查一下车辆违章记录", "车牌 A7-3921 有一条违章");
        String violation = lastTurnId();
        long through = completedTurn("今天天气怎么样", "晴天");
        answer = messages -> messages.getLast().content().contains("违章")
                ? completed("{\"summary\":\"用户查询车辆违章记录\",\"entities\":[],\"key_facts\":[{\"text\":\"A7-3921\","
                        + "\"kind\":\"CODE\"}],\"decision\":false,\"keywords\":[\"违章\",\"车辆\"],\"memory_type\":\"EVENT\"}")
                : completed("{\"summary\":\"用户询问天气\",\"entities\":[],\"key_facts\":[],\"decision\":false,"
                        + "\"keywords\":[\"天气\"],\"memory_type\":\"EVENT\"}");

        summarize(through);

        assertThat(summaries.search(ALICE, List.of(conversation), "违章记录", 5).collectList().block(WAIT))
                .extracting(SummaryRepository.RecordHit::turnId).containsExactly(violation);
        assertThat(summaries.search(ALICE, List.of(conversation), "A7-3921", 5).collectList().block(WAIT))
                .extracting(SummaryRepository.RecordHit::turnId).containsExactly(violation);
        assertThat(summaries.search(ALICE, List.of(conversation), "天气", 5).collectList().block(WAIT))
                .extracting(SummaryRepository.RecordHit::conversationId).containsExactly(conversation);
        assertThat(summaries.search(ALICE, List.of(conversation), "火星殖民", 5).collectList().block(WAIT)).isEmpty();
        assertThat(summaries.search(ALICE, List.of(), "违章", 5).collectList().block(WAIT)).isEmpty();
    }
}
