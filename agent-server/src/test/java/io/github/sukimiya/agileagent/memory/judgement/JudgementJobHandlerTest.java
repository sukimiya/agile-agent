package io.github.sukimiya.agileagent.memory.judgement;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Function;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import io.github.sukimiya.agileagent.memory.MemoryMetrics;
import io.github.sukimiya.agileagent.memory.MemoryProperties;
import io.github.sukimiya.agileagent.memory.MemoryScope;
import io.github.sukimiya.agileagent.memory.MemoryTestFixture;
import io.github.sukimiya.agileagent.memory.MemoryTestStack;
import io.github.sukimiya.agileagent.memory.SourceType;
import io.github.sukimiya.agileagent.memory.items.ItemStatus;
import io.github.sukimiya.agileagent.memory.items.MemoryItem;
import io.github.sukimiya.agileagent.memory.items.MemoryItemRepository;
import io.github.sukimiya.agileagent.memory.items.MemoryItemSourceRepository;
import io.github.sukimiya.agileagent.memory.jobs.ConsolidationQuota;
import io.github.sukimiya.agileagent.memory.jobs.JobType;
import io.github.sukimiya.agileagent.memory.jobs.MemoryJobQueue;
import io.github.sukimiya.agileagent.memory.jobs.MemoryJobWorker;
import io.github.sukimiya.agileagent.memory.recall.MemorySettingsService;
import io.github.sukimiya.agileagent.service.LlmClient;
import io.github.sukimiya.agileagent.util.NonBlockingIds;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

class JudgementJobHandlerTest {

    static final Duration WAIT = Duration.ofSeconds(15);
    static final MemoryScope ALICE = MemoryTestStack.ALICE;
    static final ObjectMapper JSON = new ObjectMapper();
    static final String EXISTING_MARKER = "已有条目（可能为空）：\n";

    MemoryTestFixture fixture;
    MemoryProperties props;
    MemoryMetrics metrics;
    MemoryItemRepository items;
    MemoryItemSourceRepository sources;
    JudgementProgress progress;
    JudgementValidator validator;
    MemorySettingsService settings;
    MemoryJobQueue queue;
    MemoryJobWorker worker;
    JudgementJobHandler handler;
    ConsolidationQuota quota;
    final List<String> llmOutputs = new CopyOnWriteArrayList<>();
    /** The existing-items JSON of every judgement call, in call order. */
    final List<String> existingSeen = new CopyOnWriteArrayList<>();
    Function<List<LlmClient.Message>, Flux<LlmClient.LlmEvent>> answer;
    String conversation;
    String userMsgId;

    protected MemoryTestFixture newFixture() {
        return MemoryTestFixture.h2();
    }

    @BeforeEach
    void setUp() throws Exception {
        fixture = newFixture();
        props = new MemoryProperties();
        props.getJudgement().setEnabled(true);
        props.getConsolidation().setDailyTokenLimit(1_000_000);
        metrics = new MemoryMetrics(new SimpleMeterRegistry());
        items = new MemoryItemRepository(fixture.databaseClient());
        sources = new MemoryItemSourceRepository(fixture.databaseClient());
        progress = new JudgementProgress(fixture.databaseClient());
        validator = new JudgementValidator(fixture.databaseClient(), items);
        settings = new MemorySettingsService(fixture.databaseClient());
        quota = new ConsolidationQuota(fixture.databaseClient(), props, metrics);
        queue = new MemoryJobQueue(fixture.databaseClient());
        worker = new MemoryJobWorker(fixture.databaseClient(), () -> false, quota, props, metrics);
        answer = messages -> Flux.just(new LlmClient.LlmEvent.Completed(llmOutputs.isEmpty() ? "[]" : llmOutputs.get(0)),
                new LlmClient.LlmEvent.Usage(10, 20, 30));
        LlmClient llm = new LlmClient() {
            @Override public Flux<LlmEvent> chatStream(List<Message> messages, List<ToolDefinition> tools) {
                return chatStream(messages, tools, new CallOptions(null));
            }
            @Override public Flux<LlmEvent> chatStream(List<Message> messages, List<ToolDefinition> tools,
                    CallOptions options) {
                var system = String.valueOf(messages.getFirst().content());
                int at = system.indexOf(EXISTING_MARKER);
                existingSeen.add(at < 0 ? "" : system.substring(at + EXISTING_MARKER.length()));
                return Flux.defer(() -> answer.apply(messages));
            }
        };
        this.llm = llm;
        handler = newHandler();
        conversation = seed();
    }

