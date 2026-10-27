package io.github.sukimiya.agileagent.memory.command;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Files;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.github.sukimiya.agileagent.agent.DomainAgent;
import io.github.sukimiya.agileagent.agent.InProcessTaskDispatcher;
import io.github.sukimiya.agileagent.agent.Orchestrator;
import io.github.sukimiya.agileagent.capability.CapabilityRegistry;
import io.github.sukimiya.agileagent.config.UploadProperties;
import io.github.sukimiya.agileagent.memory.MemoryException;
import io.github.sukimiya.agileagent.memory.MemoryException.ErrorCode;
import io.github.sukimiya.agileagent.memory.MemoryMaintenance;
import io.github.sukimiya.agileagent.memory.MemoryScope;
import io.github.sukimiya.agileagent.memory.MemoryTestFixture;
import io.github.sukimiya.agileagent.memory.MemoryTestStack;
import io.github.sukimiya.agileagent.memory.SourceType;
import io.github.sukimiya.agileagent.memory.context.ContextAssembler;
import io.github.sukimiya.agileagent.memory.context.ContextBudget;
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
import io.github.sukimiya.agileagent.memory.retention.MemoryIndexPurger;
import io.github.sukimiya.agileagent.memory.summary.SummaryRepository;
import io.github.sukimiya.agileagent.service.FileStorageService;
import io.github.sukimiya.agileagent.service.LlmClient;
import io.github.sukimiya.agileagent.service.TenantLlmClientResolver.ResolvedModel;
import io.github.sukimiya.agileagent.spi.capability.ToolDefinition;
import io.github.sukimiya.agileagent.util.NonBlockingIds;
import reactor.core.publisher.Flux;

/**
 * In-chat forget / restore (M2b T5 follow-up, user decision 2026-10-27): only the user's own current input
 * triggers it; matching follows the recall scope; one match is forgotten at once, several are listed for a
 * pick by number or "全部" (short-lived, any other reply cancels), none changes nothing. FORGOTTEN items stay
 * listed, leave recall and the profile, are never purged, and are audited; restore brings them back.
 * H2 here; {@link MemoryForgetMySqlIntegrationTest} on MySQL.
 */
class MemoryForgetTest {

    static final Duration WAIT = Duration.ofSeconds(15);
    static final MemoryScope ALICE = MemoryTestStack.ALICE;
    static final MemoryScope BOB = new MemoryScope(ALICE.tenantId(), "bob");
    static final String CLINIC = "用户下周三要去口腔医院复诊";

    MemoryTestStack stack;
    MemoryItemRepository items;
    MemoryItemSourceRepository sources;
    EntityGraphRepository graph;
    MemoryItemService itemService;
    ProfileService profiles;
    MemorySettingsService settings;
    MemoryCommandService commands;
    io.github.sukimiya.agileagent.memory.jobs.ConsolidationQuota quota;
    MemoryRetriever retriever;
    MemoryMaintenance maintenance;
    MemoryIndexPurger purger;
    Orchestrator orchestrator;
    MemoryJobWorker worker;
    SummaryRepository summaries;
    String conversation;
    final List<List<LlmClient.Message>> modelCalls = new CopyOnWriteArrayList<>();
    final List<String> classifierInputs = new CopyOnWriteArrayList<>();
    final List<LlmClient.CallOptions> classifierOptions = new CopyOnWriteArrayList<>();
    final java.util.Set<String> failingInputs = java.util.concurrent.ConcurrentHashMap.newKeySet();
    final java.util.Map<String, String> classifications = new java.util.concurrent.ConcurrentHashMap<>(java.util.Map.of(
            "忘记我下周三去口腔医院复诊", "{\"command\":\"FORGET\",\"topic\":\"下周三去口腔医院复诊\"}",
            "记起来我下周三去口腔医院复诊", "{\"command\":\"RESTORE\",\"topic\":\"下周三去口腔医院复诊\"}",
            "那个口腔医院复诊的事你就别记了", "{\"command\":\"FORGET\",\"topic\":\"口腔医院复诊\"}",
            "把口腔医院复诊那条记忆删掉", "{\"command\":\"FORGET\",\"topic\":\"口腔医院复诊\"}"));

