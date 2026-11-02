package io.github.sukimiya.agileagent.memory.judgement;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.regex.Pattern;

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
import io.github.sukimiya.agileagent.memory.items.MemoryItem.Assertion;
import io.github.sukimiya.agileagent.memory.items.MemoryItemRepository;
import io.github.sukimiya.agileagent.memory.items.MemoryItemSourceRepository;
import io.github.sukimiya.agileagent.memory.jobs.ConsolidationQuota;
import io.github.sukimiya.agileagent.memory.jobs.MemoryJobWorker;
import io.github.sukimiya.agileagent.memory.recall.MemorySettingsService;
import io.github.sukimiya.agileagent.memory.retention.RecallReferences;
import io.github.sukimiya.agileagent.service.LlmClient;
import io.github.sukimiya.agileagent.util.NonBlockingIds;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * M2b Task 7 (D13=A, user decisions 2026-10-28): Rules replay is explicit, per item, only for INFERRED items of
 * older Rules versions that the user never touched, in live conversations; keep/rewrite supersedes with a new
 * current-version item that inherits the recall time and count, anything else makes the old item INACTIVE; the
 * old-to-new mapping is recorded; the daily quota postpones it; an interrupted replay resumes. H2 here;
 * {@link RulesReplayMySqlIntegrationTest} on MySQL.
 */
class RulesReplayTest {

    static final Duration WAIT = Duration.ofSeconds(15);
    static final MemoryScope ALICE = MemoryTestStack.ALICE;
    static final ObjectMapper JSON = new ObjectMapper();
    static final Pattern TARGET = Pattern.compile("\"id\"\\s*:\\s*\"([^\"]+)\"");
    static final GlobalRules NEW_RULES = new GlobalRules(
            new GlobalRules.RulesVersion(2, "rules-version: 2\n新规则：谨慎推断。", "sha-2"));

    MemoryTestFixture fixture;
    MemoryProperties props;
    MemoryMetrics metrics;
    MemoryItemRepository items;
    MemoryItemSourceRepository sources;
    JudgementValidator validator;
    MemorySettingsService settings;
    ConsolidationQuota quota;
    MemoryJobWorker worker;
    RulesReplayHandler replay;
    /** Model output per targeted item id; missing: "[]". */
    final Map<String, String> outputs = new ConcurrentHashMap<>();
    final Set<String> failing = ConcurrentHashMap.newKeySet();
    final List<String> calledFor = new CopyOnWriteArrayList<>();
    final List<String> systemPrompts = new CopyOnWriteArrayList<>();
    String conversation;
    String userMsgId;

    protected MemoryTestFixture newFixture() {
        return MemoryTestFixture.h2();
    }

    @BeforeEach
    void setUp() throws Exception {
        fixture = newFixture();
        props = new MemoryProperties();
        props.getConsolidation().setDailyTokenLimit(1_000_000);
        props.getConsolidation().setReserveTokens(100);
        metrics = new MemoryMetrics(new SimpleMeterRegistry());
        var db = fixture.databaseClient();
        items = new MemoryItemRepository(db);
        sources = new MemoryItemSourceRepository(db);
        validator = new JudgementValidator(db, items);
        settings = new MemorySettingsService(db);
        quota = new ConsolidationQuota(db, props, metrics);
        worker = new MemoryJobWorker(db, () -> false, quota, props, metrics);
        LlmClient llm = new LlmClient() {
            @Override public Flux<LlmEvent> chatStream(List<Message> messages, List<ToolDefinition> tools) {
                return chatStream(messages, tools, new CallOptions(null));
            }
            @Override public Flux<LlmEvent> chatStream(List<Message> messages, List<ToolDefinition> tools,
                    CallOptions options) {
                return Flux.defer(() -> {
                    var system = messages.getFirst().content();
                    systemPrompts.add(system);
                    var m = TARGET.matcher(system.substring(system.indexOf("已有条目")));
                    var id = m.find() ? m.group(1) : "?";
                    calledFor.add(id);
                    if (failing.remove(id)) {
                        return Flux.error(new IllegalStateException("model down"));
                    }
                    return Flux.just(new LlmEvent.Completed(outputs.getOrDefault(id, "[]")),
                            new LlmEvent.Usage(10, 20, 30));
                });
            }
        };
        replay = new RulesReplayHandler(db, fixture.connectionFactory(), validator, items, NEW_RULES,
                tenant -> Mono.just(new JudgementJobHandler.JudgeModel(llm, "fake-model")), worker, props, metrics,
                quota);
        conversation = seedConversation(true);
        userMsgId = "msg-" + conversation;
    }