    LlmClient llm;

    JudgementJobHandler newHandler() {
        return new JudgementJobHandler(fixture.databaseClient(), fixture.connectionFactory(), progress, validator,
                items, sources, GlobalRules.loadFromClasspath(), settings,
                tenant -> Mono.just(new JudgementJobHandler.JudgeModel(llm, "fake-model")),
                worker, props, metrics, quota);
    }

    private String seed() throws Exception {
        var c = NonBlockingIds.uuid();
        fixture.jdbc().update("INSERT INTO sessions (id, tenant_id, user_id, title, revision, created_at, updated_at)"
                + " VALUES (?,?,?,'t',1,CURRENT_TIMESTAMP(6),CURRENT_TIMESTAMP(6))",
                c, ALICE.tenantId(), ALICE.subject());
        fixture.jdbc().update("INSERT INTO chat_turns (id, tenant_id, subject, conversation_id, request_hash, status, fence,"
                + " created_at, updated_at, finished_at) VALUES ('turn-j',?,?,?,'hash','COMPLETED',1,"
                + " TIMESTAMPADD(MINUTE,-10,CURRENT_TIMESTAMP(6)), CURRENT_TIMESTAMP(6),"
                + " TIMESTAMPADD(MINUTE,-10,CURRENT_TIMESTAMP(6)))",
                ALICE.tenantId(), ALICE.subject(), c);
        userMsgId = "msg-j-user";
        var payload = JSON.writeValueAsString(LlmClient.Message.user("我的工号是 A7-3921，项目代号 W-5518。"));
        fixture.jdbc().update("INSERT INTO chat_messages (id, tenant_id, subject, conversation_id, turn_id, sequence,"
                + " role, source_type, payload, created_at, updated_at) VALUES (?,?,?,?, 'turn-j', 1, 'user', ?, ?,"
                + " CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6))",
                userMsgId, ALICE.tenantId(), ALICE.subject(), c, SourceType.USER_INPUT.name(), payload);
        return c;
    }

    private String actionJson(String action, String assertion, String content) throws Exception {
        ArrayNode arr = JSON.createArrayNode();
        ObjectNode o = arr.addObject();
        o.put("action", action);
        o.put("itemType", "FACT");
        o.put("content", content);
        o.put("assertion", assertion);
        o.putArray("sourceMessageIds").add(userMsgId);
        o.putArray("evidenceExcerpts").add("A7-3921");
        o.put("reason", "test");
        o.put("importance", 0.8);
        o.put("confidence", 0.9);
        return JSON.writeValueAsString(arr);
    }

    @Test
    void judgementRecordsRulesVersionModelReasonEvidence() throws Exception {
        llmOutputs.add(actionJson("REMEMBER", "USER_STATED", "用户工号是 A7-3921"));
        queue.enqueue(ALICE, conversation, JobType.JUDGE_MEMORY,
                JudgementJobHandler.dedupeKey(conversation, 1), JudgementJobHandler.payload(1)).block(WAIT);
        assertThat(worker.runPending(5).block(WAIT)).isEqualTo(1);
        var row = fixture.jdbc().queryForMap(
                "SELECT rules_version, judge_model, reason, content, status, assertion, conversation_id"
                        + " FROM memory_items WHERE tenant_id = ?", ALICE.tenantId());
        assertThat(row.get("rules_version")).isEqualTo(1);
        assertThat(row.get("judge_model")).isEqualTo("fake-model");
        assertThat(row.get("reason")).isEqualTo("test");
        assertThat(row.get("content").toString()).contains("A7-3921");
        assertThat(row.get("status")).isEqualTo("ACTIVE");
        // cross-conversation off → session scoped
        assertThat(row.get("conversation_id")).isEqualTo(conversation);
        var src = fixture.jdbc().queryForMap("SELECT evidence_excerpt FROM memory_item_sources WHERE message_id = ?",
                userMsgId);
        assertThat(src.get("evidence_excerpt").toString()).contains("A7-3921");
        assertThat(progress.judgedThrough(ALICE, conversation).block(WAIT)).isEqualTo(1L);
    }

    @Test
    void crossConversationOffWritesNoUserLevelItems() throws Exception {
        llmOutputs.add(actionJson("REMEMBER", "USER_STATED", "用户工号是 A7-3921"));
        handler.judge(ALICE, conversation, 1).block(WAIT);
        Long userLevel = fixture.jdbc().queryForObject(
                "SELECT COUNT(*) FROM memory_items WHERE conversation_id IS NULL AND tenant_id = ?",
                Long.class, ALICE.tenantId());
        assertThat(userLevel).isZero();
    }