    /** Model calls other than the intent classification (routing and answering). */
    List<List<LlmClient.Message>> answerCalls() {
        return modelCalls.stream().filter(c -> !c.getFirst().content().contains("记忆指令分类器")).toList();
    }
    final LlmClient llm = new LlmClient() {
        @Override
        public Flux<LlmEvent> chatStream(List<Message> messages, List<ToolDefinition> tools) {
            return chatStream(messages, tools, new CallOptions(null));
        }

        @Override
        public Flux<LlmEvent> chatStream(List<Message> messages, List<ToolDefinition> tools, CallOptions options) {
            return Flux.defer(() -> {
                modelCalls.add(List.copyOf(messages));
                if (messages.getFirst().content().contains("记忆指令分类器")) {
                    String input;
                    try {
                        input = new com.fasterxml.jackson.databind.ObjectMapper().readTree(messages.get(1).content())
                                .path("input").asText();
                    } catch (Exception e) {
                        return Flux.error(e);
                    }
                    classifierInputs.add(input);
                    classifierOptions.add(options);
                    if (failingInputs.contains(input)) {
                        return Flux.error(new IllegalStateException("model down"));
                    }
                    var out = classifications.getOrDefault(input, "{\"command\":\"NONE\",\"topic\":\"\"}");
                    return Flux.just(new LlmEvent.Completed(out), new LlmEvent.Usage(5, 3, 8));
                }
                if (messages.getFirst().content().contains("路由模块")) {
                    return Flux.just(new LlmEvent.Completed("[]"));
                }
                return Flux.just(new LlmEvent.ContentDelta("模型回答"), new LlmEvent.Completed("模型回答"));
            });
        }
    };

    protected MemoryTestFixture newFixture() {
        return MemoryTestFixture.h2();
    }

    @BeforeEach
    void setUp() throws Exception {
        stack = MemoryTestStack.of(newFixture());
        conversation = MemoryTestStack.newConversationId();
        var db = stack.fixture().databaseClient();
        worker = new MemoryJobWorker(db, () -> false);
        var chunks = new ChunkRepository(db, stack.conversations());
        new ChunkIndexer(db, stack.fixture().connectionFactory(), chunks, worker, stack.props());
        items = new MemoryItemRepository(db);
        sources = new MemoryItemSourceRepository(db);
        graph = new EntityGraphRepository(db);
        var cache = new ProfileCache(null, stack.props());
        settings = new MemorySettingsService(db, cache);
        profiles = new ProfileService(db, items, settings, cache, stack.props());
        itemService = new MemoryItemService(db, stack.fixture().connectionFactory(), items, sources, graph, profiles,
                stack.props());
        summaries = new SummaryRepository(db, stack.conversations());
        retriever = new MemoryRetriever(db, stack.props(), settings, chunks, summaries, FullTextSearch.forDatabase(db), stack.metrics(), items, graph, profiles);
        quota = new io.github.sukimiya.agileagent.memory.jobs.ConsolidationQuota(db, stack.props(), stack.metrics());
        commands = new MemoryCommandService(db, itemService, settings, stack.props(), stack.metrics(), quota);
        var budget = new ContextBudget(stack.props());
        var registry = new CapabilityRegistry(List.of());
        orchestrator = new Orchestrator(new InProcessTaskDispatcher(new DomainAgent(registry, stack.toolRecorder(),
                budget)), registry, stack.lifecycle(), stack.history(),
                new ContextAssembler(stack.history(), budget, stack.metrics(), retriever), commands);
        var uploads = new UploadProperties();
        uploads.setDir(Files.createTempDirectory("forget").toString());
        maintenance = new MemoryMaintenance(stack.fixture().connectionFactory(), db, stack.cache(),
                new FileStorageService(uploads), stack.props(), stack.metrics());
        purger = new MemoryIndexPurger(db, stack.props(), stack.metrics());
    }

    @AfterEach
    void tearDown() {
        stack.close();
    }

    // --- helpers -------------------------------------------------------------------------------------------

