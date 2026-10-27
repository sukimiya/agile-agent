package io.github.sukimiya.agileagent.memory.retention;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.time.Duration;
import java.util.List;

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
import io.github.sukimiya.agileagent.memory.items.EntityGraphRepository;
import io.github.sukimiya.agileagent.memory.items.ItemStatus;
import io.github.sukimiya.agileagent.memory.items.MemoryItem;
import io.github.sukimiya.agileagent.memory.items.MemoryItemRepository;
import io.github.sukimiya.agileagent.memory.items.MemoryItemService;
import io.github.sukimiya.agileagent.memory.items.MemoryItemSourceRepository;
import io.github.sukimiya.agileagent.memory.items.ProfileCache;
import io.github.sukimiya.agileagent.memory.items.ProfileService;
import io.github.sukimiya.agileagent.memory.jobs.MemoryJobWorker;
import io.github.sukimiya.agileagent.memory.judgement.ValidatedAction.SourceRef;
import io.github.sukimiya.agileagent.memory.recall.ChunkIndexer;
import io.github.sukimiya.agileagent.memory.recall.ChunkRepository;
import io.github.sukimiya.agileagent.memory.recall.FullTextSearch;
import io.github.sukimiya.agileagent.memory.recall.MemoryRetriever;
import io.github.sukimiya.agileagent.memory.recall.MemorySettingsService;
import io.github.sukimiya.agileagent.memory.recall.RecallCandidate;
import io.github.sukimiya.agileagent.memory.summary.MemoryRecord;
import io.github.sukimiya.agileagent.memory.summary.SummaryRepository;
import io.github.sukimiya.agileagent.service.FileStorageService;
import io.github.sukimiya.agileagent.util.NonBlockingIds;

/**
 * Q4 redesign (M2b): every delete is soft ({@code is_valid = 0}); conversation data stays queryable but never
 * reaches history, recall or the API; derived items are deactivated and marked source-deleted; the periodic
 * purge removes derived index rows after a retention picked by their recall reference count. H2 here;
 * {@link SoftDeleteRetentionMySqlIntegrationTest} on MySQL.
 */
class SoftDeleteRetentionTest {

    static final Duration WAIT = Duration.ofSeconds(15);
    static final MemoryScope ALICE = MemoryTestStack.ALICE;

    MemoryTestStack stack;
    String conversation;
    String other;
    MemoryJobWorker worker;
    SummaryRepository summaries;
    MemoryRetriever retriever;
    MemoryMaintenance maintenance;
    MemoryItemRepository items;
    MemoryItemSourceRepository sources;
    EntityGraphRepository graph;
    MemoryItemService itemService;
    MemoryIndexPurger purger;
    java.nio.file.Path uploadDir;

    protected MemoryTestFixture newFixture() {
        return MemoryTestFixture.h2();
    }

    @BeforeEach
    void setUp() throws Exception {
        stack = MemoryTestStack.of(newFixture());
        conversation = MemoryTestStack.newConversationId();
        other = MemoryTestStack.newConversationId();
        var db = stack.fixture().databaseClient();
        worker = new MemoryJobWorker(db, () -> false);
        var chunks = new ChunkRepository(db, stack.conversations());
        new ChunkIndexer(db, stack.fixture().connectionFactory(), chunks, worker, stack.props());
        var settings = new MemorySettingsService(db);
        summaries = new SummaryRepository(db, stack.conversations());
        retriever = new MemoryRetriever(db, stack.props(), settings, chunks, summaries, FullTextSearch.forDatabase(db),
                stack.metrics());
        var uploads = new UploadProperties();
        uploadDir = Files.createTempDirectory("soft-delete");
        uploads.setDir(uploadDir.toString());
        maintenance = new MemoryMaintenance(stack.fixture().connectionFactory(), db, stack.cache(),
                new FileStorageService(uploads), stack.props(), stack.metrics());
        items = new MemoryItemRepository(db);
        sources = new MemoryItemSourceRepository(db);
        graph = new EntityGraphRepository(db);
        var cache = new ProfileCache(null, stack.props());
        var profiles = new ProfileService(db, items, new MemorySettingsService(db, cache), cache, stack.props());
        itemService = new MemoryItemService(db, stack.fixture().connectionFactory(), items, sources, graph, profiles,
                stack.props());
        purger = new MemoryIndexPurger(db, stack.props(), stack.metrics());
    }

