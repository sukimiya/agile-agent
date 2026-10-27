package io.github.sukimiya.agileagent.memory.recall;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.github.sukimiya.agileagent.memory.MemoryModel.TurnStatus;
import io.github.sukimiya.agileagent.memory.MemoryModel.TurnTicket;
import io.github.sukimiya.agileagent.memory.MemoryScope;
import io.github.sukimiya.agileagent.memory.MemoryTestFixture;
import io.github.sukimiya.agileagent.memory.MemoryTestStack;
import io.github.sukimiya.agileagent.memory.SourceType;
import io.github.sukimiya.agileagent.memory.jobs.MemoryJobWorker;
import io.github.sukimiya.agileagent.memory.summary.MemoryRecord;
import io.github.sukimiya.agileagent.memory.summary.SummaryRepository;

/**
 * Basic recall (M2a Task 5, D22): deterministic code/name matching plus full-text search over turn records and
 * original-text chunks, merged per turn, expanded to the original turn after re-checking ownership; scoped to
 * the owner, the current conversation unless cross-conversation recall is on, never deleted conversations; weak
 * hits dropped; un-indexed turns back-filled within a limit and coverage reported. H2 here (LIKE),
 * {@link RecallMySqlIntegrationTest} on MySQL 8.4 (ngram FULLTEXT).
 */
class MemoryRetrieverTest {

    static final Duration WAIT = Duration.ofSeconds(10);
    static final MemoryScope ALICE = MemoryTestStack.ALICE;
    static final MemoryScope BOB = new MemoryScope(ALICE.tenantId(), "bob");
    static final MemoryScope OTHER_TENANT = new MemoryScope("tenant-2", ALICE.subject());

    MemoryTestStack stack;
    String conversation;
    MemoryJobWorker worker;
    MemorySettingsService settings;
    SummaryRepository summaries;
    MemoryRetriever retriever;

    protected MemoryTestFixture newFixture() {
        return MemoryTestFixture.h2();
    }

    @BeforeEach
    void setUp() {
        stack = MemoryTestStack.create(newFixture(), p -> {}, t -> t, MemoryTestStack.testRedis());
        conversation = MemoryTestStack.newConversationId();
        var db = stack.fixture().databaseClient();
        worker = new MemoryJobWorker(db, () -> false);
        var chunks = new ChunkRepository(db, stack.conversations());
        new ChunkIndexer(db, stack.fixture().connectionFactory(), chunks, worker, stack.props());
        settings = new MemorySettingsService(db);
        summaries = new SummaryRepository(db, stack.conversations());
        retriever = new MemoryRetriever(db, stack.props(), settings, chunks, summaries, FullTextSearch.forDatabase(db),
                stack.metrics());
    }

    @AfterEach
    void tearDown() {
        stack.close();
    }

    String completedTurn(MemoryScope scope, String conversationId, String question, String answer) {
        var ticket = stack.accept(scope, conversationId, MemoryTestStack.newConversationId(), question).ticket();
        assertThat(stack.finalizer().finish(ticket, TurnStatus.COMPLETED, answer).block(WAIT)).isTrue();
        return ticket.turnId();
    }

    String completedTurn(String question, String answer) {
        return completedTurn(ALICE, conversation, question, answer);
    }

    void index() {
        worker.runPending(100).block(WAIT);
    }

    TurnTicket ask(String conversationId, String question) {
        return stack.accept(ALICE, conversationId, MemoryTestStack.newConversationId(), question).ticket();
    }

    RecallResult recall(String conversationId, String question) {
        var ticket = ask(conversationId, question);
        try {
            return retriever.recall(ticket, question).block(WAIT);
        } finally {
            stack.finalizer().finish(ticket, TurnStatus.FAILED, "").block(WAIT);
        }
    }

    List<String> turns(RecallResult result) {
        return result.items().stream().map(RecallCandidate::turnId).toList();
    }