    MemoryItem item(String conversationId, String content) {
        var item = MemoryItem.newItem(ALICE, conversationId, "FACT", content, ItemStatus.ACTIVE,
                MemoryItem.Assertion.USER_STATED, 0.5, 0.9, 1, "m", "r");
        items.save(item).block(WAIT);
        sources.saveAll(item.id(), List.of(new SourceRef(NonBlockingIds.uuid(),
                conversationId == null ? conversation : conversationId, SourceType.USER_INPUT, content))).block(WAIT);
        return item;
    }

    /** One chat turn through the orchestrator; returns the stored final answer. */
    String say(String text) {
        var accepted = stack.accept(ALICE, conversation, NonBlockingIds.uuid(), text);
        orchestrator.chat(accepted, "tok", null, new ResolvedModel(llm, 32_768, 4_096)).collectList().block(WAIT);
        assertThat(stack.status(accepted.ticket().turnId())).isEqualTo("COMPLETED");
        lastTurn = accepted.ticket().turnId();
        return stack.assistantText(lastTurn);
    }

    String lastTurn;

    String status(MemoryItem item) {
        return (String) stack.rows("SELECT status FROM memory_items WHERE id = ?", item.id()).getFirst().get("STATUS");
    }

    long count(String sql, Object... args) {
        return ((Number) stack.rows(sql, args).getFirst().values().iterator().next()).longValue();
    }

    List<String> audit(String action) {
        return stack.rows("SELECT target_id FROM memory_audit_log WHERE action = ? ORDER BY created_at, target_id",
                action).stream().map(r -> (String) r.get("TARGET_ID")).toList();
    }

    // --- forget --------------------------------------------------------------------------------------------

    @Test
    void singleMatchIsForgottenAtOnceWithoutConfirmationOrModelCall() {
        var clinic = item(conversation, CLINIC);
        var other = item(conversation, "用户下周三要开会");

        var reply = say("忘记我下周三去口腔医院复诊");

        assertThat(reply).contains("已忘记").contains(CLINIC);
        assertThat(answerCalls()).as("a handled command calls no routing or answer model").isEmpty();
        assertThat(classifierInputs).containsExactly("忘记我下周三去口腔医院复诊");
        assertThat(status(clinic)).isEqualTo("FORGOTTEN");
        assertThat(status(other)).isEqualTo("ACTIVE");
        assertThat(audit("FORGET_IN_CHAT")).containsExactly(clinic.id());
        assertThat(stack.rows("SELECT actor FROM memory_audit_log WHERE action = 'FORGET_IN_CHAT'").getFirst())
                .containsEntry("ACTOR", ALICE.subject());
        assertThat(count("SELECT COUNT(*) FROM chat_turns WHERE id = ? AND memory_command = 'FORGET'", lastTurn))
                .as("the command turn is marked so judgement skips it").isEqualTo(1);
        // Still listed (not a delete), excluded from item recall.
        assertThat(itemService.list(ALICE, null, 20, null).block(WAIT).items()).extracting(MemoryItem::id)
                .contains(clinic.id());
        assertThat(itemService.list(ALICE, ItemStatus.FORGOTTEN, 20, null).block(WAIT).items())
                .extracting(MemoryItem::id).containsExactly(clinic.id());
        assertThat(items.searchActive(ALICE, conversation, List.of("口腔"), 10).collectList().block(WAIT)).isEmpty();
    }

    @Test
    void severalMatchesAreListedThenPickedByNumber() {
        var first = item(conversation, CLINIC);
        var second = item(conversation, "用户每周三去口腔医院复诊牙套");

        var list = say("忘记我下周三去口腔医院复诊");
        assertThat(list).contains("1.").contains("2.").contains("全部").contains(CLINIC)
                .contains("用户每周三去口腔医院复诊牙套");
        assertThat(status(first)).isEqualTo("ACTIVE");
        assertThat(status(second)).isEqualTo("ACTIVE");
        assertThat(count("SELECT COUNT(*) FROM memory_command_pending WHERE conversation_id = ?", conversation))
                .isEqualTo(1);

        var order = stack.rows("SELECT candidate_ids FROM memory_command_pending").getFirst().get("CANDIDATE_IDS")
                .toString();
        var pickedFirst = order.indexOf(first.id()) < order.indexOf(second.id()) ? second : first;
        var reply = say("2");

        assertThat(reply).contains("已忘记").contains(pickedFirst.content());
        assertThat(status(pickedFirst)).isEqualTo("FORGOTTEN");
        assertThat(status(pickedFirst == first ? second : first)).isEqualTo("ACTIVE");
        assertThat(answerCalls()).isEmpty();
        assertThat(count("SELECT COUNT(*) FROM memory_command_pending")).as("pick consumes the token").isZero();
    }