    @AfterEach
    void tearDown() {
        stack.close();
    }

    // --- helpers -------------------------------------------------------------------------------------------

    String completedTurn(String conversationId, String question, String answer) {
        var ticket = stack.accept(ALICE, conversationId, MemoryTestStack.newConversationId(), question).ticket();
        assertThat(stack.finalizer().finish(ticket, TurnStatus.COMPLETED, answer).block(WAIT)).isTrue();
        return ticket.turnId();
    }

    void index() {
        worker.runPending(100).block(WAIT);
    }

    void record(String conversationId, String turnId, String summary) {
        var range = stack.rows("SELECT MIN(sequence) AS f, MAX(sequence) AS t, MIN(id) AS fid, MAX(id) AS tid"
                + " FROM chat_messages WHERE turn_id = ?", turnId).getFirst();
        long position = summaries.summarizedThrough(ALICE, conversationId).block(WAIT);
        var tx = org.springframework.transaction.reactive.TransactionalOperator.create(
                new org.springframework.r2dbc.connection.R2dbcTransactionManager(stack.fixture().connectionFactory()));
        assertThat(tx.transactional(summaries.save(ALICE, conversationId, new MemoryRecord("r-" + turnId, turnId,
                ((Number) range.get("F")).longValue(), ((Number) range.get("T")).longValue(), (String) range.get("FID"),
                (String) range.get("TID"), summary, List.of(), List.of(), false, List.of(summary),
                MemoryRecord.MemoryType.FACT, "m", "memory-record-v1", 1), position)).block(WAIT)).isTrue();
    }

    TurnTicket ask(String conversationId, String question) {
        return stack.accept(ALICE, conversationId, MemoryTestStack.newConversationId(), question).ticket();
    }

    List<String> recalledTurns(String conversationId, String question) {
        var ticket = ask(conversationId, question);
        try {
            return retriever.recall(ticket, question).block(WAIT).items().stream().map(RecallCandidate::turnId)
                    .toList();
        } finally {
            stack.finalizer().finish(ticket, TurnStatus.FAILED, "").block(WAIT);
        }
    }

    MemoryItem item(String conversationId, String content, String... sourceConversations) {
        var item = MemoryItem.newItem(ALICE, conversationId, "PREFERENCE", content, ItemStatus.ACTIVE,
                MemoryItem.Assertion.USER_STATED, 0.5, 0.9, 1, "m", "r");
        items.save(item).block(WAIT);
        var refs = java.util.Arrays.stream(sourceConversations)
                .map(c -> new SourceRef(NonBlockingIds.uuid(), c, SourceType.USER_INPUT, content)).toList();
        sources.saveAll(item.id(), refs).block(WAIT);
        return item;
    }

    void references(String kind, String refId, int n, String conversationId) {
        for (int i = 0; i < n; i++) {
            stack.sql("INSERT INTO memory_recall_events (id, tenant_id, subject, conversation_id, turn_id, kind, ref_id,"
                    + " reason, score, `rank`, source_types, created_at) VALUES (?, ?, ?, ?, ?, ?, ?, 'X', 1, 1, '',"
                    + " CURRENT_TIMESTAMP(6))", NonBlockingIds.uuid(), ALICE.tenantId(), ALICE.subject(),
                    conversationId, "turn-" + i, kind, refId);
        }
    }

    long count(String sql, Object... args) {
        return ((Number) stack.rows(sql, args).getFirst().values().iterator().next()).longValue();
    }

