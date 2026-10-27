package io.github.sukimiya.agileagent.memory.items;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.github.sukimiya.agileagent.memory.MemoryModel.TurnStatus;
import io.github.sukimiya.agileagent.memory.MemoryModel.TurnTicket;
import io.github.sukimiya.agileagent.memory.MemoryScope;
import io.github.sukimiya.agileagent.memory.MemoryTestFixture;
import io.github.sukimiya.agileagent.memory.MemoryTestStack;
import io.github.sukimiya.agileagent.memory.SourceType;
import io.github.sukimiya.agileagent.memory.context.ContextAssembler;
import io.github.sukimiya.agileagent.memory.context.ContextBudget;
import io.github.sukimiya.agileagent.memory.context.ContextPurpose;
import io.github.sukimiya.agileagent.memory.context.ModelWindow;
import io.github.sukimiya.agileagent.memory.items.MemoryItem.Assertion;
import io.github.sukimiya.agileagent.memory.jobs.MemoryJobWorker;
import io.github.sukimiya.agileagent.memory.judgement.ValidatedAction.SourceRef;
import io.github.sukimiya.agileagent.memory.recall.ChunkIndexer;
import io.github.sukimiya.agileagent.memory.recall.ChunkRepository;
import io.github.sukimiya.agileagent.memory.recall.FullTextSearch;
import io.github.sukimiya.agileagent.memory.recall.MemoryRetriever;
import io.github.sukimiya.agileagent.memory.recall.MemorySettingsService;
import io.github.sukimiya.agileagent.memory.recall.RecallCandidate;
import io.github.sukimiya.agileagent.memory.recall.RecallEventRecorder;
import io.github.sukimiya.agileagent.memory.summary.SummaryRepository;
import io.github.sukimiya.agileagent.service.LlmClient;
import io.github.sukimiya.agileagent.util.NonBlockingIds;

/**
 * M2b Task 6: items decay by their last recall (half-life 30 days, from {@code COALESCE(last_recalled_at,
 * created_at)}, applied before selection); only individually injected items refresh the recall time; INFERRED
 * items unrecalled for 180 days become INACTIVE (never deleted), user-stated and user-confirmed ones never do;
 * restore refreshes. Database time is the clock: tests move timestamps into the past. H2 here;
 * {@link MemoryDecayMySqlIntegrationTest} on MySQL.
 */
class MemoryDecayTest {

    static final Duration WAIT = Duration.ofSeconds(15);
    static final MemoryScope ALICE = MemoryTestStack.ALICE;

    MemoryTestStack stack;
    String conversation;
    MemoryItemRepository items;
    MemoryItemSourceRepository sources;
    MemoryItemService itemService;
    MemoryRetriever retriever;
    RecallEventRecorder recorder;
    ContextAssembler assembler;
    MemoryDecay decay;

    protected MemoryTestFixture newFixture() {
        return MemoryTestFixture.h2();
    }

    @BeforeEach
    void setUp() {
        stack = MemoryTestStack.of(newFixture());
        conversation = MemoryTestStack.newConversationId();
        var db = stack.fixture().databaseClient();
        var worker = new MemoryJobWorker(db, () -> false);
        var chunks = new ChunkRepository(db, stack.conversations());
        new ChunkIndexer(db, stack.fixture().connectionFactory(), chunks, worker, stack.props());
        items = new MemoryItemRepository(db);
        sources = new MemoryItemSourceRepository(db);
        var graph = new EntityGraphRepository(db);
        var cache = new ProfileCache(null, stack.props());
        var settings = new MemorySettingsService(db, cache);
        var profiles = new ProfileService(db, items, settings, cache, stack.props());
        itemService = new MemoryItemService(db, stack.fixture().connectionFactory(), items, sources, graph, profiles,
                stack.props());
        retriever = new MemoryRetriever(db, stack.props(), settings, chunks,
                new SummaryRepository(db, stack.conversations()), FullTextSearch.forDatabase(db), stack.metrics(),
                items, graph, profiles);
        recorder = new RecallEventRecorder(db, stack.metrics(), 64);
        assembler = new ContextAssembler(stack.history(), new ContextBudget(stack.props()), stack.metrics(), retriever,
                recorder);
        decay = new MemoryDecay(db, stack.props(), stack.metrics(), itemService);
        // The conversation exists (items belong to it).
        completedTurn("你好", "你好");
    }

    @AfterEach
    void tearDown() {
        recorder.close();
        stack.close();
    }

    // --- helpers -------------------------------------------------------------------------------------------

    void completedTurn(String question, String answer) {
        var ticket = stack.accept(ALICE, conversation, NonBlockingIds.uuid(), question).ticket();
        assertThat(stack.finalizer().finish(ticket, TurnStatus.COMPLETED, answer).block(WAIT)).isTrue();
    }

    MemoryItem item(String content, Assertion assertion, double importance) {
        var item = MemoryItem.newItem(ALICE, conversation, "FACT", content, ItemStatus.ACTIVE, assertion, importance,
                0.9, 1, "m", "r");
        items.save(item).block(WAIT);
        sources.saveAll(item.id(), List.of(new SourceRef(NonBlockingIds.uuid(), conversation, SourceType.USER_INPUT,
                content))).block(WAIT);
        return item;
    }

