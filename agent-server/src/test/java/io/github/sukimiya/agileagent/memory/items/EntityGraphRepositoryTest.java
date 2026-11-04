package io.github.sukimiya.agileagent.memory.items;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.github.sukimiya.agileagent.memory.MemoryProperties;
import io.github.sukimiya.agileagent.memory.MemoryScope;
import io.github.sukimiya.agileagent.memory.MemoryTestFixture;
import io.github.sukimiya.agileagent.memory.MemoryTestStack;
import io.github.sukimiya.agileagent.memory.context.ContextAssembler;
import io.github.sukimiya.agileagent.memory.context.ContextBudget;
import io.github.sukimiya.agileagent.memory.context.ContextPurpose;
import io.github.sukimiya.agileagent.memory.context.ModelWindow;
import io.github.sukimiya.agileagent.memory.recall.RecallResult;
import io.github.sukimiya.agileagent.service.LlmClient;
import reactor.core.publisher.Mono;

/**
 * One-hop entity graph (relation write path T1, user decisions 2026-11-04): a relation belongs to a memory item and
 * is visible exactly while that item is (ACTIVE, valid, in the recall scope); filtering happens in SQL.
 */
class EntityGraphRepositoryTest {

    static final Duration WAIT = Duration.ofSeconds(15);
    static final MemoryScope ALICE = MemoryTestStack.ALICE;
    static final String CONV = "11111111-1111-4111-8111-111111111111";
    static final String OTHER_CONV = "22222222-2222-4222-8222-222222222222";

    MemoryTestFixture fixture;
    MemoryProperties props;
    EntityGraphRepository graph;
    MemoryItemRepository items;

    protected MemoryTestFixture newFixture() {
        return MemoryTestFixture.h2();
    }

    @BeforeEach
    void setUp() {
        fixture = newFixture();
        props = new MemoryProperties();
        graph = new EntityGraphRepository(fixture.databaseClient(), props);
        items = new MemoryItemRepository(fixture.databaseClient());
    }

    /** A user-level (conversation null) or session-level ACTIVE item. */
    MemoryItem item(MemoryScope scope, String conversationId, String content) {
        var item = MemoryItem.newItem(scope, conversationId, "FACT", content, ItemStatus.ACTIVE,
                MemoryItem.Assertion.USER_STATED, 0.5, 0.9, 1, "m", "r");
        items.save(item).block(WAIT);
        return item;
    }

    MemoryEntity entity(MemoryScope scope, String name, String type) {
        return graph.upsertEntity(scope, name, type).block(WAIT);
    }

    MemoryRelation edge(MemoryScope scope, MemoryItem item, MemoryEntity from, String relation, MemoryEntity to) {
        return graph.saveRelation(scope, item.id(), from.id(), relation, to.id()).block(WAIT);
    }

    List<EntityGraphRepository.RelationHop> userLevel(MemoryScope scope, String... keys) {
        return graph.oneHop(scope, CONV, true, List.of(keys), 10).collectList().block(WAIT);
    }

    void setItem(MemoryItem item, String status, int valid) {
        fixture.jdbc().update("UPDATE memory_items SET status = ?, is_valid = ? WHERE id = ?", status, valid,
                item.id());
    }

    @Test
    void visibilityFollowsTheItem() {
        var item = item(ALICE, null, "我负责星河项目");
        var self = graph.self(ALICE).block(WAIT);
        var project = entity(ALICE, "星河项目", "PROJECT");
        var rel = edge(ALICE, item, self, "负责", project);

        var hops = userLevel(ALICE, "星河项目");
        assertThat(hops).singleElement().satisfies(h -> {
            assertThat(h.relation().id()).isEqualTo(rel.id());
            assertThat(h.from().name()).isEqualTo("用户");
            assertThat(h.to().name()).isEqualTo("星河项目");
            assertThat(h.relation().relation()).isEqualTo("负责");
        });

        for (var status : List.of("SUPERSEDED", "INACTIVE", "CONFLICT", "CANDIDATE", "FORGOTTEN", "DELETED")) {
            setItem(item, status, 1);
            assertThat(userLevel(ALICE, "星河项目")).as(status).isEmpty();
        }
        setItem(item, "ACTIVE", 0);
        assertThat(userLevel(ALICE, "星河项目")).as("invalid item").isEmpty();
        // Restoring the item (e.g. undoing a forget) shows the relation again, nothing else to maintain.
        setItem(item, "ACTIVE", 1);
        assertThat(userLevel(ALICE, "星河项目")).hasSize(1);
    }