    /** A session (live or deleted) with one completed turn whose user message is {@code msg-<conversation>}. */
    String seedConversation(boolean live) throws Exception {
        var c = NonBlockingIds.uuid();
        fixture.jdbc().update("INSERT INTO sessions (id, tenant_id, user_id, title, revision, created_at, updated_at,"
                + " is_valid) VALUES (?,?,?,'t',1,CURRENT_TIMESTAMP(6),CURRENT_TIMESTAMP(6),?)",
                c, ALICE.tenantId(), ALICE.subject(), live ? 1 : 0);
        var turn = "turn-" + c;
        fixture.jdbc().update("INSERT INTO chat_turns (id, tenant_id, subject, conversation_id, request_hash, status,"
                + " fence, created_at, updated_at, finished_at) VALUES (?,?,?,?,'hash','COMPLETED',1,"
                + " CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6))",
                turn, ALICE.tenantId(), ALICE.subject(), c);
        var payload = JSON.writeValueAsString(LlmClient.Message.user("我的工号是 A7-3921，我大概住在杭州。"));
        fixture.jdbc().update("INSERT INTO chat_messages (id, tenant_id, subject, conversation_id, turn_id, sequence,"
                + " role, source_type, payload, created_at, updated_at) VALUES (?,?,?,?,?, 1, 'user', ?, ?,"
                + " CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6))",
                "msg-" + c, ALICE.tenantId(), ALICE.subject(), c, turn, SourceType.USER_INPUT.name(), payload);
        return c;
    }

    MemoryItem oldItem(String conversationId, String content, Assertion assertion, ItemStatus status, int rules) {
        var item = MemoryItem.newItem(ALICE, conversationId, "FACT", content, status, assertion, 0.5, 0.6, rules,
                "old-model", "old");
        items.save(item).block(WAIT);
        sources.saveAll(item.id(), List.of(new ValidatedAction.SourceRef("msg-" + conversationId, conversationId,
                SourceType.USER_INPUT, "杭州"))).block(WAIT);
        return item;
    }

    MemoryItem oldInferred(String content) {
        return oldItem(conversation, content, Assertion.INFERRED, ItemStatus.ACTIVE, 1);
    }

    String action(String kind, MemoryItem target, String content) throws Exception {
        ArrayNode arr = JSON.createArrayNode();
        ObjectNode o = arr.addObject();
        o.put("action", kind);
        o.put("itemType", "FACT");
        o.put("content", content);
        o.put("assertion", "INFERRED");
        o.putArray("sourceMessageIds").add(userMsgId);
        o.putArray("evidenceExcerpts").add("杭州");
        o.put("reason", "replay");
        o.put("importance", 0.6);
        o.put("confidence", 0.8);
        if (target != null) {
            o.put("targetItemId", target.id());
            o.put("expectedRevision", target.revision());
        }
        return JSON.writeValueAsString(arr);
    }

    Map<String, Object> row(String id) {
        return fixture.jdbc().queryForMap("SELECT status, superseded_by, rules_version, content, last_recalled_at,"
                + " inherited_recall_count, revision FROM memory_items WHERE id = ?", id);
    }

    List<Map<String, Object>> mapping(String replayId) {
        return fixture.jdbc().queryForList("SELECT item_id, new_item_id, conversation_id, outcome FROM"
                + " memory_replay_items WHERE replay_id = ? ORDER BY created_at, item_id", replayId);
    }

    RulesReplayHandler.Replay status(String replayId) {
        return replay.find(replayId).block(WAIT).orElseThrow();
    }

    String request(Integer from) {
        return replay.request(ALICE.tenantId(), from, "platform-admin").block(WAIT).id();
    }