    @Test
    void correctionSupersedesOldItem() throws Exception {
        var old = MemoryItem.newItem(ALICE, conversation, "FACT", "旧工号 X", ItemStatus.ACTIVE,
                MemoryItem.Assertion.USER_STATED, 0.5, 0.9, 1, "m", "old");
        items.save(old).block(WAIT);
        sources.saveAll(old.id(), List.of(new ValidatedAction.SourceRef(userMsgId, conversation,
                SourceType.USER_INPUT, "A7-3921"))).block(WAIT);
        ArrayNode arr = JSON.createArrayNode();
        ObjectNode o = arr.addObject();
        o.put("action", "SUPERSEDE");
        o.put("itemType", "FACT");
        o.put("content", "用户工号更正为 A7-3921");
        o.put("assertion", "USER_CONFIRMED");
        o.putArray("sourceMessageIds").add(userMsgId);
        o.putArray("evidenceExcerpts").add("A7-3921");
        o.put("reason", "corr-02");
        o.put("importance", 0.9);
        o.put("confidence", 1.0);
        o.put("targetItemId", old.id());
        o.put("expectedRevision", 1);
        llmOutputs.add(JSON.writeValueAsString(arr));
        handler.judge(ALICE, conversation, 1).block(WAIT);
        var oldRow = fixture.jdbc().queryForMap("SELECT status, superseded_by FROM memory_items WHERE id = ?",
                old.id());
        assertThat(oldRow.get("status")).isEqualTo("SUPERSEDED");
        assertThat(oldRow.get("superseded_by")).isNotNull();
        var neu = fixture.jdbc().queryForMap("SELECT assertion, content FROM memory_items WHERE id = ?",
                oldRow.get("superseded_by"));
        assertThat(neu.get("assertion")).isEqualTo("USER_CONFIRMED");
        assertThat(neu.get("content").toString()).contains("A7-3921");
    }

    @Test
    void unresolvableConflictKeepsConflictState() throws Exception {
        var old = MemoryItem.newItem(ALICE, conversation, "FACT", "工号 X", ItemStatus.ACTIVE,
                MemoryItem.Assertion.USER_STATED, 0.5, 0.9, 1, "m", "old");
        items.save(old).block(WAIT);
        ArrayNode arr = JSON.createArrayNode();
        ObjectNode o = arr.addObject();
        o.put("action", "CONFLICT");
        o.put("itemType", "FACT");
        o.put("content", "冲突");
        o.put("assertion", "INFERRED");
        o.putArray("sourceMessageIds").add(userMsgId);
        o.putArray("evidenceExcerpts").add("A7-3921");
        o.put("reason", "unresolvable");
        o.put("targetItemId", old.id());
        o.put("expectedRevision", 1);
        llmOutputs.add(JSON.writeValueAsString(arr));
        handler.judge(ALICE, conversation, 1).block(WAIT);
        assertThat(fixture.jdbc().queryForObject("SELECT status FROM memory_items WHERE id = ?", String.class,
                old.id())).isEqualTo("CONFLICT");
    }

    @Test
    void malformedJsonRejectedWholly() {
        llmOutputs.add("{not-json");
        assertThat(queue.enqueue(ALICE, conversation, JobType.JUDGE_MEMORY,
                JudgementJobHandler.dedupeKey(conversation, 1), JudgementJobHandler.payload(1)).block(WAIT))
                .isTrue();
        assertThat(worker.runPending(5).block(WAIT)).isZero(); // failed → retry, not DONE
        assertThat(fixture.jdbc().queryForObject("SELECT status FROM memory_jobs WHERE dedupe_key like 'judge:%'",
                String.class)).isEqualTo("PENDING");
        assertThat(fixture.jdbc().queryForObject("SELECT COUNT(*) FROM memory_items", Long.class)).isZero();
    }


