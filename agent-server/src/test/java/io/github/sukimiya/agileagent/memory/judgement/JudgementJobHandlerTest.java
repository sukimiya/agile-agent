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
                return Flux.defer(() -> answer.apply(messages));
            }
        };
        handler = new JudgementJobHandler(fixture.databaseClient(), fixture.connectionFactory(), progress, validator,
                items, sources, GlobalRules.loadFromClasspath(), settings,
                tenant -> Mono.just(new JudgementJobHandler.JudgeModel(llm, "fake-model")),
                worker, props, metrics, quota);
        conversation = seed();
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
}