    void deleteConversation() {
        maintenance.deleteConversation(ALICE, conversation).block(WAIT);
        maintenance.runPending(10).block(WAIT);
    }

    void itemsDeletedHoursAgo(long hours) {
        stack.sql("UPDATE memory_items SET source_deleted_at = TIMESTAMPADD(HOUR, -" + hours + ", CURRENT_TIMESTAMP(6))"
                + " WHERE source_deleted_at IS NOT NULL");
    }

    void conversationDeletedHoursAgo(long hours) {
        stack.sql("UPDATE sessions SET deleted_at = TIMESTAMPADD(HOUR, -" + hours + ", CURRENT_TIMESTAMP(6))"
                + " WHERE id = ?", conversation);
    }

    boolean exists(String id) {
        return count("SELECT COUNT(*) FROM memory_items WHERE id = ?", id) == 1;
    }

    // --- soft delete ---------------------------------------------------------------------------------------

    @Test
    void deleteKeepsConversationDataButInvalidatesIt() throws Exception {
        var turn = completedTurn(conversation, "我的车牌是 A7-3921", "好的，已记下");
        completedTurn(conversation, "第二个问题", "第二个回答");
        index();
        record(conversation, turn, "车牌 A7-3921");
        references("CHUNK", "chunk-x", 1, conversation);
        Files.createDirectories(uploadDir.resolve(conversation));
        Files.writeString(uploadDir.resolve(conversation).resolve("a.md"), "附件");
        long messages = count("SELECT COUNT(*) FROM chat_messages WHERE conversation_id = ?", conversation);
        long chunks = count("SELECT COUNT(*) FROM memory_chunks WHERE conversation_id = ?", conversation);
        assertThat(messages).isEqualTo(4);
        assertThat(chunks).isPositive();

        deleteConversation();

        assertThat(stack.rows("SELECT title, is_valid FROM sessions WHERE id = ?", conversation).getFirst())
                .containsEntry("IS_VALID", 0);
        assertThat((String) stack.rows("SELECT title FROM sessions WHERE id = ?", conversation).getFirst()
                .get("TITLE")).as("title kept (soft delete)").isNotBlank();
        for (var table : List.of("chat_messages", "chat_turns", "memory_chunks", "memory_summaries",
                "memory_recall_events")) {
            assertThat(count("SELECT COUNT(*) FROM " + table + " WHERE conversation_id = ?", conversation))
                    .as(table + " rows stay queryable").isPositive();
            assertThat(count("SELECT COUNT(*) FROM " + table + " WHERE conversation_id = ? AND is_valid = 1",
                    conversation)).as(table + " all invalid").isZero();
        }
        assertThat(count("SELECT COUNT(*) FROM chat_messages WHERE conversation_id = ?", conversation))
                .isEqualTo(messages);
        assertThat(uploadDir.resolve(conversation).resolve("a.md")).as("attachments kept (soft)").exists();
        // Another live conversation does not see it through recall.
        completedTurn(other, "别的会话", "好");
        assertThat(recalledTurns(other, "A7-3921 有违章吗")).doesNotContain(turn);
    }

    @Test
    void invalidRowsNeverReachHistoryRecallOrPrompt() {
        var hidden = completedTurn(conversation, "我的车牌是 A7-3921", "好的，已记下");
        var shown = completedTurn(conversation, "我的订单号是 W-5518", "收到");
        index();
        record(conversation, hidden, "车牌 A7-3921");
        for (var table : List.of("chat_messages", "memory_chunks", "memory_summaries")) {
            stack.sql("UPDATE " + table + " SET is_valid = 0 WHERE turn_id = ?", hidden);
        }
        stack.sql("UPDATE chat_turns SET is_valid = 0 WHERE id = ?", hidden);

        var page = stack.history().page(ALICE, conversation, Long.MAX_VALUE, 50).block(WAIT);
        assertThat(page.items()).hasSize(2);
        var ticket = ask(conversation, "A7-3921 有违章吗");
        try {
            var turns = stack.history().completedTurnsBefore(ticket).block(WAIT);
            assertThat(turns).extracting(t -> t.turnId()).containsExactly(shown);
            assertThat(retriever.recall(ticket, "A7-3921 有违章吗").block(WAIT).items())
                    .extracting(RecallCandidate::turnId).doesNotContain(hidden);
        } finally {
            stack.finalizer().finish(ticket, TurnStatus.FAILED, "").block(WAIT);
        }
        assertThat(recalledTurns(conversation, "W-5518 到哪了")).contains(shown);
    }

