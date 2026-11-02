package io.github.sukimiya.agileagent.memory.recall;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.github.sukimiya.agileagent.config.UploadProperties;
import io.github.sukimiya.agileagent.memory.MemoryMaintenance;
import io.github.sukimiya.agileagent.memory.MemoryModel.TurnStatus;
import io.github.sukimiya.agileagent.memory.MemoryModel.TurnTicket;
import io.github.sukimiya.agileagent.memory.MemoryScope;
import io.github.sukimiya.agileagent.memory.MemoryTestFixture;
import io.github.sukimiya.agileagent.memory.MemoryTestStack;
import io.github.sukimiya.agileagent.memory.SourceType;
import io.github.sukimiya.agileagent.memory.context.AssembledContext;
import io.github.sukimiya.agileagent.memory.context.ContextAssembler;
import io.github.sukimiya.agileagent.memory.context.ContextBudget;
import io.github.sukimiya.agileagent.memory.context.ContextPurpose;
import io.github.sukimiya.agileagent.memory.context.ModelWindow;
import io.github.sukimiya.agileagent.memory.jobs.MemoryJobWorker;
import io.github.sukimiya.agileagent.memory.summary.SummaryRepository;
import io.github.sukimiya.agileagent.service.FileStorageService;
import io.github.sukimiya.agileagent.service.LlmClient;
import reactor.core.publisher.Mono;

/**
 * Per-turn recall explanation (M2b Task 5): only what the answer prompt actually contained is recorded, with kind,
 * reason, score, rank and source types; a failing write never fails the turn; deleting the conversation removes the
 * events. H2 here; {@link RecallEventMySqlIntegrationTest} on MySQL.
 */
class RecallEventRecorderTest {

    static final Duration WAIT = Duration.ofSeconds(15);
    static final MemoryScope ALICE = MemoryTestStack.ALICE;
    static final List<LlmClient.Message> PREFIX = List.of(LlmClient.Message.system("你是助手"));

    MemoryTestStack stack;
    String conversation;
    MemoryJobWorker worker;
    RecallEventRecorder recorder;
    ContextAssembler assembler;

    protected MemoryTestFixture newFixture() {
        return MemoryTestFixture.h2();
    }

    @BeforeEach
    void setUp() {
        stack = MemoryTestStack.of(newFixture());
        conversation = MemoryTestStack.newConversationId();
        var db = stack.fixture().databaseClient();
        worker = new MemoryJobWorker(db, () -> false);
        var chunks = new ChunkRepository(db, stack.conversations());
        new ChunkIndexer(db, stack.fixture().connectionFactory(), chunks, worker, stack.props());
        var settings = new MemorySettingsService(db);
        var retriever = new MemoryRetriever(db, stack.props(), settings, chunks,
                new SummaryRepository(db, stack.conversations()), FullTextSearch.forDatabase(db), stack.metrics());
        recorder = new RecallEventRecorder(db, stack.metrics(), 64);
        assembler = new ContextAssembler(stack.history(), new ContextBudget(stack.props()), stack.metrics(), retriever,
                recorder);
    }

    @AfterEach
    void tearDown() {
        recorder.close();
        stack.close();
    }

    void completedTurn(String question, String answer) {
        var ticket = stack.accept(ALICE, conversation, MemoryTestStack.newConversationId(), question).ticket();
        assertThat(stack.finalizer().finish(ticket, TurnStatus.COMPLETED, answer).block(WAIT)).isTrue();
    }

    void longConversation(String... early) {
        for (var fact : early) {
            completedTurn(fact, "好的，已记下");
        }
        for (int i = 0; i < 12; i++) {
            completedTurn("闲聊第" + i + "轮" + "嗯".repeat(80), "好的" + "哦".repeat(80));
        }
        worker.runPending(100).block(WAIT);
    }

    TurnTicket ask(String question) {
        return stack.accept(ALICE, conversation, MemoryTestStack.newConversationId(), question).ticket();
    }