    @Test
    void deletingConversationRecomputesMultiSourceItem() throws Exception {
        settings.update(ALICE, true).block(WAIT);
        var c2 = NonBlockingIds.uuid();
        fixture.jdbc().update("INSERT INTO sessions (id, tenant_id, user_id, title, revision, created_at, updated_at)"
                + " VALUES (?,?,?,'t',1,CURRENT_TIMESTAMP(6),CURRENT_TIMESTAMP(6))",
                c2, ALICE.tenantId(), ALICE.subject());
        var item = MemoryItem.newItem(ALICE, null, "FACT", "跨会话事实", ItemStatus.ACTIVE,
                MemoryItem.Assertion.USER_STATED, 0.5, 0.9, 1, "m", "r");
        items.save(item).block(WAIT);
        sources.saveAll(item.id(), List.of(
                new ValidatedAction.SourceRef(userMsgId, conversation, SourceType.USER_INPUT, "A7-3921"),
                new ValidatedAction.SourceRef("msg-other", c2, SourceType.USER_INPUT, "W-5518"))).block(WAIT);

        try (var stack = MemoryTestStack.of(fixture)) {
            var uploads = new io.github.sukimiya.agileagent.config.UploadProperties();
            uploads.setDir(java.nio.file.Files.createTempDirectory("mem-up").toString());
            var maintenance = new io.github.sukimiya.agileagent.memory.MemoryMaintenance(
                    fixture.connectionFactory(), fixture.databaseClient(), stack.cache(),
                    new io.github.sukimiya.agileagent.service.FileStorageService(uploads), props, metrics);
            maintenance.deleteConversation(ALICE, conversation).block(WAIT);
            // One source remains from c2 → still ACTIVE
            assertThat(fixture.jdbc().queryForObject("SELECT status FROM memory_items WHERE id = ?", String.class,
                    item.id())).isEqualTo("ACTIVE");
            assertThat(fixture.jdbc().queryForObject(
                    "SELECT COUNT(*) FROM memory_item_sources WHERE item_id = ? AND is_valid = 1", Long.class, item.id()))
                    .isEqualTo(1L);
        }
    }

    @Test
    void noRemainingSourceDeactivatesItem() throws Exception {
        var item = MemoryItem.newItem(ALICE, conversation, "FACT", "仅本会话", ItemStatus.ACTIVE,
                MemoryItem.Assertion.USER_STATED, 0.5, 0.9, 1, "m", "r");
        items.save(item).block(WAIT);
        sources.saveAll(item.id(), List.of(new ValidatedAction.SourceRef(userMsgId, conversation,
                SourceType.USER_INPUT, "A7-3921"))).block(WAIT);
        try (var stack = MemoryTestStack.of(fixture)) {
            var uploads = new io.github.sukimiya.agileagent.config.UploadProperties();
            uploads.setDir(java.nio.file.Files.createTempDirectory("mem-up2").toString());
            var maintenance = new io.github.sukimiya.agileagent.memory.MemoryMaintenance(
                    fixture.connectionFactory(), fixture.databaseClient(), stack.cache(),
                    new io.github.sukimiya.agileagent.service.FileStorageService(uploads), props, metrics);
            maintenance.deleteConversation(ALICE, conversation).block(WAIT);
            assertThat(fixture.jdbc().queryForObject("SELECT status FROM memory_items WHERE id = ?", String.class,
                    item.id())).isEqualTo("INACTIVE");
            assertThat(fixture.jdbc().queryForObject(
                    "SELECT COUNT(*) FROM memory_item_sources WHERE item_id = ? AND is_valid = 1", Long.class, item.id()))
                    .isZero();
        }
    }

    @Test
    void memoryCommandTurnsAreNotJudged() throws Exception {
        var calls = new java.util.concurrent.atomic.AtomicInteger();
        var original = answer;
        answer = messages -> {
            calls.incrementAndGet();
            return original.apply(messages);
        };
        llmOutputs.add(actionJson("REMEMBER", "USER_STATED", "用户工号是 A7-3921"));
        fixture.jdbc().update("UPDATE chat_turns SET memory_command = 'FORGET' WHERE id = 'turn-j'");
        queue.enqueue(ALICE, conversation, JobType.JUDGE_MEMORY,
                JudgementJobHandler.dedupeKey(conversation, 1), JudgementJobHandler.payload(1)).block(WAIT);

        assertThat(worker.runPending(5).block(WAIT)).isEqualTo(1);

        assertThat(calls).hasValue(0);
        assertThat(fixture.jdbc().queryForObject("SELECT COUNT(*) FROM memory_items", Long.class)).isZero();
        assertThat(progress.judgedThrough(ALICE, conversation).block(WAIT)).isEqualTo(1L);
    }

    // --- M2b Task 6: re-confirmation refreshes the recall time ------------------------------------------------