    @Test
    void allPicksEveryListedCandidate() {
        var first = item(conversation, CLINIC);
        var second = item(conversation, "用户每周三去口腔医院复诊牙套");
        say("忘记我下周三去口腔医院复诊");

        say("全部");

        assertThat(status(first)).isEqualTo("FORGOTTEN");
        assertThat(status(second)).isEqualTo("FORGOTTEN");
        assertThat(audit("FORGET_IN_CHAT")).containsExactlyInAnyOrder(first.id(), second.id());
    }

    @Test
    void atMostFiveCandidatesAreListed() {
        for (int i = 0; i < 7; i++) {
            item(conversation, "用户下周三去口腔医院复诊第" + i + "次");
        }
        var list = say("忘记我下周三去口腔医院复诊");
        assertThat(list).contains("5.").doesNotContain("6.");
    }

    @Test
    void unrelatedReplyCancelsThePickAndIsAnsweredNormally() {
        var first = item(conversation, CLINIC);
        var second = item(conversation, "用户每周三去口腔医院复诊牙套");
        say("忘记我下周三去口腔医院复诊");

        var reply = say("今天天气怎么样");

        assertThat(reply).isEqualTo(MemoryCommandService.CANCELLED + "模型回答");
        assertThat(answerCalls()).isNotEmpty();
        assertThat(status(first)).isEqualTo("ACTIVE");
        assertThat(status(second)).isEqualTo("ACTIVE");
        assertThat(count("SELECT COUNT(*) FROM memory_command_pending")).isZero();
        // A later number is an ordinary message again.
        modelCalls.clear();
        assertThat(say("1")).as("no pick open any more: an ordinary message").isEqualTo("模型回答");
        assertThat(status(first)).isEqualTo("ACTIVE");
    }

    @Test
    void expiredPickIsCancelled() {
        var first = item(conversation, CLINIC);
        item(conversation, "用户每周三去口腔医院复诊牙套");
        say("忘记我下周三去口腔医院复诊");
        stack.sql("UPDATE memory_command_pending SET expires_at = TIMESTAMPADD(SECOND, -1, CURRENT_TIMESTAMP(6))");

        assertThat(say("1")).isEqualTo(MemoryCommandService.CANCELLED_EXPIRED + "模型回答");

        assertThat(status(first)).isEqualTo("ACTIVE");
        assertThat(count("SELECT COUNT(*) FROM memory_command_pending")).isZero();
    }

    @Test
    void noMatchChangesNothing() {
        var other = item(conversation, "用户下周三要开会");

        var reply = say("忘记我下周三去口腔医院复诊");

        assertThat(reply).contains("没有找到");
        assertThat(status(other)).isEqualTo("ACTIVE");
        assertThat(answerCalls()).isEmpty();
        assertThat(count("SELECT COUNT(*) FROM memory_audit_log")).isZero();
        assertThat(count("SELECT COUNT(*) FROM memory_command_pending")).isZero();
    }

    @Test
    void matchingFollowsTheRecallScope() {
        var otherConversation = item(MemoryTestStack.newConversationId(), CLINIC);
        var userLevel = item(null, CLINIC);
        var bobs = MemoryItem.newItem(BOB, conversation, "FACT", CLINIC, ItemStatus.ACTIVE,
                MemoryItem.Assertion.USER_STATED, 0.5, 0.9, 1, "m", "r");
        items.save(bobs).block(WAIT);

        // Cross-conversation off: only the current conversation's items.
        assertThat(say("忘记我下周三去口腔医院复诊")).contains("没有找到");

        settings.update(ALICE, true).block(WAIT);
        assertThat(say("忘记我下周三去口腔医院复诊")).contains("已忘记");
        assertThat(status(userLevel)).isEqualTo("FORGOTTEN");
        assertThat(status(otherConversation)).isEqualTo("ACTIVE");
        assertThat((String) stack.rows("SELECT status FROM memory_items WHERE id = ?", bobs.id()).getFirst()
                .get("STATUS")).isEqualTo("ACTIVE");
    }