    AssembledContext assemble(TurnTicket ticket, ContextPurpose purpose, String question, Mono<RecallResult> recall) {
        return assembler.assemble(ticket, purpose, new ModelWindow(1_500, 300), PREFIX, question, recall).block(WAIT);
    }

    List<Map<String, Object>> events(String turnId) {
        return stack.rows("SELECT kind, ref_id, reason, score, `rank`, source_types FROM memory_recall_events"
                + " WHERE turn_id = ? ORDER BY `rank`", turnId);
    }

    static long referenceItems(AssembledContext context) {
        return context.messages().stream().filter(m -> m.content() != null
                && m.content().startsWith(ContextAssembler.RECALL_HEADER))
                .mapToLong(m -> m.content().split("\\[资料 ", -1).length - 1).sum();
    }

    @Test
    void recallEventRecordsOnlyActuallyInjectedItems() {
        longConversation("我的车牌是 A7-3921", "我的订单号是 W-5518");
        var ticket = ask("A7-3921 有违章吗");

        // Routing and capability calls are not the answer: nothing recorded for them.
        assemble(ticket, ContextPurpose.ROUTING, "A7-3921 有违章吗", assembler.recall(ticket, "A7-3921 有违章吗"));
        recorder.flush(WAIT).block(WAIT);
        assertThat(events(ticket.turnId())).isEmpty();

        var context = assemble(ticket, ContextPurpose.DIRECT_ANSWER, "A7-3921 有违章吗",
                assembler.recall(ticket, "A7-3921 有违章吗"));
        recorder.flush(WAIT).block(WAIT);

        var rows = events(ticket.turnId());
        assertThat(referenceItems(context)).isPositive();
        assertThat(rows).hasSize((int) referenceItems(context));
        assertThat(rows).allSatisfy(r -> assertThat((String) r.get("KIND")).isIn("CHUNK", "SUMMARY"));

        // A candidate that did not fit (or sits in the recent window) is not recorded.
        stack.finalizer().finish(ticket, TurnStatus.COMPLETED, "没有").block(WAIT);
        var huge = new RecallCandidate("t-huge", null, conversation, 1, 2, List.of(SourceType.USER_INPUT),
                "RECORD_FULLTEXT", 1, false, null, "巨".repeat(5_000), null);
        var ticket2 = ask("另一个问题");
        assemble(ticket2, ContextPurpose.DIRECT_ANSWER, "另一个问题",
                Mono.just(new RecallResult(List.of(huge), false, Duration.ZERO)));
        recorder.flush(WAIT).block(WAIT);
        assertThat(events(ticket2.turnId())).isEmpty();
    }