    void record(String turnId, String summary, List<String> keywords) {
        var range = stack.rows("SELECT MIN(sequence) AS f, MAX(sequence) AS t, MIN(id) AS fid, MAX(id) AS tid"
                + " FROM chat_messages WHERE turn_id = ?", turnId).getFirst();
        long from = ((Number) range.get("F")).longValue();
        long to = ((Number) range.get("T")).longValue();
        long position = summaries.summarizedThrough(ALICE, conversation).block(WAIT);
        var tx = org.springframework.transaction.reactive.TransactionalOperator.create(
                new org.springframework.r2dbc.connection.R2dbcTransactionManager(stack.fixture().connectionFactory()));
        assertThat(tx.transactional(summaries.save(ALICE, conversation, new MemoryRecord("r-" + turnId, turnId, from,
                to, (String) range.get("FID"), (String) range.get("TID"), summary, List.of(), List.of(), false,
                keywords, MemoryRecord.MemoryType.EVENT, "m", "memory-record-v1", 1), position)).block(WAIT)).isTrue();
    }

    @Test
    void exactEntityMatchFindsCodes() {
        String plate = completedTurn("我的车牌是 A7-3921", "好的，已记下");
        String order = completedTurn("订单号是 W-5518", "收到");
        completedTurn("今天天气怎么样", "晴天");
        index();

        var byPlate = recall(conversation, "A7-3921 有违章吗");
        assertThat(turns(byPlate)).containsExactly(plate);
        assertThat(byPlate.items().getFirst().reason()).isEqualTo("ENTITY_EXACT");
        assertThat(turns(recall(conversation, "查一下 W-5518 到哪了"))).containsExactly(order);
    }

    @Test
    void recordHitInjectsOriginalTurnNotRecord() {
        String turn = completedTurn("我昨天闯红灯被拍了", "建议你在交管平台查询");
        completedTurn("今天天气怎么样", "晴天");
        index();
        record(turn, "用户咨询交通罚单处理", List.of("交通罚单", "闯红灯"));

        var result = recall(conversation, "上次说的交通罚单怎么处理");

        var item = result.items().getFirst();
        assertThat(item.turnId()).isEqualTo(turn);
        assertThat(item.reason()).isEqualTo("RECORD_FULLTEXT");
        assertThat(item.content()).isEqualTo("用户：我昨天闯红灯被拍了\n助手：建议你在交管平台查询")
                .doesNotContain("交通罚单");
        assertThat(item.sources()).containsExactly(SourceType.USER_INPUT, SourceType.ASSISTANT);
        assertThat(item.crossConversation()).isFalse();
        assertThat(item.finishedAt()).isNotNull();
    }

    @Test
    void recordAndChunkHitsMergedPerTurn() {
        String turn = completedTurn("帮我查车辆违章记录", "车辆违章记录已查到一条");
        index();
        record(turn, "用户查询车辆违章记录", List.of("违章记录"));

        var result = recall(conversation, "车辆违章记录");

        assertThat(turns(result)).containsExactly(turn);
    }

    @Test
    void recentTurnWithoutRecordFoundViaChunk() {
        String turn = completedTurn("我在考虑买一台电动汽车", "可以比较续航和充电网络");
        completedTurn("今天天气怎么样", "晴天");
        index();

        var result = recall(conversation, "电动汽车的续航怎么样");

        assertThat(turns(result)).containsExactly(turn);
        assertThat(result.items().getFirst().reason()).isEqualTo("CHUNK_FULLTEXT");
    }

    @Test
    void weakHitIsNotInjected() {
        completedTurn("我在考虑买一台电动汽车", "可以比较续航和充电网络");
        index();

        assertThat(recall(conversation, "你觉得电影怎么样").items()).isEmpty();
        assertThat(recall(conversation, "火星殖民计划").items()).isEmpty();
    }

    @Test
    void foreignUserAndTenantNeverRecalled() {
        var bobs = MemoryTestStack.newConversationId();
        completedTurn(BOB, bobs, "我的车牌是 A7-3921", "好的");
        var foreign = MemoryTestStack.newConversationId();
        completedTurn(OTHER_TENANT, foreign, "我的车牌是 A7-3921", "好的");
        index();
        settings.update(ALICE, true).block(WAIT);

        assertThat(recall(conversation, "A7-3921 有违章吗").items()).isEmpty();
    }