    void recalledInTurns(MemoryItem item, String... turns) {
        for (var t : turns) {
            fixture.jdbc().update("INSERT INTO memory_recall_events (id, tenant_id, subject, conversation_id, turn_id,"
                    + " kind, ref_id, created_at) VALUES (?,?,?,?,?,'ITEM',?,CURRENT_TIMESTAMP(6))",
                    NonBlockingIds.uuid(), ALICE.tenantId(), ALICE.subject(), conversation, t, item.id());
        }
    }

    // --- tests -----------------------------------------------------------------------------------------------

    @Test
    void rulesChangeDoesNotSilentlyRewriteHistory() throws Exception {
        var old = oldInferred("用户可能住在杭州");
        props.getJudgement().setEnabled(true);
        var judge = new JudgementJobHandler(fixture.databaseClient(), fixture.connectionFactory(),
                new JudgementProgress(fixture.databaseClient()), validator, items, sources, NEW_RULES, settings,
                tenant -> Mono.empty(), worker, props, metrics, quota);
        assertThat(judge).isNotNull();

        worker.runPending(10).block(WAIT);

        assertThat(row(old.id())).containsEntry("STATUS", "ACTIVE").containsEntry("RULES_VERSION", 1);
        assertThat(calledFor).as("no replay without an explicit request").isEmpty();
        assertThat(fixture.jdbc().queryForObject("SELECT COUNT(*) FROM memory_jobs WHERE job_type = 'REPLAY_RULES'",
                Long.class)).isZero();
    }

    @Test
    void replayRecordsNewRulesVersionAndSupersedes() throws Exception {
        var old = oldInferred("用户可能住在杭州");
        recalledInTurns(old, "t-1", "t-2");
        fixture.jdbc().update("UPDATE memory_items SET last_recalled_at = TIMESTAMPADD(DAY, -40, CURRENT_TIMESTAMP(6))"
                + " WHERE id = ?", old.id());
        var lastRecalled = row(old.id()).get("LAST_RECALLED_AT");
        outputs.put(old.id(), action("UPDATE", old, "用户住在杭州（推断）"));

        var id = request(null);
        assertThat(status(id).status()).isEqualTo("PENDING");
        worker.runPending(5).block(WAIT);

        var oldRow = row(old.id());
        assertThat(oldRow).containsEntry("STATUS", "SUPERSEDED");
        var newId = (String) oldRow.get("SUPERSEDED_BY");
        assertThat(newId).isNotNull();
        var neu = row(newId);
        assertThat(neu).containsEntry("RULES_VERSION", 2).containsEntry("CONTENT", "用户住在杭州（推断）");
        assertThat(neu.get("LAST_RECALLED_AT")).as("inherits the recall time").isEqualTo(lastRecalled);
        assertThat(((Number) neu.get("INHERITED_RECALL_COUNT")).longValue()).isEqualTo(2);
        assertThat(new RecallReferences(fixture.databaseClient()).itemCount(ALICE, newId).block(WAIT))
                .as("the inherited count is the new item's effective count").isEqualTo(2);
        assertThat(fixture.jdbc().queryForObject("SELECT COUNT(*) FROM memory_recall_events WHERE ref_id = ?",
                Long.class, newId)).as("recall records are not copied").isZero();
        assertThat(fixture.jdbc().queryForObject("SELECT COUNT(*) FROM memory_item_sources WHERE item_id = ?",
                Long.class, newId)).isEqualTo(1);
        assertThat(mapping(id)).singleElement().satisfies(m -> {
            assertThat(m).containsEntry("ITEM_ID", old.id()).containsEntry("NEW_ITEM_ID", newId)
                    .containsEntry("CONVERSATION_ID", conversation).containsEntry("OUTCOME", "SUPERSEDED");
        });
        var done = status(id);
        assertThat(done.status()).isEqualTo("COMPLETED");
        assertThat(done.superseded()).isEqualTo(1);
        assertThat(done.processed()).isEqualTo(1);
        assertThat(done.toRulesVersion()).isEqualTo(2);
        assertThat(systemPrompts.getFirst()).contains("新规则").contains(old.id()).contains("用户可能住在杭州");
    }