    @Test
    void derivedItemsAreDeactivatedAndMarkedSourceDeleted() {
        completedTurn(conversation, "q", "a");
        completedTurn(other, "q2", "a2");
        var sessionItem = item(conversation, "本会话偏好绿茶", conversation);
        var onlyHere = item(null, "用户喜欢绿茶", conversation);
        var shared = item(null, "用户喜欢中文", conversation, other);

        maintenance.deleteConversation(ALICE, conversation).block(WAIT);

        for (var gone : List.of(sessionItem, onlyHere)) {
            var row = stack.rows("SELECT status, source_deleted_at, content FROM memory_items WHERE id = ?", gone.id())
                    .getFirst();
            assertThat(row).containsEntry("STATUS", "INACTIVE").containsEntry("CONTENT", gone.content());
            assertThat(row.get("SOURCE_DELETED_AT")).isNotNull();
        }
        var kept = stack.rows("SELECT status, source_deleted_at FROM memory_items WHERE id = ?", shared.id())
                .getFirst();
        assertThat(kept).containsEntry("STATUS", "ACTIVE");
        assertThat(kept.get("SOURCE_DELETED_AT")).isNull();
        assertThat(count("SELECT COUNT(*) FROM memory_item_sources WHERE conversation_id = ?", conversation))
                .as("sources kept, invalid").isEqualTo(3);
        assertThat(count("SELECT COUNT(*) FROM memory_item_sources WHERE conversation_id = ? AND is_valid = 1",
                conversation)).isZero();
        assertThat(items.searchActive(ALICE, null, List.of("绿茶"), 10).collectList().block(WAIT)).isEmpty();
        assertThat(itemService.detail(ALICE, shared.id()).block(WAIT).sources()).singleElement()
                .satisfies(s -> assertThat(s.conversationId()).isEqualTo(other));
    }

    // --- purge by reference tiers ----------------------------------------------------------------------------

    @Test
    void purgeFollowsReferenceTiersFromSourceDeletion() {
        completedTurn(conversation, "q", "a");
        var none = item(conversation, "零引用", conversation);
        var few = item(conversation, "三次引用", conversation);
        var many = item(conversation, "六次引用", conversation);
        var e1 = graph.upsertEntity(ALICE, "甲", "OTHER").block(WAIT);
        var e2 = graph.upsertEntity(ALICE, "乙", "OTHER").block(WAIT);
        graph.saveRelation(ALICE, e1.id(), "LINKS", e2.id(), none.id(), "ACTIVE").block(WAIT);
        // References recorded inside the deleted conversation still count.
        references("ITEM", few.id(), 3, conversation);
        references("ITEM", many.id(), 6, other);
        deleteConversation();

        itemsDeletedHoursAgo(23);
        assertThat(purger.purgeExpired().block(WAIT).items()).isZero();
        assertThat(exists(none.id())).isTrue();

        itemsDeletedHoursAgo(25);
        assertThat(purger.purgeExpired().block(WAIT).items()).isEqualTo(1);
        assertThat(exists(none.id())).isFalse();
        assertThat(count("SELECT COUNT(*) FROM memory_item_sources WHERE item_id = ?", none.id())).isZero();
        assertThat(count("SELECT COUNT(*) FROM memory_relations WHERE item_id = ?", none.id())).isZero();
        assertThat(exists(few.id())).isTrue();

        itemsDeletedHoursAgo(24 * 8);
        assertThat(purger.purgeExpired().block(WAIT).items()).isEqualTo(1);
        assertThat(exists(few.id())).isFalse();
        assertThat(exists(many.id())).isTrue();

        itemsDeletedHoursAgo(24 * 31);
        assertThat(purger.purgeExpired().block(WAIT).items()).isEqualTo(1);
        assertThat(exists(many.id())).isFalse();

        assertThat(count("SELECT COUNT(*) FROM chat_messages WHERE conversation_id = ?", conversation))
                .as("raw conversation data is never purged").isEqualTo(2);
        assertThat(count("SELECT COUNT(*) FROM memory_recall_events WHERE ref_id = ?", many.id()))
                .as("recall records are kept").isEqualTo(6);
        assertThat(stack.meters().counter("memory.purge.purged", "kind", "ITEM").count()).isEqualTo(3);
    }