    @Test
    void crossConversationSwitchAndConversationScopeComeFromTheItem() {
        var userLevel = item(ALICE, null, "我负责星河项目");
        var here = item(ALICE, CONV, "我负责北斗项目");
        var elsewhere = item(ALICE, OTHER_CONV, "我负责天枢项目");
        var self = graph.self(ALICE).block(WAIT);
        edge(ALICE, userLevel, self, "负责", entity(ALICE, "星河项目", "PROJECT"));
        edge(ALICE, here, self, "负责", entity(ALICE, "北斗项目", "PROJECT"));
        edge(ALICE, elsewhere, self, "负责", entity(ALICE, "天枢项目", "PROJECT"));

        // Switch on: user-level items only (same rule as item recall).
        assertThat(graph.oneHop(ALICE, CONV, true, List.of(EntityGraphRepository.SELF_KEY), 10)
                .map(h -> h.to().name()).collectList().block(WAIT)).containsExactly("星河项目");
        // Switch off: only items of this conversation; nothing leaks from another conversation.
        assertThat(graph.oneHop(ALICE, CONV, false, List.of(EntityGraphRepository.SELF_KEY), 10)
                .map(h -> h.to().name()).collectList().block(WAIT)).containsExactly("北斗项目");
        assertThat(graph.oneHop(ALICE, null, false, List.of(EntityGraphRepository.SELF_KEY), 10)
                .collectList().block(WAIT)).as("no conversation, switch off").isEmpty();
    }

    @Test
    void supersededOrInvalidRelationsAndEntitiesAreNotCurrent() {
        var item = item(ALICE, null, "星河项目改用 MySQL");
        var project = entity(ALICE, "星河项目", "PROJECT");
        var old = edge(ALICE, item, project, "使用", entity(ALICE, "PostgreSQL", "PRODUCT"));
        graph.supersedeRelation(ALICE, old.id()).block(WAIT);
        var mysql = entity(ALICE, "MySQL", "PRODUCT");
        edge(ALICE, item, project, "使用", mysql);
        assertThat(userLevel(ALICE, "星河项目")).singleElement()
                .satisfies(h -> assertThat(h.to().id()).isEqualTo(mysql.id()));

        fixture.jdbc().update("UPDATE memory_entities SET is_valid = 0 WHERE id = ?", mysql.id());
        assertThat(userLevel(ALICE, "星河项目")).as("an invalid end hides the relation").isEmpty();
    }

    @Test
    void sameEdgeIsStoredOncePerItemAndShownOnce() {
        var first = item(ALICE, null, "我负责星河项目");
        var second = item(ALICE, null, "星河项目由我负责");
        var self = graph.self(ALICE).block(WAIT);
        var project = entity(ALICE, "星河项目", "PROJECT");
        var a = edge(ALICE, first, self, "负责", project);
        graph.supersedeRelation(ALICE, a.id()).block(WAIT);
        var again = edge(ALICE, first, self, "负责", project);
        assertThat(again.id()).as("saving the same edge of the same item reuses (and reactivates) the row")
                .isEqualTo(a.id());
        assertThat(fixture.jdbc().queryForObject("SELECT status FROM memory_relations WHERE id = ?", String.class,
                a.id())).isEqualTo("ACTIVE");
        var b = edge(ALICE, second, self, "负责", project);

        assertThat(fixture.jdbc().queryForObject("SELECT COUNT(*) FROM memory_relations", Integer.class))
                .isEqualTo(2);
        assertThat(userLevel(ALICE, "星河项目")).as("one line per (from, relation, to), the newest")
                .singleElement().satisfies(h -> assertThat(h.relation().id()).isEqualTo(b.id()));
    }

    @Test
    void limitAppliesToMatchingRelationsNewestFirst() {
        var self = graph.self(ALICE).block(WAIT);
        var target = entity(ALICE, "目标项目", "PROJECT");
        var relevant = edge(ALICE, item(ALICE, null, "我负责目标项目"), self, "负责", target);
        for (int i = 0; i < 20; i++) {
            edge(ALICE, item(ALICE, null, "无关" + i), entity(ALICE, "无关甲" + i, "OTHER"), "合作",
                    entity(ALICE, "无关乙" + i, "OTHER"));
        }
        assertThat(graph.oneHop(ALICE, CONV, true, List.of("目标项目"), 5).collectList().block(WAIT))
                .singleElement().satisfies(h -> assertThat(h.relation().id()).isEqualTo(relevant.id()));

        for (int i = 0; i < 15; i++) {
            edge(ALICE, item(ALICE, null, "我负责项目" + i), self, "负责", entity(ALICE, "项目" + i, "PROJECT"));
        }
        var limited = graph.oneHop(ALICE, CONV, true, List.of(EntityGraphRepository.SELF_KEY), 5)
                .collectList().block(WAIT);
        assertThat(limited).hasSize(5);
        assertThat(limited.getFirst().to().name()).as("newest first").isEqualTo("项目14");
    }