    private MemoryItem agedItem(String content, MemoryItem.Assertion assertion) {
        var item = MemoryItem.newItem(ALICE, conversation, "FACT", content, ItemStatus.ACTIVE, assertion, 0.5, 0.9, 1,
                "m", "old");
        items.save(item).block(WAIT);
        sources.saveAll(item.id(), List.of(new ValidatedAction.SourceRef(userMsgId, conversation,
                SourceType.USER_INPUT, "A7-3921"))).block(WAIT);
        fixture.jdbc().update("UPDATE memory_items SET last_recalled_at = TIMESTAMPADD(DAY, -100, CURRENT_TIMESTAMP(6)),"
                + " created_at = TIMESTAMPADD(DAY, -200, CURRENT_TIMESTAMP(6)) WHERE id = ?", item.id());
        return item;
    }

    private long recalledDaysAgo(MemoryItem item) {
        return fixture.jdbc().queryForObject("SELECT TIMESTAMPDIFF(DAY, last_recalled_at, CURRENT_TIMESTAMP(6))"
                + " FROM memory_items WHERE id = ?", Long.class, item.id());
    }

    private String targeted(String action, String assertion, String content, MemoryItem target) throws Exception {
        ArrayNode arr = (ArrayNode) JSON.readTree(actionJson(action, assertion, content));
        ((ObjectNode) arr.get(0)).put("targetItemId", target.id());
        ((ObjectNode) arr.get(0)).put("expectedRevision", target.revision());
        return JSON.writeValueAsString(arr);
    }

    @Test
    void userReconfirmationRefreshesTheRecallTime() throws Exception {
        var item = agedItem("用户工号是 A7-3921", MemoryItem.Assertion.USER_STATED);
        llmOutputs.add(targeted("UPDATE", "USER_STATED", "用户工号是 A7-3921（再次确认）", item));

        handler.judge(ALICE, conversation, 1).block(WAIT);

        assertThat(recalledDaysAgo(item)).isZero();
    }

    @Test
    void rememberOfAnExistingItemRefreshesItsRecallTime() throws Exception {
        var item = agedItem("用户工号是 A7-3921", MemoryItem.Assertion.USER_STATED);
        llmOutputs.add(targeted("REMEMBER", "USER_STATED", "用户工号是 A7-3921", item));

        handler.judge(ALICE, conversation, 1).block(WAIT);

        assertThat(recalledDaysAgo(item)).isZero();
        assertThat(itemCount()).as("REMEMBER of an existing item creates no duplicate").isEqualTo(1);
    }

    private long itemCount() {
        return fixture.jdbc().queryForObject("SELECT COUNT(*) FROM memory_items WHERE tenant_id = ?", Long.class,
                ALICE.tenantId());
    }

    @Test
    void inferredRememberOfAnExistingItemNeitherDuplicatesNorRefreshes() throws Exception {
        var item = agedItem("用户可能在 A7-3921 部门", MemoryItem.Assertion.INFERRED);
        llmOutputs.add(targeted("REMEMBER", "INFERRED", "用户可能在 A7-3921 部门", item));

        handler.judge(ALICE, conversation, 1).block(WAIT);

        assertThat(itemCount()).isEqualTo(1);
        assertThat(recalledDaysAgo(item)).isEqualTo(100);
    }

    @Test
    void inferredUpdatesDoNotRefreshTheRecallTime() throws Exception {
        var item = agedItem("用户可能在 A7-3921 部门", MemoryItem.Assertion.INFERRED);
        llmOutputs.add(targeted("UPDATE", "INFERRED", "用户可能在 A7-3921 部门工作", item));

        handler.judge(ALICE, conversation, 1).block(WAIT);

        assertThat(fixture.jdbc().queryForObject("SELECT content FROM memory_items WHERE id = ?", String.class,
                item.id())).contains("工作");
        assertThat(recalledDaysAgo(item)).isEqualTo(100);
    }

    // --- 2026-10-30: existing items passed to the judgement ---------------------------------------------------

    /** Saves an item of {@code conversationId} (null = user level) created {@code minutesAgo}, sourced from msgId. */
    private MemoryItem existing(String conversationId, String content, ItemStatus status, double importance,
            int minutesAgo, String sourceConversation, String sourceMessage) {
        var item = MemoryItem.newItem(ALICE, conversationId, "FACT", content, status, MemoryItem.Assertion.USER_STATED,
                importance, 0.9, 1, "m", "seed");
        items.save(item).block(WAIT);
        if (sourceMessage != null) {
            sources.saveAll(item.id(), List.of(new ValidatedAction.SourceRef(sourceMessage, sourceConversation,
                    SourceType.USER_INPUT, "x"))).block(WAIT);
        }
        fixture.jdbc().update("UPDATE memory_items SET created_at = TIMESTAMPADD(MINUTE, ?, CURRENT_TIMESTAMP(6)),"
                + " updated_at = TIMESTAMPADD(MINUTE, ?, CURRENT_TIMESTAMP(6)) WHERE id = ?",
                -minutesAgo, -minutesAgo, item.id());
        return item;
    }

