package io.github.sukimiya.agileagent.memory.judgement;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

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
import io.github.sukimiya.agileagent.memory.context.TokenEstimator;
import io.github.sukimiya.agileagent.memory.items.EntityGraphRepository;
import io.github.sukimiya.agileagent.memory.items.ItemStatus;
import io.github.sukimiya.agileagent.memory.items.MemoryItem;
import io.github.sukimiya.agileagent.memory.items.MemoryItemRepository;
import io.github.sukimiya.agileagent.memory.items.MemoryItemSourceRepository;
import io.github.sukimiya.agileagent.memory.items.RelationSpec;
import io.github.sukimiya.agileagent.memory.jobs.ConsolidationQuota;
import io.github.sukimiya.agileagent.memory.jobs.MemoryJobWorker;
import io.github.sukimiya.agileagent.memory.recall.MemorySettingsService;
import io.github.sukimiya.agileagent.service.LlmClient;
import io.github.sukimiya.agileagent.util.NonBlockingIds;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Relation write path T2 (user decisions 2026-11-04): relations come only from memory judgement, attached to the
 * item of REMEMBER / UPDATE / MERGE / SUPERSEDE; each relation is checked on its own and a bad one is dropped alone.
 */
class JudgementRelationsTest {

    static final Duration WAIT = Duration.ofSeconds(15);
    static final MemoryScope ALICE = MemoryTestStack.ALICE;
    static final ObjectMapper JSON = new ObjectMapper();
    static final String EXISTING_MARKER = "已有条目（可能为空）：\n";

    MemoryTestFixture fixture;
    MemoryProperties props;
    SimpleMeterRegistry meters;
    MemoryItemRepository items;
    MemoryItemSourceRepository sources;
    EntityGraphRepository graph;
    JudgementJobHandler handler;
    final List<String> outputs = new CopyOnWriteArrayList<>();
    final List<String> systemPrompts = new CopyOnWriteArrayList<>();
    String conversation;
    long sequence;

    protected MemoryTestFixture newFixture() {
        return MemoryTestFixture.h2();
    }

    @BeforeEach
    void setUp() {
        fixture = newFixture();
        props = new MemoryProperties();
        props.getJudgement().setEnabled(true);
        props.getConsolidation().setDailyTokenLimit(1_000_000);
        meters = new SimpleMeterRegistry();
        var metrics = new MemoryMetrics(meters);
        var db = fixture.databaseClient();
        items = new MemoryItemRepository(db);
        sources = new MemoryItemSourceRepository(db);
        graph = new EntityGraphRepository(db, props);
        var quota = new ConsolidationQuota(db, props, metrics);
        var worker = new MemoryJobWorker(db, () -> false, quota, props, metrics);
        LlmClient llm = new LlmClient() {
            @Override public Flux<LlmEvent> chatStream(List<Message> messages, List<ToolDefinition> tools) {
                return chatStream(messages, tools, new CallOptions(null));
            }
            @Override public Flux<LlmEvent> chatStream(List<Message> messages, List<ToolDefinition> tools,
                    CallOptions options) {
                systemPrompts.add(String.valueOf(messages.getFirst().content()));
                return Flux.just(new LlmEvent.Completed(outputs.isEmpty() ? "[]" : outputs.removeFirst()),
                        new LlmEvent.Usage(10, 20, 30));
            }
        };
        handler = new JudgementJobHandler(db, fixture.connectionFactory(), new JudgementProgress(db),
                new JudgementValidator(db, items, props), items, sources, GlobalRules.loadFromClasspath(),
                new MemorySettingsService(db), tenant -> Mono.just(new JudgementJobHandler.JudgeModel(llm, "fake")),
                worker, props, metrics, quota, graph);
        conversation = NonBlockingIds.uuid();
        fixture.jdbc().update("INSERT INTO sessions (id, tenant_id, user_id, title, revision, created_at, updated_at)"
                + " VALUES (?,?,?,'t',1,CURRENT_TIMESTAMP(6),CURRENT_TIMESTAMP(6))",
                conversation, ALICE.tenantId(), ALICE.subject());
    }