    @Test
    void graphsAreOwnerScoped() {
        var bob = new MemoryScope(ALICE.tenantId(), "bob");
        var aliceLower = new MemoryScope(ALICE.tenantId(), ALICE.subject().toUpperCase(java.util.Locale.ROOT));
        var item = item(bob, null, "我负责星河项目");
        edge(bob, item, graph.self(bob).block(WAIT), "负责", entity(bob, "星河项目", "PROJECT"));
        assertThat(userLevel(bob, "星河项目")).hasSize(1);
        assertThat(userLevel(ALICE, "星河项目")).isEmpty();
        assertThat(userLevel(aliceLower, "星河项目")).isEmpty();
    }

    @Test
    void userSelfEntityIsReservedAndCreatedOnce() {
        var self = graph.self(ALICE).block(WAIT);
        assertThat(self.normalizedKey()).isEqualTo(EntityGraphRepository.SELF_KEY);
        assertThat(self.entityType()).isEqualTo(EntityGraphRepository.SELF_TYPE);
        assertThat(self.name()).isEqualTo("用户");
        assertThat(graph.self(ALICE).block(WAIT).id()).isEqualTo(self.id());
        assertThatThrownBy(() -> graph.upsertEntity(ALICE, " @SELF ", "PERSON").block(WAIT))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("reserved");
        assertThatThrownBy(() -> graph.upsertEntity(ALICE, "张三", "SELF").block(WAIT))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void writesAreCheckedAgainstTheConfiguredVocabularies() {
        var item = item(ALICE, null, "我负责星河项目");
        var a = entity(ALICE, "星河项目", "PROJECT");
        var b = entity(ALICE, "北斗项目", "PROJECT");
        assertThatThrownBy(() -> graph.upsertEntity(ALICE, "某物", "THING").block(WAIT))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("entity type");
        assertThatThrownBy(() -> graph.upsertEntity(ALICE, "长".repeat(65), "OTHER").block(WAIT))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("64");
        assertThatThrownBy(() -> graph.upsertEntity(ALICE, "  ", "OTHER").block(WAIT))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> graph.saveRelation(ALICE, item.id(), a.id(), "LIKES", b.id()).block(WAIT))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("relation type");
        assertThatThrownBy(() -> graph.saveRelation(ALICE, null, a.id(), "合作", b.id()).block(WAIT))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("item");
        assertThatThrownBy(() -> graph.saveRelation(ALICE, item.id(), a.id(), "合作", a.id()).block(WAIT))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("itself");

        props.getRelations().setTypes(List.of("合作"));
        props.getRelations().setEntityTypes(List.of("THING"));
        assertThat(graph.upsertEntity(ALICE, "某物", "THING").block(WAIT).entityType()).isEqualTo("THING");
        assertThat(graph.saveRelation(ALICE, item.id(), a.id(), "合作", b.id()).block(WAIT).relation())
                .isEqualTo("合作");
    }

    @Test
    void invalidSettingsFailAtStartup() {
        var bad = new MemoryProperties();
        bad.getRelations().setEntityMatchMinChars(70);
        assertThatThrownBy(() -> new EntityGraphRepository(fixture.databaseClient(), bad))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void onlyRelevantSubgraphInjected() {
        var recalled = new RecallResult(List.of(), false, Duration.ZERO, null,
                List.of("项目甲 —使用→ 方案乙"));
        try (var stack = MemoryTestStack.of(fixture)) {
            var conversation = MemoryTestStack.newConversationId();
            fixture.jdbc().update("INSERT INTO sessions (id, tenant_id, user_id, title, revision, created_at, updated_at)"
                    + " VALUES (?, ?, ?, 't', 0, CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6))",
                    conversation, ALICE.tenantId(), ALICE.subject());
            var ticket = stack.accept(conversation, "项目甲怎样了？").ticket();
            var assembler = new ContextAssembler(stack.history(), new ContextBudget(stack.props()), stack.metrics());
            var ctx = assembler.assemble(ticket, ContextPurpose.DIRECT_ANSWER, new ModelWindow(8000, 500),
                    List.of(LlmClient.Message.system("sys")), "项目甲怎样了？", Mono.just(recalled)).block(WAIT);
            var joined = ctx.messages().stream().map(LlmClient.Message::content).reduce("", (a1, b1) -> a1 + b1);
            assertThat(joined).contains("项目甲 —使用→ 方案乙");
            assertThat(joined).doesNotContain("无关实体");
        }
    }
}