    @Test
    void chunksAndSummariesOfDeletedConversationsPurgedByTheirReferences() {
        var turn = completedTurn(conversation, "我的车牌是 A7-3921", "好的，已记下");
        index();
        record(conversation, turn, "车牌 A7-3921");
        references("SUMMARY", turn, 2, other);
        deleteConversation();

        conversationDeletedHoursAgo(48);
        var first = purger.purgeExpired().block(WAIT);
        assertThat(first.chunks()).isPositive();
        assertThat(first.summaries()).isZero();
        assertThat(count("SELECT COUNT(*) FROM memory_chunks WHERE conversation_id = ?", conversation)).isZero();
        assertThat(count("SELECT COUNT(*) FROM memory_summaries WHERE conversation_id = ?", conversation)).isOne();

        conversationDeletedHoursAgo(24 * 8);
        assertThat(purger.purgeExpired().block(WAIT).summaries()).isOne();
        assertThat(count("SELECT COUNT(*) FROM memory_summaries WHERE conversation_id = ?", conversation)).isZero();
        assertThat(count("SELECT COUNT(*) FROM chat_messages WHERE conversation_id = ?", conversation)).isEqualTo(2);
    }

    @Test
    void liveDataIsNeverPurged() {
        var turn = completedTurn(conversation, "我的车牌是 A7-3921", "好的，已记下");
        index();
        record(conversation, turn, "车牌 A7-3921");
        var active = item(null, "用户喜欢绿茶", conversation);
        var inactive = item(null, "用户喜欢红茶", conversation);
        items.markInactive(ALICE, inactive.id()).block(WAIT);
        stack.sql("UPDATE memory_chunks SET created_at = TIMESTAMPADD(DAY, -400, CURRENT_TIMESTAMP(6))");
        stack.sql("UPDATE memory_items SET created_at = TIMESTAMPADD(DAY, -400, CURRENT_TIMESTAMP(6)),"
                + " updated_at = TIMESTAMPADD(DAY, -400, CURRENT_TIMESTAMP(6))");

        var result = purger.purgeExpired().block(WAIT);

        assertThat(result.items() + result.chunks() + result.summaries()).isZero();
        assertThat(exists(active.id())).isTrue();
        assertThat(exists(inactive.id())).as("inactive but source not deleted").isTrue();
    }

    @Test
    void manualDecrementLowersTheTierAndPurgeEligibility() {
        completedTurn(conversation, "q", "a");
        var popular = item(conversation, "六次引用", conversation);
        references("ITEM", popular.id(), 6, other);
        deleteConversation();
        itemsDeletedHoursAgo(24 * 3);
        assertThat(purger.purgeExpired().block(WAIT).items()).isZero();

        var after = itemService.decrementRecallCount(ALICE, popular.id(), 5, "alice").block(WAIT);

        assertThat(after.count()).isEqualTo(1);
        assertThat(after.retention()).isEqualTo(Duration.ofDays(1));
        assertThat(after.purgeEligibleAt()).isNotNull();
        assertThat(purger.purgeExpired().block(WAIT).items()).isOne();
        assertThat(exists(popular.id())).isFalse();
    }
}