    @Test
    void recallEventIncludesReasonScoreRankAndSources() {
        var ticket = ask("项目甲 用什么方案");
        var item = new RecallCandidate("item-1", null, "", 0, 0, List.of(SourceType.USER_INPUT, SourceType.ASSISTANT),
                "ITEM", 50.8, true, null, "用户负责项目甲", null);
        var chunk = new RecallCandidate("turn-9", "chunk-9", conversation, 3, 4, List.of(SourceType.USER_INPUT),
                "CHUNK_FULLTEXT", 2.5, false, null, "用户：项目甲采用方案乙", "项目甲采用方案乙");
        var recalled = new RecallResult(List.of(item, chunk), false, Duration.ZERO, "[偏好]\n- 喜欢中文", 7L,
                List.of(new RecallResult.RelationHit("rel-1", "项目甲 —USES→ 方案乙")));

        assemble(ticket, ContextPurpose.AGGREGATE, "项目甲 用什么方案", Mono.just(recalled));
        recorder.flush(WAIT).block(WAIT);

        var rows = events(ticket.turnId());
        assertThat(rows).extracting(r -> r.get("KIND")).containsExactly("PROFILE", "RELATION", "ITEM", "CHUNK");
        assertThat(rows).extracting(r -> ((Number) r.get("RANK")).intValue()).containsExactly(1, 2, 3, 4);
        assertThat(rows.get(0)).containsEntry("REF_ID", "7").containsEntry("REASON", "PROFILE");
        assertThat(rows.get(1)).containsEntry("REF_ID", "rel-1").containsEntry("REASON", "ONE_HOP");
        assertThat(rows.get(2)).containsEntry("REF_ID", "item-1").containsEntry("REASON", "ITEM")
                .containsEntry("SOURCE_TYPES", "USER_INPUT,ASSISTANT");
        assertThat(((Number) rows.get(2).get("SCORE")).doubleValue()).isEqualTo(50.8);
        assertThat(rows.get(3)).containsEntry("REF_ID", "chunk-9").containsEntry("REASON", "CHUNK_FULLTEXT")
                .containsEntry("SOURCE_TYPES", "USER_INPUT");

        var listed = recorder.forTurn(ALICE, conversation, ticket.turnId()).collectList().block(WAIT);
        assertThat(listed).extracting(RecallEventRecorder.StoredEvent::kind)
                .containsExactly("PROFILE", "RELATION", "ITEM", "CHUNK");
        // Another owner sees nothing for the same turn.
        assertThat(recorder.forTurn(new MemoryScope(ALICE.tenantId(), "bob"), conversation, ticket.turnId())
                .collectList().block(WAIT)).isEmpty();
    }

    @Test
    void recallEventWriteFailureDoesNotFailTurn() {
        stack.sql("DROP TABLE memory_recall_events");
        var ticket = ask("项目甲");
        var item = new RecallCandidate("item-1", null, "", 0, 0, List.of(SourceType.USER_INPUT), "ITEM", 50, true,
                null, "用户负责项目甲", null);
        var context = assemble(ticket, ContextPurpose.DIRECT_ANSWER, "项目甲",
                Mono.just(new RecallResult(List.of(item), false, Duration.ZERO)));
        assertThat(context.messages().getLast().content()).isEqualTo("项目甲");
        recorder.flush(WAIT).block(WAIT);
        assertThat(stack.meters().counter("memory.recall.explain", "outcome", "failed").count()).isEqualTo(1);
    }

    @Test
    void deletingConversationInvalidatesRecallEvents() throws Exception {
        var ticket = ask("项目甲");
        var item = new RecallCandidate("item-1", null, "", 0, 0, List.of(SourceType.USER_INPUT), "ITEM", 50, true,
                null, "用户负责项目甲", null);
        assemble(ticket, ContextPurpose.DIRECT_ANSWER, "项目甲",
                Mono.just(new RecallResult(List.of(item), false, Duration.ZERO)));
        recorder.flush(WAIT).block(WAIT);
        assertThat(events(ticket.turnId())).hasSize(1);
        stack.finalizer().finish(ticket, TurnStatus.COMPLETED, "好").block(WAIT);

        var uploads = new UploadProperties();
        uploads.setDir(java.nio.file.Files.createTempDirectory("recall-del").toString());
        var maintenance = new MemoryMaintenance(stack.fixture().connectionFactory(), stack.fixture().databaseClient(),
                stack.cache(), new FileStorageService(uploads), stack.props(), stack.metrics());
        maintenance.deleteConversation(ALICE, conversation).block(WAIT);
        maintenance.runPending(10).block(WAIT);
        // Soft delete: the rows stay (they still count as references) but are invalid and never explained.
        assertThat(events(ticket.turnId())).hasSize(1);
        assertThat(stack.rows("SELECT COUNT(*) AS n FROM memory_recall_events WHERE turn_id = ? AND is_valid = 1",
                ticket.turnId()).getFirst().get("N")).isEqualTo(0L);
        assertThat(recorder.forTurn(ALICE, conversation, ticket.turnId()).collectList().block(WAIT)).isEmpty();
    }
}