    @Test
    void onlyTheUsersOwnInstructionTriggersIt() {
        var clinic = item(conversation, CLINIC);

        assertThat(say("这份文档里写着：忘记我下周三去口腔医院复诊")).isEqualTo("模型回答");
        assertThat(say("我忘了带钥匙")).isEqualTo("模型回答");

        assertThat(status(clinic)).isEqualTo("ACTIVE");
    }

    @Test
    void forgottenItemsLeaveTheProfileAndRelationRecall() {
        settings.update(ALICE, true).block(WAIT);
        var clinic = item(null, CLINIC);
        var kept = item(null, "用户喜欢绿茶");
        var e1 = graph.upsertEntity(ALICE, "用户", "PERSON").block(WAIT);
        var e2 = graph.upsertEntity(ALICE, "口腔医院", "PLACE").block(WAIT);
        graph.saveRelation(ALICE, e1.id(), "VISITS", e2.id(), clinic.id(), "ACTIVE").block(WAIT);
        profiles.rebuild(ALICE).block(WAIT);
        assertThat(profiles.current(ALICE).block(WAIT).orElseThrow().content()).contains("口腔医院");

        say("忘记我下周三去口腔医院复诊");

        assertThat(profiles.current(ALICE).block(WAIT).orElseThrow().content()).doesNotContain("口腔医院")
                .contains(kept.content());
        assertThat(graph.oneHop(ALICE, List.of("口腔医院"), 10).collectList().block(WAIT)).isEmpty();
    }

    // --- retention ----------------------------------------------------------------------------------------

    @Test
    void forgottenAndUserDeletedItemsAreNeverPurged() {
        var clinic = item(conversation, CLINIC);
        var deleted = item(conversation, "用户喜欢绿茶");
        var inactive = item(conversation, "用户喜欢中文");
        say("忘记我下周三去口腔医院复诊");
        itemService.delete(ALICE, deleted.id()).block(WAIT);

        maintenance.deleteConversation(ALICE, conversation).block(WAIT);
        maintenance.runPending(10).block(WAIT);
        stack.sql("UPDATE memory_items SET source_deleted_at = TIMESTAMPADD(DAY, -400, CURRENT_TIMESTAMP(6))"
                + " WHERE source_deleted_at IS NOT NULL");
        purger.purgeExpired().block(WAIT);

        assertThat(count("SELECT COUNT(*) FROM memory_items WHERE id = ?", clinic.id())).isEqualTo(1);
        assertThat(status(clinic)).isEqualTo("FORGOTTEN");
        assertThat(count("SELECT COUNT(*) FROM memory_items WHERE id = ?", deleted.id())).isEqualTo(1);
        assertThat(count("SELECT COUNT(*) FROM memory_items WHERE id = ?", inactive.id()))
                .as("an ordinary source-deleted item is still purged").isZero();
        assertThat(itemService.recallCount(ALICE, clinic.id()).block(WAIT).purgeEligibleAt()).isNull();
    }

    // --- restore ------------------------------------------------------------------------------------------

    @Test
    void restoreApiBringsAForgottenItemBack() {
        var clinic = item(conversation, CLINIC);
        say("忘记我下周三去口腔医院复诊");

        var restored = itemService.restore(ALICE, clinic.id(), ALICE.subject()).block(WAIT);

        assertThat(restored.status()).isEqualTo(ItemStatus.ACTIVE);
        assertThat(status(clinic)).isEqualTo("ACTIVE");
        assertThat(audit("RESTORE")).containsExactly(clinic.id());
        assertThat(items.searchActive(ALICE, conversation, List.of("口腔"), 10).collectList().block(WAIT))
                .extracting(MemoryItem::id).containsExactly(clinic.id());
        assertThatThrownBy(() -> itemService.restore(ALICE, clinic.id(), ALICE.subject()).block(WAIT))
                .isInstanceOfSatisfying(MemoryException.class,
                        e -> assertThat(e.code()).isEqualTo(ErrorCode.MEMORY_ITEM_CONFLICT));
        assertThatThrownBy(() -> itemService.restore(BOB, clinic.id(), BOB.subject()).block(WAIT))
                .isInstanceOfSatisfying(MemoryException.class,
                        e -> assertThat(e.code()).isEqualTo(ErrorCode.MEMORY_ITEM_NOT_FOUND));
        assertThatThrownBy(() -> itemService.restore(ALICE, "missing", ALICE.subject()).block(WAIT))
                .isInstanceOfSatisfying(MemoryException.class,
                        e -> assertThat(e.code()).isEqualTo(ErrorCode.MEMORY_ITEM_NOT_FOUND));
    }