    private MemoryItem existing(String content, ItemStatus status, double importance, int minutesAgo) {
        return existing(conversation, content, status, importance, minutesAgo, conversation, userMsgId);
    }

    private List<String> seenIds(int call) throws Exception {
        var ids = new java.util.ArrayList<String>();
        JSON.readTree(existingSeen.get(call)).forEach(n -> ids.add(n.path("id").asText()));
        return ids;
    }

    private String otherConversation() throws Exception {
        var c = NonBlockingIds.uuid();
        fixture.jdbc().update("INSERT INTO sessions (id, tenant_id, user_id, title, revision, created_at, updated_at)"
                + " VALUES (?,?,?,'o',1,CURRENT_TIMESTAMP(6),CURRENT_TIMESTAMP(6))", c, ALICE.tenantId(),
                ALICE.subject());
        fixture.jdbc().update("INSERT INTO chat_turns (id, tenant_id, subject, conversation_id, request_hash, status,"
                + " fence, created_at, updated_at, finished_at) VALUES (?,?,?,?,'h','COMPLETED',1,CURRENT_TIMESTAMP(6),"
                + " CURRENT_TIMESTAMP(6),CURRENT_TIMESTAMP(6))", "turn-" + c, ALICE.tenantId(), ALICE.subject(), c);
        fixture.jdbc().update("INSERT INTO chat_messages (id, tenant_id, subject, conversation_id, turn_id, sequence,"
                + " role, source_type, payload, created_at, updated_at) VALUES (?,?,?,?,?,1,'user','USER_INPUT',?,"
                + " CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6))", "msg-" + c, ALICE.tenantId(), ALICE.subject(), c,
                "turn-" + c, JSON.writeValueAsString(LlmClient.Message.user("别的会话")));
        return c;
    }

    @Test
    void existingItemsAreRecentPlusMatchedInScopeWithTheReplayFields() throws Exception {
        var recent = existing("用户最喜欢绿色", ItemStatus.ACTIVE, 0.5, 1);
        var candidate = existing("用户可能爱喝茶", ItemStatus.CANDIDATE, 0.5, 2);
        var conflict = existing("用户的车是蓝色还是白色", ItemStatus.CONFLICT, 0.5, 3);
        var hidden = List.of(existing("已遗忘 A7-3921", ItemStatus.FORGOTTEN, 0.9, 4),
                existing("已停用 A7-3921", ItemStatus.INACTIVE, 0.9, 5),
                existing("已替代 A7-3921", ItemStatus.SUPERSEDED, 0.9, 6));
        var deleted = existing("已删除 A7-3921", ItemStatus.ACTIVE, 0.9, 7);
        fixture.jdbc().update("UPDATE memory_items SET status = 'DELETED', is_valid = 0 WHERE id = ?", deleted.id());
        var other = otherConversation();
        var foreign = existing(other, "别的会话里的 A7-3921", ItemStatus.ACTIVE, 0.9, 1, other, "msg-" + other);
        var userLevel = existing(null, "用户级的 A7-3921", ItemStatus.ACTIVE, 0.9, 1, conversation, userMsgId);

        handler.judge(ALICE, conversation, 1).block(WAIT);

        assertThat(seenIds(0)).containsExactlyInAnyOrder(recent.id(), candidate.id(), conflict.id());
        assertThat(seenIds(0)).doesNotContain(hidden.get(0).id(), deleted.id(), foreign.id(), userLevel.id());
        var first = JSON.readTree(existingSeen.get(0)).get(0);
        var fields = new java.util.ArrayList<String>();
        first.fieldNames().forEachRemaining(fields::add);
        assertThat(fields).containsExactlyInAnyOrder("id", "revision", "itemType", "content", "assertion", "status");
    }