    /** Moves the item's creation {@code createdDaysAgo} and its last recall {@code recalledDaysAgo} (null: never). */
    void age(MemoryItem item, int createdDaysAgo, Integer recalledDaysAgo) {
        stack.sql("UPDATE memory_items SET created_at = TIMESTAMPADD(DAY, -" + createdDaysAgo
                + ", CURRENT_TIMESTAMP(6)), last_recalled_at = "
                + (recalledDaysAgo == null ? "NULL" : "TIMESTAMPADD(DAY, -" + recalledDaysAgo + ", CURRENT_TIMESTAMP(6))")
                + " WHERE id = ?", item.id());
    }

    Map<String, Object> row(MemoryItem item) {
        return stack.rows("SELECT status, is_valid, revision, last_recalled_at,"
                + " TIMESTAMPDIFF(SECOND, last_recalled_at, CURRENT_TIMESTAMP(6)) AS recalled_s"
                + " FROM memory_items WHERE id = ?", item.id()).getFirst();
    }

    /** Seconds since the last recall; null when never recalled. */
    Long recalledSecondsAgo(MemoryItem item) {
        var v = row(item).get("RECALLED_S");
        return v == null ? null : ((Number) v).longValue();
    }

    List<RecallCandidate> itemHits(String query) {
        var ticket = stack.accept(ALICE, conversation, NonBlockingIds.uuid(), query).ticket();
        try {
            return retriever.recall(ticket, query).block(WAIT).items().stream()
                    .filter(c -> "ITEM".equals(c.reason())).toList();
        } finally {
            stack.finalizer().finish(ticket, TurnStatus.FAILED, "").block(WAIT);
        }
    }

    static List<String> ids(List<RecallCandidate> hits) {
        return hits.stream().map(RecallCandidate::turnId).toList();
    }

    // --- ranking -------------------------------------------------------------------------------------------

    @Test
    void decayedScoreDecidesSelectionBeforeTheItemLimit() {
        var old = item("用户的车牌是 A7-3921", Assertion.USER_STATED, 0.9);
        var fresh = item("用户的车牌颜色是蓝色", Assertion.USER_STATED, 0.1);
        age(old, 200, 90);
        age(fresh, 1, 0);

        stack.props().getRecall().setMaxItems(1);
        assertThat(ids(itemHits("车牌"))).as("without decay the more important item would win")
                .containsExactly(fresh.id());

        stack.props().getRecall().setMaxItems(5);
        var hits = itemHits("车牌");
        assertThat(ids(hits)).containsExactly(fresh.id(), old.id());
        assertThat(hits.get(1).score()).isCloseTo((DecayPolicy.ITEM_BASE_SCORE + 0.9) * 0.125, within(0.01));
        assertThat(hits.get(0).score()).isCloseTo(DecayPolicy.ITEM_BASE_SCORE + 0.1, within(0.01));
    }

    @Test
    void neverRecalledItemsAgeFromCreation() {
        var never = item("用户的车牌是 A7-3921", Assertion.USER_STATED, 0.0);
        age(never, 60, null);

        assertThat(itemHits("车牌").getFirst().score()).isCloseTo(DecayPolicy.ITEM_BASE_SCORE * 0.25, within(0.01));
    }

    // --- refresh -------------------------------------------------------------------------------------------

    @Test
    void recallRefreshesLastRecalledAt() {
        var plate = item("用户的车牌是 A7-3921", Assertion.USER_STATED, 0.5);
        age(plate, 120, 100);
        long revision = ((Number) row(plate).get("REVISION")).longValue();
        var ticket = stack.accept(ALICE, conversation, NonBlockingIds.uuid(), "A7-3921 有违章吗").ticket();

        // A routing prompt is not an injection into the answer: nothing is refreshed.
        assembler.assemble(ticket, ContextPurpose.ROUTING, new ModelWindow(4_000, 500),
                List.of(LlmClient.Message.system("你是助手")), "A7-3921 有违章吗",
                assembler.recall(ticket, "A7-3921 有违章吗")).block(WAIT);
        recorder.flush(WAIT).block(WAIT);
        assertThat(recalledSecondsAgo(plate)).isGreaterThan(Duration.ofDays(99).toSeconds());

        assembler.assemble(ticket, ContextPurpose.DIRECT_ANSWER, new ModelWindow(4_000, 500),
                List.of(LlmClient.Message.system("你是助手")), "A7-3921 有违章吗",
                assembler.recall(ticket, "A7-3921 有违章吗")).block(WAIT);
        recorder.flush(WAIT).block(WAIT);

        assertThat(recalledSecondsAgo(plate)).isLessThan(60);
        assertThat(((Number) row(plate).get("REVISION")).longValue()).as("no content change, no revision bump")
                .isEqualTo(revision);
    }