    @Test
    void restoringAnItemWhoseSourceWasDeletedLeavesItInactive() {
        var clinic = item(conversation, CLINIC);
        say("忘记我下周三去口腔医院复诊");
        stack.sql("UPDATE memory_items SET source_deleted_at = CURRENT_TIMESTAMP(6) WHERE id = ?", clinic.id());

        assertThat(itemService.restore(ALICE, clinic.id(), ALICE.subject()).block(WAIT).status())
                .isEqualTo(ItemStatus.INACTIVE);
    }

    @Test
    void inChatRestoreMatchesOnlyForgottenItems() {
        var clinic = item(conversation, CLINIC);
        var active = item(conversation, "用户每周三去口腔医院复诊牙套");
        stack.sql("UPDATE memory_items SET status = 'FORGOTTEN' WHERE id = ?", clinic.id());

        var reply = say("记起来我下周三去口腔医院复诊");

        assertThat(reply).contains("已记起").contains(CLINIC).doesNotContain(active.content());
        assertThat(status(clinic)).isEqualTo("ACTIVE");
        assertThat(audit("RESTORE_IN_CHAT")).containsExactly(clinic.id());
        assertThat(count("SELECT COUNT(*) FROM chat_turns WHERE id = ? AND memory_command = 'RESTORE'", lastTurn))
                .isEqualTo(1);
        assertThat(say("记起来我下周三去口腔医院复诊")).as("nothing forgotten any more").contains("没有找到");
    }

    @Test
    void inChatRestoreWithSeveralMatchesAsksForAPick() {
        var first = item(conversation, CLINIC);
        var second = item(conversation, "用户每周三去口腔医院复诊牙套");
        stack.sql("UPDATE memory_items SET status = 'FORGOTTEN'");

        assertThat(say("记起来我下周三去口腔医院复诊")).contains("1.").contains("2.");
        say("全部");

        assertThat(status(first)).isEqualTo("ACTIVE");
        assertThat(status(second)).isEqualTo("ACTIVE");
    }

    // --- raw text recall after forget -------------------------------------------------------------------------

    String completedTurn(String question, String answer) {
        var ticket = stack.accept(ALICE, conversation, NonBlockingIds.uuid(), question).ticket();
        assertThat(stack.finalizer().finish(ticket, io.github.sukimiya.agileagent.memory.MemoryModel.TurnStatus.COMPLETED,
                answer).block(WAIT)).isTrue();
        return ticket.turnId();
    }

    /** An item whose provenance is the user message of {@code turnId}. */
    MemoryItem itemFrom(String turnId, String content) {
        var messageId = (String) stack.rows("SELECT id FROM chat_messages WHERE turn_id = ? AND role = 'user'", turnId)
                .getFirst().get("ID");
        var item = MemoryItem.newItem(ALICE, conversation, "FACT", content, ItemStatus.ACTIVE,
                MemoryItem.Assertion.USER_STATED, 0.5, 0.9, 1, "m", "r");
        items.save(item).block(WAIT);
        sources.saveAll(item.id(), List.of(new SourceRef(messageId, conversation, SourceType.USER_INPUT, content)))
                .block(WAIT);
        return item;
    }