    /** One completed turn with a user message {@code text}; returns its message id. */
    String userSays(String text) throws Exception {
        long seq = ++sequence;
        var turn = "turn-" + seq;
        fixture.jdbc().update("INSERT INTO chat_turns (id, tenant_id, subject, conversation_id, request_hash, status,"
                + " fence, created_at, updated_at, finished_at) VALUES (?,?,?,?,?,'COMPLETED',1,"
                + " CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6))",
                turn, ALICE.tenantId(), ALICE.subject(), conversation, "hash-" + seq);
        var id = "msg-" + seq;
        fixture.jdbc().update("INSERT INTO chat_messages (id, tenant_id, subject, conversation_id, turn_id, sequence,"
                + " role, source_type, payload, created_at, updated_at) VALUES (?,?,?,?,?,?, 'user', ?, ?,"
                + " CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6))",
                id, ALICE.tenantId(), ALICE.subject(), conversation, turn, seq, SourceType.USER_INPUT.name(),
                JSON.writeValueAsString(LlmClient.Message.user(text)));
        return id;
    }

    ObjectNode action(ArrayNode arr, String kind, String assertion, String content, String messageId) {
        var o = arr.addObject();
        o.put("action", kind);
        o.put("itemType", "FACT");
        o.put("content", content);
        o.put("assertion", assertion);
        o.putArray("sourceMessageIds").add(messageId);
        o.put("reason", "test");
        o.put("importance", 0.8);
        o.put("confidence", 0.9);
        return o;
    }

    static void relation(ObjectNode action, String fromName, String fromType, String rel, String toName,
            String toType) {
        var list = action.has("relations") ? (ArrayNode) action.get("relations") : action.putArray("relations");
        var r = list.addObject();
        r.putObject("from").put("name", fromName).put("type", fromType);
        r.put("relation", rel);
        r.putObject("to").put("name", toName).put("type", toType);
    }

    void judge(ArrayNode arr) throws Exception {
        outputs.add(JSON.writeValueAsString(arr));
        handler.judge(ALICE, conversation, sequence).block(WAIT);
    }

    /** "from —relation→ to [status]" for every relation row of the owner, ordered. */
    List<String> relationRows() {
        return fixture.jdbc().queryForList("SELECT fe.name AS f, r.relation AS rel, te.name AS t, r.status AS st,"
                + " r.item_id AS item FROM memory_relations r JOIN memory_entities fe ON fe.id = r.from_entity"
                + " JOIN memory_entities te ON te.id = r.to_entity WHERE r.tenant_id = ? AND r.subject = ?",
                ALICE.tenantId(), ALICE.subject()).stream()
                .map(m -> lower(m, "f") + " —" + lower(m, "rel") + "→ " + lower(m, "t") + " [" + lower(m, "st") + "]")
                .sorted().toList();
    }

    static String lower(Map<String, Object> row, String key) {
        var v = row.containsKey(key) ? row.get(key) : row.get(key.toUpperCase(java.util.Locale.ROOT));
        return String.valueOf(v);
    }

    String onlyItemId() {
        return fixture.jdbc().queryForObject("SELECT id FROM memory_items WHERE tenant_id = ? AND status = 'ACTIVE'",
                String.class, ALICE.tenantId());
    }

    double written() {
        var c = meters.find("memory.relations.written").counter();
        return c == null ? 0 : c.count();
    }

    double rejected(String reason) {
        var c = meters.find("memory.relations.rejected").tag("reason", reason).counter();
        return c == null ? 0 : c.count();
    }

    MemoryItem existingItem(String content, String messageId) {
        var item = MemoryItem.newItem(ALICE, conversation, "FACT", content, ItemStatus.ACTIVE,
                MemoryItem.Assertion.USER_STATED, 0.5, 0.9, 2, "m", "seed");
        items.save(item).block(WAIT);
        sources.saveAll(item.id(), List.of(new ValidatedAction.SourceRef(messageId, conversation,
                SourceType.USER_INPUT, content))).block(WAIT);
        return item;
    }