    @Test
    void unsupportedItemsBecomeInactiveWithNoNewItem() throws Exception {
        var ignored = oldInferred("用户可能住在杭州");
        var silent = oldInferred("用户可能养猫");
        var elsewhere = oldInferred("用户可能喜欢爬山");
        var rejected = oldInferred("用户可能有车");
        outputs.put(ignored.id(), action("IGNORE", ignored, "x"));
        // Output about something else is ignored; nothing targets the item.
        outputs.put(elsewhere.id(), action("REMEMBER", null, "用户住在杭州"));
        // Evidence that is not in the source message fails validation.
        outputs.put(rejected.id(), action("UPDATE", rejected, "用户有车").replace("杭州", "火星"));

        var id = request(null);
        worker.runPending(5).block(WAIT);

        for (var item : List.of(ignored, silent, elsewhere, rejected)) {
            assertThat(row(item.id())).as(item.content()).containsEntry("STATUS", "INACTIVE");
        }
        assertThat(mapping(id)).hasSize(4).allSatisfy(m -> {
            assertThat(m.get("NEW_ITEM_ID")).isNull();
            assertThat(m).containsEntry("OUTCOME", "INACTIVATED");
        });
        assertThat(fixture.jdbc().queryForObject("SELECT COUNT(*) FROM memory_items WHERE rules_version = 2",
                Long.class)).as("no new item for unsupported ones, nor for unrelated output").isZero();
        assertThat(status(id).inactivated()).isEqualTo(4);
    }

    @Test
    void onlyOldUneditedInferredItemsInReplayableStatesAreRejudged() throws Exception {
        var eligibleActive = oldInferred("用户可能住在杭州");
        var eligibleCandidate = oldItem(conversation, "用户可能养猫", Assertion.INFERRED, ItemStatus.CANDIDATE, 1);
        var eligibleConflict = oldItem(conversation, "用户可能喜欢爬山", Assertion.INFERRED, ItemStatus.CONFLICT, 1);
        var stated = oldItem(conversation, "用户工号 A7-3921", Assertion.USER_STATED, ItemStatus.ACTIVE, 1);
        var confirmed = oldItem(conversation, "用户工号确认", Assertion.USER_CONFIRMED, ItemStatus.ACTIVE, 1);
        var forgotten = oldItem(conversation, "用户可能有车", Assertion.INFERRED, ItemStatus.FORGOTTEN, 1);
        var inactive = oldItem(conversation, "用户可能会游泳", Assertion.INFERRED, ItemStatus.INACTIVE, 1);
        var current = oldItem(conversation, "用户可能爱喝茶", Assertion.INFERRED, ItemStatus.ACTIVE, 2);
        var edited = oldInferred("用户可能是工程师");
        fixture.jdbc().update("INSERT INTO memory_audit_log (id, tenant_id, subject, actor, action, target_kind,"
                + " target_id, created_at) VALUES (?,?,?,?,'RECALL_COUNT_DECREMENT','ITEM',?,CURRENT_TIMESTAMP(6))",
                NonBlockingIds.uuid(), ALICE.tenantId(), ALICE.subject(), ALICE.subject(), edited.id());
        var systemTouched = oldInferred("用户可能会画画");
        fixture.jdbc().update("INSERT INTO memory_audit_log (id, tenant_id, subject, actor, action, target_kind,"
                + " target_id, created_at) VALUES (?,?,?,'system:decay','DECAY_INACTIVATE','ITEM',?,"
                + " CURRENT_TIMESTAMP(6))", NonBlockingIds.uuid(), ALICE.tenantId(), ALICE.subject(),
                systemTouched.id());

        request(null);
        worker.runPending(5).block(WAIT);

        assertThat(calledFor).containsExactlyInAnyOrder(eligibleActive.id(), eligibleCandidate.id(),
                eligibleConflict.id(), systemTouched.id());
        for (var untouched : List.of(stated, confirmed, forgotten, inactive, current, edited)) {
            assertThat(row(untouched.id())).as(untouched.content())
                    .containsEntry("STATUS", untouched.status().name())
                    .containsEntry("REVISION", 1L);
        }
    }

    @Test
    void fromRulesVersionLimitsTheReplay() throws Exception {
        var v0 = oldItem(conversation, "用户可能住在杭州", Assertion.INFERRED, ItemStatus.ACTIVE, 0);
        var v1 = oldInferred("用户可能养猫");

        request(1);
        worker.runPending(5).block(WAIT);

        assertThat(calledFor).containsExactly(v1.id());
        assertThat(row(v0.id())).containsEntry("STATUS", "ACTIVE");
    }