    @Test
    void recentItemsNeedNoLiteralMatchAndBothLimitsComeFromConfiguration() throws Exception {
        props.getJudgement().setExistingMaxItems(3);
        props.getJudgement().setExistingRecentItems(2);
        var newest = existing("用户最喜欢绿色", ItemStatus.ACTIVE, 0.1, 1);
        var second = existing("用户周末爬山", ItemStatus.ACTIVE, 0.1, 2);
        var third = existing("用户养了一只猫", ItemStatus.ACTIVE, 0.1, 3);
        var matchedHigh = existing("用户工号是 A7-3921", ItemStatus.ACTIVE, 0.9, 30);
        var matchedLow = existing("项目 W-5518 已结项", ItemStatus.ACTIVE, 0.2, 40);

        handler.judge(ALICE, conversation, 1).block(WAIT);

        assertThat(seenIds(0)).containsExactlyInAnyOrder(newest.id(), second.id(), matchedHigh.id())
                .doesNotContain(third.id(), matchedLow.id()).doesNotHaveDuplicates();
        assertThat(seenIds(0).getFirst()).as("ranked by decayed score").isEqualTo(matchedHigh.id());
    }

    @Test
    void aRecentItemThatAlsoMatchesIsListedOnce() throws Exception {
        var both = existing("用户工号是 A7-3921", ItemStatus.ACTIVE, 0.5, 1);

        handler.judge(ALICE, conversation, 1).block(WAIT);

        assertThat(seenIds(0)).containsExactly(both.id());
    }

    @Test
    void overTheTokenCapTheLowestRankedItemsAreDroppedFirst() throws Exception {
        var high = existing("用户工号是 A7-3921，这一条的重要性最高，内容稍长一些以便估算", ItemStatus.ACTIVE, 0.9, 1);
        var low = existing("项目 W-5518 已结项，这一条的重要性最低，内容稍长一些以便估算", ItemStatus.ACTIVE, 0.1, 2);
        handler.judge(ALICE, conversation, 1).block(WAIT);
        assertThat(seenIds(0)).containsExactly(high.id(), low.id());
        long both = io.github.sukimiya.agileagent.memory.context.TokenEstimator.estimate(existingSeen.get(0));

        fixture.jdbc().update("UPDATE memory_index_progress SET judged_through_sequence = 0");
        props.getJudgement().setExistingMaxTokens((int) both - 1);
        handler.judge(ALICE, conversation, 1).block(WAIT);
        assertThat(seenIds(1)).containsExactly(high.id());

        fixture.jdbc().update("UPDATE memory_index_progress SET judged_through_sequence = 0");
        props.getJudgement().setExistingMaxTokens(0);
        handler.judge(ALICE, conversation, 1).block(WAIT);
        assertThat(existingSeen.get(2)).isEqualTo("[]");
    }

    @Test
    void zeroMaxItemsSendsNoExistingItems() throws Exception {
        props.getJudgement().setExistingMaxItems(0);
        props.getJudgement().setExistingRecentItems(0);
        existing("用户工号是 A7-3921", ItemStatus.ACTIVE, 0.9, 1);

        handler.judge(ALICE, conversation, 1).block(WAIT);

        assertThat(existingSeen.get(0)).isEqualTo("[]");
    }

    @Test
    void existingItemsAreFetchedAgainBeforeEachTurnSoACorrectionSupersedes() throws Exception {
        fixture.jdbc().update("INSERT INTO chat_turns (id, tenant_id, subject, conversation_id, request_hash, status,"
                + " fence, created_at, updated_at, finished_at) VALUES ('turn-k',?,?,?,'hash2','COMPLETED',1,"
                + " TIMESTAMPADD(MINUTE,-9,CURRENT_TIMESTAMP(6)), CURRENT_TIMESTAMP(6),"
                + " TIMESTAMPADD(MINUTE,-9,CURRENT_TIMESTAMP(6)))", ALICE.tenantId(), ALICE.subject(), conversation);
        fixture.jdbc().update("INSERT INTO chat_messages (id, tenant_id, subject, conversation_id, turn_id, sequence,"
                + " role, source_type, payload, created_at, updated_at) VALUES ('msg-k-user',?,?,?, 'turn-k', 2,"
                + " 'user', 'USER_INPUT', ?, CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6))", ALICE.tenantId(),
                ALICE.subject(), conversation, JSON.writeValueAsString(LlmClient.Message.user("不对，换成 B8-1000。")));
        var calls = new java.util.concurrent.atomic.AtomicInteger();
        answer = messages -> {
            try {
                String out;
                if (calls.getAndIncrement() == 0) {
                    out = actionJson("REMEMBER", "USER_STATED", "用户工号是 A7-3921");
                } else {
                    var seen = JSON.readTree(existingSeen.get(1)).get(0);
                    ArrayNode arr = JSON.createArrayNode();
                    ObjectNode o = arr.addObject();
                    o.put("action", "SUPERSEDE");
                    o.put("itemType", "FACT");
                    o.put("content", "用户工号是 B8-1000");
                    o.put("assertion", "USER_STATED");
                    o.putArray("sourceMessageIds").add("msg-k-user");
                    o.putArray("evidenceExcerpts").add("B8-1000");
                    o.put("reason", "更正");
                    o.put("targetItemId", seen.path("id").asText());
                    o.put("expectedRevision", seen.path("revision").asLong());
                    out = JSON.writeValueAsString(arr);
                }
                return Flux.just(new LlmClient.LlmEvent.Completed(out), new LlmClient.LlmEvent.Usage(10, 20, 30));
            } catch (Exception e) {
                return Flux.error(e);
            }
        };

        handler.judge(ALICE, conversation, 2).block(WAIT);

        assertThat(existingSeen.get(0)).isEqualTo("[]");
        assertThat(existingSeen.get(1)).contains("A7-3921");
        var rows = fixture.jdbc().queryForList("SELECT content, status FROM memory_items WHERE tenant_id = ?"
                + " ORDER BY created_at", ALICE.tenantId());
        assertThat(rows).extracting(r -> r.get("status") + ":" + r.get("content"))
                .containsExactlyInAnyOrder("SUPERSEDED:用户工号是 A7-3921", "ACTIVE:用户工号是 B8-1000");
    }