    @Test
    void rememberWritesTheItemsRelations() throws Exception {
        var msg = userSays("我负责星河项目，星河项目使用 MySQL。");
        var arr = JSON.createArrayNode();
        var a = action(arr, "REMEMBER", "USER_STATED", "用户负责星河项目，项目使用 MySQL", msg);
        relation(a, "用户", "SELF", "负责", "星河项目", "PROJECT");
        relation(a, "星河项目", "PROJECT", "使用", "MySQL", "PRODUCT");
        judge(arr);

        assertThat(relationRows()).containsExactly("星河项目 —使用→ MySQL [ACTIVE]", "用户 —负责→ 星河项目 [ACTIVE]");
        assertThat(fixture.jdbc().queryForList("SELECT DISTINCT item_id FROM memory_relations", String.class))
                .containsExactly(onlyItemId());
        assertThat(graph.oneHop(ALICE, conversation, false, List.of(EntityGraphRepository.SELF_KEY), 10)
                .collectList().block(WAIT)).hasSize(1);
        assertThat(written()).isEqualTo(2);
    }

    @Test
    void aBadRelationIsDroppedAloneAndCountedByReason() throws Exception {
        props.getRelations().setMaxPerItem(2);
        props.getRelations().setMaxEntityNameChars(8);
        var msg = userSays("我负责星河项目，星河项目使用 MySQL 和 Redis，联系 13812345678，超长名字星河项目第二期工程。");
        var arr = JSON.createArrayNode();
        var a = action(arr, "REMEMBER", "USER_STATED", "用户负责星河项目", msg);
        relation(a, "用户", "SELF", "LIKES", "星河项目", "PROJECT");
        relation(a, "星河项目", "THING", "使用", "MySQL", "PRODUCT");
        relation(a, "星河项目", "PROJECT", "使用", "天枢项目", "PROJECT");
        relation(a, "星河项目", "PROJECT", "合作", "星河项目", "PROJECT");
        relation(a, "超长名字星河项目第二期工程", "PROJECT", "使用", "MySQL", "PRODUCT");
        relation(a, "星河项目", "PROJECT", "拥有", "13812345678", "OTHER");
        relation(a, "星河项目", "PROJECT", "使用", "MySQL", "PRODUCT");
        relation(a, "星河项目", "PROJECT", "使用", "MySQL", "PRODUCT");
        relation(a, "用户", "SELF", "负责", "星河项目", "PROJECT");
        relation(a, "星河项目", "PROJECT", "使用", "Redis", "PRODUCT");
        judge(arr);

        assertThat(onlyItemId()).as("the item is written anyway").isNotBlank();
        assertThat(relationRows()).containsExactly("星河项目 —使用→ MySQL [ACTIVE]", "用户 —负责→ 星河项目 [ACTIVE]");
        assertThat(rejected("BAD_RELATION_TYPE")).isEqualTo(1);
        assertThat(rejected("BAD_ENTITY_TYPE")).isEqualTo(1);
        assertThat(rejected("NAME_NOT_IN_SOURCE")).isEqualTo(1);
        assertThat(rejected("SAME_ENTITY")).isEqualTo(1);
        assertThat(rejected("NAME_TOO_LONG")).isEqualTo(1);
        assertThat(rejected("PII_OR_CREDENTIAL")).isEqualTo(1);
        assertThat(rejected("DUPLICATE")).as("the same edge twice in one action").isEqualTo(1);
        assertThat(rejected("TOO_MANY")).as("over max-per-item").isEqualTo(1);
        assertThat(written()).isEqualTo(2);
    }