    void record(String turnId, String summary) {
        var range = stack.rows("SELECT MIN(sequence) AS f, MAX(sequence) AS t, MIN(id) AS fid, MAX(id) AS tid"
                + " FROM chat_messages WHERE turn_id = ?", turnId).getFirst();
        long position = summaries.summarizedThrough(ALICE, conversation).block(WAIT);
        var tx = org.springframework.transaction.reactive.TransactionalOperator.create(
                new org.springframework.r2dbc.connection.R2dbcTransactionManager(stack.fixture().connectionFactory()));
        assertThat(tx.transactional(summaries.save(ALICE, conversation,
                new io.github.sukimiya.agileagent.memory.summary.MemoryRecord("r-" + turnId, turnId,
                        ((Number) range.get("F")).longValue(), ((Number) range.get("T")).longValue(),
                        (String) range.get("FID"), (String) range.get("TID"), summary, List.of(), List.of(), false,
                        List.of(summary), io.github.sukimiya.agileagent.memory.summary.MemoryRecord.MemoryType.FACT,
                        "m", "memory-record-v1", 1), position)).block(WAIT)).isTrue();
    }

    List<String> recalled(String query) {
        var ticket = stack.accept(ALICE, conversation, NonBlockingIds.uuid(), query).ticket();
        try {
            return retriever.recall(ticket, query).block(WAIT).items().stream()
                    .map(io.github.sukimiya.agileagent.memory.recall.RecallCandidate::turnId).toList();
        } finally {
            stack.finalizer().finish(ticket, io.github.sukimiya.agileagent.memory.MemoryModel.TurnStatus.FAILED, "")
                    .block(WAIT);
        }
    }

    @Test
    void forgetExcludesTheCommandTurnsAndTheItemsSourceTurnsFromRecall() {
        var source = completedTurn("我下周三去口腔医院复诊，挂号单 K-7788", "好的，记住了");
        var unrelated = completedTurn("我的车牌是 A7-3921", "好的");
        var clinic = itemFrom(source, CLINIC);
        worker.runPending(100).block(WAIT);
        record(source, "用户下周三去口腔医院复诊，挂号单 K-7788");
        assertThat(recalled("K-7788 口腔医院复诊")).contains(source);

        say("忘记我下周三去口腔医院复诊");
        var command = lastTurn;
        worker.runPending(100).block(WAIT); // the command turn is chunk-indexed like any other turn

        for (var query : List.of("K-7788", "口腔医院复诊是哪天", "下周三去口腔医院复诊")) {
            assertThat(recalled(query)).as(query).doesNotContain(source, command);
        }
        assertThat(recalled("A7-3921 违章")).as("other turns stay recallable").contains(unrelated);

        itemService.restore(ALICE, clinic.id(), ALICE.subject()).block(WAIT);
        assertThat(recalled("K-7788 口腔医院复诊")).as("restore brings the source turn back").contains(source)
                .doesNotContain(command);
    }

    @Test
    void pickAndRestoreCommandTurnsStayExcluded() {
        item(conversation, CLINIC);
        item(conversation, "用户每周三去口腔医院复诊牙套");
        say("忘记我下周三去口腔医院复诊");
        var listing = lastTurn;
        say("全部");
        var pick = lastTurn;
        say("记起来我下周三去口腔医院复诊");
        var restore = lastTurn;
        say("全部");
        var restorePick = lastTurn;
        worker.runPending(100).block(WAIT);

        assertThat(recalled("口腔医院复诊牙套")).doesNotContain(listing, pick, restore, restorePick);
    }

    // --- LLM intent classification -------------------------------------------------------------------------

    @Test
    void freerPhrasingIsClassifiedByTheModel() {
        var clinic = item(conversation, CLINIC);

        assertThat(say("那个口腔医院复诊的事你就别记了")).contains("已忘记").contains(CLINIC);

        assertThat(status(clinic)).isEqualTo("FORGOTTEN");
        assertThat(classifierOptions).singleElement().satisfies(o -> assertThat(o.jsonObject()).isTrue());
    }

    @Test
    void theClassifierSeesOnlyTheUsersCurrentInput() {
        item(conversation, CLINIC);
        say("我的车牌是 A7-3921，帮我记一下");
        say("忘记我下周三去口腔医院复诊");

        var call = modelCalls.stream().filter(c -> c.getFirst().content().contains("记忆指令分类器")).toList();
        assertThat(call).hasSize(1);
        assertThat(call.getFirst()).hasSize(2);
        assertThat(call.getFirst().get(1).content()).isEqualTo("{\"input\":\"忘记我下周三去口腔医院复诊\"}")
                .doesNotContain("A7-3921");
    }