    @Test
    void crossOnUsesUserLevelItemsAndThisConversationsSourcesForRecency() throws Exception {
        settings.update(ALICE, true).block(WAIT);
        var other = otherConversation();
        var mine = existing(null, "用户最喜欢绿色", ItemStatus.ACTIVE, 0.5, 1, conversation, userMsgId);
        var elsewhere = existing(null, "用户周末爬山", ItemStatus.ACTIVE, 0.5, 1, other, "msg-" + other);
        var matchedElsewhere = existing(null, "用户工号是 A7-3921", ItemStatus.ACTIVE, 0.5, 50, other, "msg-" + other);
        var session = existing("会话级的 A7-3921", ItemStatus.ACTIVE, 0.9, 1);

        handler.judge(ALICE, conversation, 1).block(WAIT);

        assertThat(seenIds(0)).containsExactlyInAnyOrder(mine.id(), matchedElsewhere.id())
                .doesNotContain(elsewhere.id(), session.id());
    }

    @Test
    void existingItemLimitsAcceptOnlyNonNegativeValuesAndRecentAtMostTotal() {
        var j = new MemoryProperties().getJudgement();
        assertThat(j.getExistingMaxItems()).isEqualTo(10);
        assertThat(j.getExistingRecentItems()).isEqualTo(5);
        assertThat(j.getExistingMaxTokens()).isEqualTo(1500);
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> j.setExistingMaxItems(-1))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("existing-max-items");
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> j.setExistingRecentItems(-1))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("existing-recent-items");
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> j.setExistingMaxTokens(-1))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("existing-max-tokens");
        props.getJudgement().setExistingMaxItems(3);
        props.getJudgement().setExistingRecentItems(4);
        org.assertj.core.api.Assertions.assertThatThrownBy(this::newHandler)
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("existing-recent-items");
    }

    @Test
    void applicationYmlDeclaresTheExistingItemLimitsAndTheyBind() throws Exception {
        var sources = new org.springframework.boot.env.YamlPropertySourceLoader().load("app",
                new org.springframework.core.io.ClassPathResource("application.yml"));
        var values = new java.util.LinkedHashMap<String, String>();
        for (var source : sources) {
            if (source.getSource() instanceof java.util.Map<?, ?> map) {
                map.forEach((k, v) -> {
                    if (k.toString().startsWith("app.memory.judgement.")) {
                        values.put(k.toString(), String.valueOf(v));
                    }
                });
            }
        }
        assertThat(values).containsEntry("app.memory.judgement.existing-max-items", "10")
                .containsEntry("app.memory.judgement.existing-recent-items", "5")
                .containsEntry("app.memory.judgement.existing-max-tokens", "1500");
        values.put("app.memory.judgement.existing-max-items", "7");
        var bound = new MemoryProperties();
        new org.springframework.boot.context.properties.bind.Binder(
                new org.springframework.boot.context.properties.source.MapConfigurationPropertySource(values))
                .bind("app.memory", org.springframework.boot.context.properties.bind.Bindable.ofInstance(bound));
        assertThat(bound.getJudgement().getExistingMaxItems()).isEqualTo(7);
        assertThat(bound.getJudgement().getExistingRecentItems()).isEqualTo(5);
        assertThat(bound.getJudgement().getExistingMaxTokens()).isEqualTo(1500);
    }
}