    @Test
    void userSelfNeedsAFirstPersonWordAndAUserAssertion() throws Exception {
        var noFirstPerson = userSays("星河项目由张三负责。");
        var arr = JSON.createArrayNode();
        var a = action(arr, "REMEMBER", "USER_STATED", "星河项目由张三负责", noFirstPerson);
        relation(a, "用户", "SELF", "负责", "星河项目", "PROJECT");
        relation(a, "张三", "PERSON", "负责", "星河项目", "PROJECT");
        judge(arr);
        assertThat(relationRows()).containsExactly("张三 —负责→ 星河项目 [ACTIVE]");
        assertThat(rejected("SELF_NOT_IN_SOURCE")).isEqualTo(1);

        // Hypothetical / pasted / role-play content is forced to INFERRED and cannot produce a user-self relation.
        var hypothetical = userSays("假如我负责天枢项目，会怎样？");
        var arr2 = JSON.createArrayNode();
        var b = action(arr2, "REMEMBER", "INFERRED", "假如用户负责天枢项目", hypothetical);
        b.put("confidence", 0.6);
        relation(b, "用户", "SELF", "负责", "天枢项目", "PROJECT");
        judge(arr2);
        assertThat(rejected("SELF_NOT_USER_ASSERTED")).isEqualTo(1);
        assertThat(relationRows()).noneMatch(r -> r.contains("天枢项目"));
    }

    @Test
    void updateAndMergeReplaceTheItemsRelationSet() throws Exception {
        var first = userSays("我负责北斗项目，北斗项目使用 PostgreSQL。");
        var item = existingItem("用户负责北斗项目", first);
        graph.replaceItemRelations(ALICE, item.id(), List.of(
                RelationSpec.of("用户", "SELF", "负责", "北斗项目", "PROJECT"),
                RelationSpec.of("北斗项目", "PROJECT", "使用", "PostgreSQL", "PRODUCT"))).block(WAIT);

        // Field absent: the item's relations stay.
        var keep = userSays("北斗项目的事继续由我负责。");
        var arr = JSON.createArrayNode();
        action(arr, "UPDATE", "USER_STATED", "用户继续负责北斗项目", keep).put("targetItemId", item.id())
                .put("expectedRevision", 1);
        judge(arr);
        assertThat(relationRows()).containsExactly("北斗项目 —使用→ PostgreSQL [ACTIVE]",
                "用户 —负责→ 北斗项目 [ACTIVE]");

        // Field present: the new set replaces the old one; the old relations are SUPERSEDED.
        var change = userSays("北斗项目改用 MySQL 了，还是我负责。");
        var arr2 = JSON.createArrayNode();
        var u = action(arr2, "MERGE", "USER_STATED", "用户负责北斗项目，项目改用 MySQL", change)
                .put("targetItemId", item.id()).put("expectedRevision", 2);
        relation(u, "用户", "SELF", "负责", "北斗项目", "PROJECT");
        relation(u, "北斗项目", "PROJECT", "使用", "MySQL", "PRODUCT");
        judge(arr2);
        assertThat(relationRows()).containsExactly("北斗项目 —使用→ MySQL [ACTIVE]",
                "北斗项目 —使用→ PostgreSQL [SUPERSEDED]", "用户 —负责→ 北斗项目 [ACTIVE]");

        // An empty list clears the set.
        var clear = userSays("北斗项目已经结项，我不再负责。");
        var arr3 = JSON.createArrayNode();
        action(arr3, "UPDATE", "USER_STATED", "北斗项目已结项", clear).put("targetItemId", item.id())
                .put("expectedRevision", 3).putArray("relations");
        judge(arr3);
        assertThat(relationRows()).allMatch(r -> r.endsWith("[SUPERSEDED]"));
    }

    @Test
    void supersedeGivesTheNewItemTheNewRelations() throws Exception {
        var first = userSays("星河项目使用 PostgreSQL。");
        var old = existingItem("星河项目使用 PostgreSQL", first);
        graph.replaceItemRelations(ALICE, old.id(), List.of(
                RelationSpec.of("星河项目", "PROJECT", "使用", "PostgreSQL", "PRODUCT"))).block(WAIT);
        var correction = userSays("更正一下：星河项目使用 MySQL，不是 PostgreSQL。");
        var arr = JSON.createArrayNode();
        var s = action(arr, "SUPERSEDE", "USER_CONFIRMED", "星河项目使用 MySQL", correction)
                .put("targetItemId", old.id()).put("expectedRevision", 1);
        relation(s, "星河项目", "PROJECT", "使用", "MySQL", "PRODUCT");
        judge(arr);

        var neu = onlyItemId();
        assertThat(neu).isNotEqualTo(old.id());
        assertThat(fixture.jdbc().queryForObject("SELECT COUNT(*) FROM memory_relations WHERE item_id = ?",
                Integer.class, neu)).isEqualTo(1);
        assertThat(graph.oneHop(ALICE, conversation, false, List.of("星河项目"), 10)
                .map(h -> h.to().name()).collectList().block(WAIT)).containsExactly("MySQL");
    }