    @Test
    void replaySkipsDeletedConversations() throws Exception {
        var deletedConversation = seedConversation(false);
        var gone = oldItem(deletedConversation, "用户可能住在杭州", Assertion.INFERRED, ItemStatus.ACTIVE, 1);
        var live = oldInferred("用户可能养猫");

        var id = request(null);
        worker.runPending(5).block(WAIT);

        assertThat(calledFor).containsExactly(live.id());
        assertThat(row(gone.id())).containsEntry("STATUS", "ACTIVE").containsEntry("REVISION", 1L);
        assertThat(mapping(id)).anySatisfy(m -> assertThat(m).containsEntry("ITEM_ID", gone.id())
                .containsEntry("OUTCOME", "SKIPPED"));
        var done = status(id);
        assertThat(done.status()).isEqualTo("COMPLETED");
        assertThat(done.skipped()).isEqualTo(1);
    }

    @Test
    void replayIsExplicitAndResumable() throws Exception {
        var first = oldInferred("用户可能住在杭州");
        var second = oldInferred("用户可能养猫");
        outputs.put(first.id(), action("UPDATE", first, "用户住在杭州（推断）"));
        outputs.put(second.id(), action("UPDATE", second, "用户可能养了一只猫"));
        failing.add(first.id().compareTo(second.id()) < 0 ? second.id() : first.id());

        var id = request(null);
        worker.runPending(5).block(WAIT);

        assertThat(status(id).status()).as("interrupted, not finished").isNotEqualTo("COMPLETED");
        assertThat(mapping(id)).hasSize(1);
        fixture.jdbc().update("UPDATE memory_jobs SET available_at = CURRENT_TIMESTAMP(6)"
                + " WHERE job_type = 'REPLAY_RULES' AND status = 'PENDING'");
        worker.runPending(5).block(WAIT);

        assertThat(status(id).status()).isEqualTo("COMPLETED");
        assertThat(mapping(id)).hasSize(2);
        assertThat(calledFor).as("the finished item is not judged again").hasSize(3)
                .containsOnlyOnce(first.id().compareTo(second.id()) < 0 ? first.id() : second.id());
        assertThat(row(first.id())).containsEntry("STATUS", "SUPERSEDED");
        assertThat(row(second.id())).containsEntry("STATUS", "SUPERSEDED");
    }

    @Test
    void replayRespectsQuotaAndPostpones() throws Exception {
        var first = oldInferred("用户可能住在杭州");
        var second = oldInferred("用户可能养猫");
        outputs.put(first.id(), action("UPDATE", first, "用户住在杭州（推断）"));
        outputs.put(second.id(), action("UPDATE", second, "用户可能养了一只猫"));
        // Job reservation 100 + one item reservation 100 fit; after the first item (30 used) the next does not.
        props.getConsolidation().setDailyTokenLimit(210);

        var id = request(null);
        worker.runPending(5).block(WAIT);

        assertThat(calledFor).hasSize(1);
        assertThat(status(id).status()).isEqualTo("POSTPONED");
        assertThat(fixture.jdbc().queryForObject("SELECT COUNT(*) FROM memory_jobs WHERE job_type = 'REPLAY_RULES'"
                + " AND status = 'PENDING' AND available_at > CURRENT_TIMESTAMP(6)", Long.class))
                .as("continues tomorrow").isEqualTo(1);
        assertThat(fixture.jdbc().queryForObject("SELECT tokens FROM memory_token_usage WHERE tenant_id = ?",
                Long.class, ALICE.tenantId())).as("only the observed usage stays charged").isEqualTo(30);

        // Next day.
        fixture.jdbc().update("UPDATE memory_token_usage SET tokens = 0");
        fixture.jdbc().update("UPDATE memory_jobs SET available_at = CURRENT_TIMESTAMP(6)"
                + " WHERE job_type = 'REPLAY_RULES' AND status = 'PENDING'");
        worker.runPending(5).block(WAIT);

        assertThat(status(id).status()).isEqualTo("COMPLETED");
        assertThat(calledFor).hasSize(2);
        assertThat(mapping(id)).hasSize(2);
    }

    @Test
    void unknownReplayIsEmpty() {
        assertThat(replay.find("nope").block(WAIT)).isEmpty();
    }
}