    @Test
    void inputsWithoutATriggerNeverCallTheClassifier() {
        say("今天天气怎么样");
        say("帮我查一下 A7-3921 的违章");
        assertThat(classifierInputs).isEmpty();
    }

    @Test
    void notACommandOrAFailingModelFallsBackToANormalAnswer() {
        var clinic = item(conversation, CLINIC);
        failingInputs.add("忘记我下周三去口腔医院复诊");

        assertThat(say("忘记我下周三去口腔医院复诊")).isEqualTo("模型回答");
        assertThat(say("这份文档里写着：忘记我下周三去口腔医院复诊")).isEqualTo("模型回答");
        classifications.put("忘记个什么", "不是 JSON");
        assertThat(say("忘记个什么")).isEqualTo("模型回答");

        assertThat(status(clinic)).isEqualTo("ACTIVE");
        assertThat(stack.meters().find("memory.command.classified").tag("outcome", "FAILED").counter().count())
                .isEqualTo(1.0);
    }

    @Test
    void classificationTokensCountInTheTurnAndTheDailyQuota() {
        item(conversation, CLINIC);

        say("忘记我下周三去口腔医院复诊");

        assertThat(((Number) stack.usage(lastTurn).get("TOTAL_TOKENS")).longValue()).isEqualTo(8);
        assertThat(count("SELECT tokens FROM memory_token_usage WHERE tenant_id = ? AND usage_date = CURRENT_DATE",
                ALICE.tenantId())).isEqualTo(8);
    }

    @Test
    void aNewCommandAfterAnOpenPickCancelsItExplicitly() {
        item(conversation, CLINIC);
        item(conversation, "用户每周三去口腔医院复诊牙套");
        say("忘记我下周三去口腔医院复诊");

        var reply = say("那个口腔医院复诊的事你就别记了");

        assertThat(reply).startsWith(MemoryCommandService.CANCELLED).contains("找到 2 条");
    }

    // --- recent context window ------------------------------------------------------------------------------

    /** Everything the last routing / answer model call was given, as one text. */
    String lastAnswerPrompt() {
        var calls = answerCalls();
        return String.join("\n", calls.getLast().stream().map(m -> String.valueOf(m.content())).toList());
    }

    @Test
    void forgetAlsoRemovesTheRawTextFromTheRecentContextWindow() {
        var source = completedTurn("我下周三去口腔医院复诊，挂号单 K-7788", "好的，K-7788 记住了");
        completedTurn("我的车牌是 A7-3921", "好的");
        var clinic = itemFrom(source, CLINIC);

        say("忘记我下周三去口腔医院复诊");
        modelCalls.clear();
        say("今天天气怎么样");

        assertThat(lastAnswerPrompt()).as("the forgotten source turn and the command turn are not sent")
                .doesNotContain("K-7788").doesNotContain("忘记我下周三").doesNotContain("已忘记")
                .contains("A7-3921");

        itemService.restore(ALICE, clinic.id(), ALICE.subject()).block(WAIT);
        modelCalls.clear();
        say("明天呢");

        assertThat(lastAnswerPrompt()).as("restore brings the source turn back, not the command turn")
                .contains("K-7788").doesNotContain("忘记我下周三").doesNotContain("已忘记");
    }

    @Test
    void historyApiStillListsForgottenAndCommandTurns() {
        var source = completedTurn("我下周三去口腔医院复诊，挂号单 K-7788", "好的");
        itemFrom(source, CLINIC);
        say("忘记我下周三去口腔医院复诊");

        var page = stack.history().page(ALICE, conversation, null, 50).block(WAIT);
        assertThat(page.toString()).contains("K-7788").contains("忘记我下周三");
    }

    @Test
    void deleteWordsAlsoReachTheClassifier() {
        var clinic = item(conversation, CLINIC);

        assertThat(say("把口腔医院复诊那条记忆删掉")).contains("已忘记");

        assertThat(status(clinic)).isEqualTo("FORGOTTEN");
        assertThat(classifierInputs).containsExactly("把口腔医院复诊那条记忆删掉");
    }
}