    @Test
    void relationsOnOtherActionsAreDropped() throws Exception {
        var msg = userSays("我负责星河项目。");
        var item = existingItem("用户负责星河项目", msg);
        var arr = JSON.createArrayNode();
        var d = action(arr, "DEPRIORITIZE", "USER_STATED", "不重要", msg).put("targetItemId", item.id())
                .put("expectedRevision", 1);
        relation(d, "用户", "SELF", "负责", "星河项目", "PROJECT");
        judge(arr);
        assertThat(relationRows()).isEmpty();
        assertThat(rejected("UNSUPPORTED_ACTION")).isEqualTo(1);
    }

    @Test
    void disabledRelationsAreNeitherAskedForNorWritten() throws Exception {
        props.getRelations().setEnabled(false);
        var msg = userSays("我负责星河项目。");
        var arr = JSON.createArrayNode();
        relation(action(arr, "REMEMBER", "USER_STATED", "用户负责星河项目", msg), "用户", "SELF", "负责", "星河项目",
                "PROJECT");
        judge(arr);
        assertThat(onlyItemId()).isNotBlank();
        assertThat(relationRows()).isEmpty();
        assertThat(meters.find("memory.relations.rejected").counters()).isEmpty();
        assertThat(systemPrompts.getLast()).doesNotContain("relations");
    }

    @Test
    void promptV2ListsTheConfiguredVocabularies() throws Exception {
        props.getRelations().setTypes(List.of("负责", "研究"));
        props.getRelations().setMaxPerItem(4);
        userSays("你好");
        judge(JSON.createArrayNode());
        var system = systemPrompts.getLast();
        assertThat(MemoryJudgementPrompt.VERSION).isEqualTo("memory-judgement-v2");
        assertThat(system).contains("rules-version: 2").contains("relations").contains("负责、研究")
                .contains("PERSON、PROJECT、ORG、PLACE、PRODUCT、TOPIC、OTHER").contains("SELF").contains("4");
        assertThat(fixture.jdbc().queryForList("SELECT rules_version FROM memory_items", Integer.class)).isEmpty();
    }

    @Test
    void existingItemsCarryTheirRelationsWithinTheTokenBudget() throws Exception {
        var msg = userSays("我负责星河项目。");
        var item = existingItem("用户负责星河项目", msg);
        graph.replaceItemRelations(ALICE, item.id(), List.of(
                RelationSpec.of("用户", "SELF", "负责", "星河项目", "PROJECT"))).block(WAIT);
        userSays("星河项目进展如何？");
        judge(JSON.createArrayNode());
        var existing = existingPart(systemPrompts.getLast());
        assertThat(existing).contains(item.id()).contains("用户(SELF) —负责→ 星河项目(PROJECT)");

        // The relations count within existing-max-tokens: a budget that fits only the bare item drops it.
        var bare = JSON.createArrayNode();
        bare.addObject().put("id", item.id()).put("revision", 1).put("itemType", "FACT").put("content", "用户负责星河项目")
                .put("assertion", "USER_STATED").put("status", "ACTIVE");
        props.getJudgement().setExistingMaxTokens((int) TokenEstimator.estimate(bare.toString()));
        userSays("再问一次星河项目");
        judge(JSON.createArrayNode());
        assertThat(existingPart(systemPrompts.getLast())).isEqualTo("[]");
    }

    static String existingPart(String system) {
        int at = system.indexOf(EXISTING_MARKER);
        return at < 0 ? "" : system.substring(at + EXISTING_MARKER.length());
    }
}