    @Test
    void deletedConversationNeverRecalledEvenIfIndexLags() {
        var other = MemoryTestStack.newConversationId();
        completedTurn(ALICE, other, "我的车牌是 A7-3921", "好的");
        index();
        settings.update(ALICE, true).block(WAIT);
        assertThat(recall(conversation, "A7-3921 有违章吗").items()).hasSize(1);

        // Tombstone only: chunks are still there, recall must not use them.
        stack.sql("UPDATE sessions SET deleted_at = CURRENT_TIMESTAMP(6), is_valid = 0 WHERE id = ?", other);
        assertThat(stack.rows("SELECT COUNT(*) AS n FROM memory_chunks WHERE conversation_id = ?", other).getFirst()
                .get("N")).isNotEqualTo(0L);

        assertThat(recall(conversation, "A7-3921 有违章吗").items()).isEmpty();
    }

    @Test
    void crossConversationOffByDefaultAndOnRecallsOwnOtherConversation() {
        var other = MemoryTestStack.newConversationId();
        String plate = completedTurn(ALICE, other, "我的车牌是 A7-3921", "好的，已记下");
        index();

        assertThat(settings.crossConversationEnabled(ALICE).block(WAIT)).isFalse();
        assertThat(recall(conversation, "我的车牌 A7-3921 有违章吗").items()).isEmpty();

        var on = settings.update(ALICE, true).block(WAIT);
        assertThat(on.crossConversationEnabled()).isTrue();
        var result = recall(conversation, "我的车牌 A7-3921 有违章吗");
        assertThat(turns(result)).containsExactly(plate);
        assertThat(result.items().getFirst().crossConversation()).isTrue();
        assertThat(result.items().getFirst().conversationId()).isEqualTo(other);

        // Off again: effective for the very next recall (nothing is cached).
        var off = settings.update(ALICE, false).block(WAIT);
        assertThat(off.revision()).isGreaterThan(on.revision());
        assertThat(recall(conversation, "我的车牌 A7-3921 有违章吗").items()).isEmpty();
    }

    @Test
    void unindexedRangeBeyondRecentWindowIsBackfilledWithinLimit() {
        String plate = completedTurn("我的车牌是 A7-3921", "好的");
        completedTurn("订单号是 W-5518", "收到");
        // No index run: both turns are un-indexed (the index lags).

        var result = recall(conversation, "A7-3921 有违章吗");
        assertThat(turns(result)).containsExactly(plate);
        assertThat(result.items().getFirst().reason()).isEqualTo("RECENT_GAP");
        assertThat(result.coverageLimited()).isFalse();

        // Above the back-fill limit only the newest un-indexed turns are scanned and coverage is limited.
        stack.props().getRecall().setBackfillTurns(1);
        var limited = recall(conversation, "A7-3921 有违章吗");
        assertThat(limited.items()).isEmpty();
        assertThat(limited.coverageLimited()).isTrue();
    }

    @Test
    void coverageLimitedIsReportedNotTreatedAsAbsent() {
        var other = MemoryTestStack.newConversationId();
        completedTurn(ALICE, other, "我的车牌是 A7-3921", "好的");
        settings.update(ALICE, true).block(WAIT);
        // The other conversation's turn is not indexed yet; it is not back-filled (only the current conversation is).

        var result = recall(conversation, "A7-3921 有违章吗");

        assertThat(result.items()).isEmpty();
        assertThat(result.coverageLimited()).isTrue();
        assertThat(stack.meters().find("memory.recall.coverage.limited").counter().count()).isEqualTo(1.0);
    }

    @Test
    void recallMetricsAndDisabledRecall() {
        completedTurn("我的车牌是 A7-3921", "好的");
        index();

        recall(conversation, "A7-3921 有违章吗");
        assertThat(stack.meters().find("memory.recall.latency").timer().count()).isEqualTo(1);

        stack.props().getRecall().setEnabled(false);
        var off = recall(conversation, "A7-3921 有违章吗");
        assertThat(off.items()).isEmpty();
        assertThat(off.coverageLimited()).isFalse();
        assertThat(stack.meters().find("memory.recall.latency").timer().count()).isEqualTo(1);
    }
}