    @Test
    void onlyItemEventsRefreshNotProfileOrRelations() {
        var injected = item("用户的车牌是 A7-3921", Assertion.USER_STATED, 0.5);
        var other = item("用户喜欢绿茶", Assertion.USER_STATED, 0.5);
        age(injected, 120, 100);
        age(other, 120, 100);
        var ticket = stack.accept(ALICE, conversation, NonBlockingIds.uuid(), "问题").ticket();

        recorder.submit(ticket, List.of(
                new RecallEventRecorder.Event("PROFILE", other.id(), "PROFILE", null, 1, List.of()),
                new RecallEventRecorder.Event("RELATION", other.id(), "ONE_HOP", null, 2, List.of()),
                new RecallEventRecorder.Event("CHUNK", other.id(), "CHUNK_FULLTEXT", 1.0, 3, List.of()),
                new RecallEventRecorder.Event("ITEM", injected.id(), "ITEM", 50.0, 4, List.of())));
        recorder.flush(WAIT).block(WAIT);

        assertThat(recalledSecondsAgo(injected)).isLessThan(60);
        assertThat(recalledSecondsAgo(other)).isGreaterThan(Duration.ofDays(99).toSeconds());
    }

    @Test
    void restoreRefreshesTheRecallTime() {
        var guess = item("用户可能住在杭州", Assertion.INFERRED, 0.5);
        itemService.forget(ALICE, List.of(guess.id()), ALICE.subject(), "FORGET_IN_CHAT").block(WAIT);
        age(guess, 400, 300);

        itemService.restore(ALICE, guess.id(), ALICE.subject()).block(WAIT);
        assertThat(recalledSecondsAgo(guess)).isLessThan(60);
        assertThat(decay.inactivateStale().block(WAIT)).isZero();
        assertThat(row(guess).get("STATUS")).isEqualTo("ACTIVE");
    }

    // --- inactivation --------------------------------------------------------------------------------------

    @Test
    void longUnrecalledBecomesInactiveNotDeleted() {
        var stale = item("用户可能住在杭州", Assertion.INFERRED, 0.5);
        var staleNever = item("用户可能养猫", Assertion.INFERRED, 0.5);
        var recent = item("用户可能喜欢爬山", Assertion.INFERRED, 0.5);
        var stated = item("用户的车牌是 A7-3921", Assertion.USER_STATED, 0.5);
        var confirmed = item("用户的工号是 W-5518", Assertion.USER_CONFIRMED, 0.5);
        var forgotten = item("用户可能有两个孩子", Assertion.INFERRED, 0.5);
        itemService.forget(ALICE, List.of(forgotten.id()), ALICE.subject(), "FORGET_IN_CHAT").block(WAIT);
        age(stale, 400, 181);
        age(staleNever, 200, null);
        age(recent, 400, 179);
        age(stated, 900, 800);
        age(confirmed, 900, null);
        age(forgotten, 900, 800);

        assertThat(decay.inactivateStale().block(WAIT)).isEqualTo(2);

        assertThat(row(stale)).containsEntry("STATUS", "INACTIVE");
        assertThat(((Number) row(stale).get("IS_VALID")).intValue()).as("kept, not deleted").isEqualTo(1);
        assertThat(row(staleNever)).containsEntry("STATUS", "INACTIVE");
        assertThat(row(recent)).containsEntry("STATUS", "ACTIVE");
        assertThat(row(stated)).as("user-stated items never go inactive (D12 revised)").containsEntry("STATUS", "ACTIVE");
        assertThat(row(confirmed)).containsEntry("STATUS", "ACTIVE");
        assertThat(row(forgotten)).containsEntry("STATUS", "FORGOTTEN");
        assertThat(stack.rows("SELECT target_id FROM memory_audit_log WHERE action = 'DECAY_INACTIVATE'"
                + " ORDER BY target_id").stream().map(r -> (String) r.get("TARGET_ID")).toList())
                .containsExactlyInAnyOrder(stale.id(), staleNever.id());
        assertThat(stack.meters().find("memory.decay.inactivated").counter().count()).isEqualTo(2.0);

        assertThat(decay.inactivateStale().block(WAIT)).as("idempotent").isZero();
    }

    @Test
    void inactiveItemNotRecalledButVisibleInApi() {
        var stale = item("用户可能住在杭州", Assertion.INFERRED, 0.5);
        age(stale, 400, 200);
        assertThat(ids(itemHits("杭州"))).containsExactly(stale.id());

        decay.inactivateStale().block(WAIT);

        assertThat(itemHits("杭州")).isEmpty();
        var listed = itemService.list(ALICE, null, 50, null).block(WAIT).items();
        assertThat(listed).anySatisfy(i -> {
            assertThat(i.id()).isEqualTo(stale.id());
            assertThat(i.status()).isEqualTo(ItemStatus.INACTIVE);
        });
    }

    @Test
    void thresholdIsConfigurable() {
        var stale = item("用户可能住在杭州", Assertion.INFERRED, 0.5);
        age(stale, 30, 20);
        assertThat(decay.inactivateStale().block(WAIT)).isZero();

        stack.props().getDecay().setInactiveAfter(Duration.ofDays(10));
        assertThat(decay.inactivateStale().block(WAIT)).isEqualTo(1);
    }
}
