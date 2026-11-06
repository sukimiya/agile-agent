package io.github.sukimiya.agileagent.memory.recall;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.github.sukimiya.agileagent.memory.MemoryModel.TurnStatus;
import io.github.sukimiya.agileagent.memory.MemoryScope;
import io.github.sukimiya.agileagent.memory.MemoryTestFixture;
import io.github.sukimiya.agileagent.memory.MemoryTestStack;
import io.github.sukimiya.agileagent.memory.context.ContextAssembler;
import io.github.sukimiya.agileagent.memory.context.ContextBudget;
import io.github.sukimiya.agileagent.memory.context.ContextPurpose;
import io.github.sukimiya.agileagent.memory.context.ModelWindow;
import io.github.sukimiya.agileagent.memory.context.TokenEstimator;
import io.github.sukimiya.agileagent.memory.items.EntityGraphRepository;
import io.github.sukimiya.agileagent.memory.items.ItemStatus;
import io.github.sukimiya.agileagent.memory.items.MemoryItem;
import io.github.sukimiya.agileagent.memory.items.MemoryItemRepository;
import io.github.sukimiya.agileagent.memory.items.ProfileCache;
import io.github.sukimiya.agileagent.memory.items.ProfileService;
import io.github.sukimiya.agileagent.memory.items.RelationSpec;
import io.github.sukimiya.agileagent.memory.jobs.MemoryJobWorker;
import io.github.sukimiya.agileagent.memory.summary.SummaryRepository;
import io.github.sukimiya.agileagent.service.LlmClient;
import reactor.core.publisher.Mono;

/**
 * Relation recall (relation write path T1 + T3): visibility follows the item and the cross-conversation switch;
 * entity keys are names contained in the question (config min length / scan limit), first-person words hit the
 * user-self entity; lines are {@code 名称（类型）—关系→ 名称}; they count in the recall token budget and do not
 * refresh {@code last_recalled_at}.
 */
class RelationRecallTest {

    static final Duration WAIT = Duration.ofSeconds(15);
    static final MemoryScope ALICE = MemoryTestStack.ALICE;

    MemoryTestStack stack;
    MemorySettingsService settings;
    MemoryItemRepository items;
    EntityGraphRepository graph;
    MemoryRetriever retriever;

    protected MemoryTestFixture newFixture() {
        return MemoryTestFixture.h2();
    }

    @BeforeEach
    void setUp() {
        stack = MemoryTestStack.of(newFixture());
        var db = stack.fixture().databaseClient();
        var chunks = new ChunkRepository(db, stack.conversations());
        new ChunkIndexer(db, stack.fixture().connectionFactory(), chunks, new MemoryJobWorker(db, () -> false),
                stack.props());
        items = new MemoryItemRepository(db);
        graph = new EntityGraphRepository(db, stack.props());
        var cache = new ProfileCache(null, stack.props());
        settings = new MemorySettingsService(db, cache);
        var profiles = new ProfileService(db, items, settings, cache, stack.props());
        retriever = new MemoryRetriever(db, stack.props(), settings, chunks, new SummaryRepository(db,
                stack.conversations()), FullTextSearch.forDatabase(db), stack.metrics(), items, graph, profiles);
    }

    @AfterEach
    void tearDown() {
        stack.close();
    }

    MemoryItem related(String conversationId, String fromName, String fromType, String relation, String toName,
            String toType) {
        var item = MemoryItem.newItem(ALICE, conversationId, "FACT", fromName + relation + toName, ItemStatus.ACTIVE,
                MemoryItem.Assertion.USER_STATED, 0.5, 0.9, 1, "m", "r");
        items.save(item).block(WAIT);
        graph.replaceItemRelations(ALICE, item.id(), List.of(RelationSpec.of(fromName, fromType, relation, toName,
                toType))).block(WAIT);
        return item;
    }

    MemoryItem relatedSelf(String conversationId, String project) {
        return related(conversationId, "用户", "SELF", "负责", project, "PROJECT");
    }

    List<String> lines(String conversationId, String question) {
        var ticket = stack.accept(ALICE, conversationId, MemoryTestStack.newConversationId(), question).ticket();
        try {
            return retriever.recall(ticket, question).block(WAIT).relationLines();
        } finally {
            stack.finalizer().finish(ticket, TurnStatus.FAILED, "").block(WAIT);
        }
    }

    @Test
    void switchOffKeepsSessionRelationsInTheirConversation() {
        var here = MemoryTestStack.newConversationId();
        var elsewhere = MemoryTestStack.newConversationId();
        relatedSelf(here, "星河项目");
        relatedSelf(null, "北斗项目");

        assertThat(lines(here, "星河项目 北斗项目")).singleElement().asString().contains("星河项目");
        assertThat(lines(elsewhere, "星河项目 北斗项目")).as("no leak into another conversation").isEmpty();

        settings.update(ALICE, true).block(WAIT);
        assertThat(lines(elsewhere, "星河项目 北斗项目")).singleElement().asString().contains("北斗项目");
    }

    @Test
    void recalledRelationsAreCappedByTheRelationsSetting() {
        settings.update(ALICE, true).block(WAIT);
        for (var project : List.of("项目甲", "项目乙", "项目丙", "项目丁")) {
            relatedSelf(null, project);
        }
        stack.props().getRelations().setMaxRecalled(2);
        assertThat(lines(MemoryTestStack.newConversationId(), "项目甲 项目乙 项目丙 项目丁")).hasSize(2);
    }

    @Test
    void entityNameContainedInTheQuestionMatchesAndSelfHitsOnFirstPerson() {
        settings.update(ALICE, true).block(WAIT);
        relatedSelf(null, "星河项目");
        related(null, "北斗项目", "PROJECT", "使用", "MySQL", "PRODUCT");
        related(null, "天枢", "PROJECT", "位于", "上海", "PLACE");

        // "星河项目进展" contains "星河项目"; first-person "我" hits @self → "用户—负责→星河项目".
        assertThat(lines(MemoryTestStack.newConversationId(), "我负责的星河项目进展怎样？"))
                .containsExactly("用户（SELF）—负责→ 星河项目");
        // Contained name without first person: the project→product edge, not the user-self edge.
        assertThat(lines(MemoryTestStack.newConversationId(), "北斗项目用什么数据库？"))
                .containsExactly("北斗项目（PROJECT）—使用→ MySQL");
        // A short name below the configured minimum is not matched.
        stack.props().getRelations().setEntityMatchMinChars(3);
        assertThat(lines(MemoryTestStack.newConversationId(), "天枢在哪")).isEmpty();
        stack.props().getRelations().setEntityMatchMinChars(2);
        assertThat(lines(MemoryTestStack.newConversationId(), "天枢在哪"))
                .containsExactly("天枢（PROJECT）—位于→ 上海");
    }

    @Test
    void entityScanLimitCapsHowManyEntitiesAreConsidered() {
        settings.update(ALICE, true).block(WAIT);
        // Matching entities first, then many newer unrelated ones. The scan is newest-first, so a small limit
        // never reaches the older matching names.
        related(null, "目标项目", "PROJECT", "使用", "Redis", "PRODUCT");
        // Scan prefers longer names: fillers longer than "目标项目"/"Redis" fill a tiny limit.
        for (int i = 0; i < 5; i++) {
            graph.upsertEntity(ALICE, "超级超级超级无关实体名称" + i, "OTHER").block(WAIT);
        }
        stack.props().getRelations().setEntityScanLimit(5);
        assertThat(lines(MemoryTestStack.newConversationId(), "目标项目 用 Redis 吗")).as("matching entity past the scan")
                .isEmpty();
        stack.props().getRelations().setEntityScanLimit(500);
        assertThat(lines(MemoryTestStack.newConversationId(), "目标项目 用 Redis 吗"))
                .containsExactly("目标项目（PROJECT）—使用→ Redis");
    }

    @Test
    void sameEdgeIsShownOnceNewestAndFormatUsesFullwidthParens() {
        settings.update(ALICE, true).block(WAIT);
        var first = related(null, "星河项目", "PROJECT", "使用", "MySQL", "PRODUCT");
        var second = related(null, "星河项目", "PROJECT", "使用", "MySQL", "PRODUCT");
        assertThat(lines(MemoryTestStack.newConversationId(), "星河项目")).containsExactly(
                "星河项目（PROJECT）—使用→ MySQL");
        assertThat(first.id()).isNotEqualTo(second.id());
    }

    @Test
    void relationLinesCountInTheRecallTokenBudget() {
        settings.update(ALICE, true).block(WAIT);
        relatedSelf(null, "星河项目");
        var conversation = MemoryTestStack.newConversationId();
        // Seed a long recent turn so history and relations compete for the same small window.
        stack.fixture().jdbc().update("INSERT INTO sessions (id, tenant_id, user_id, title, revision, created_at,"
                        + " updated_at) VALUES (?,?,?,'t',0,CURRENT_TIMESTAMP(6),CURRENT_TIMESTAMP(6))",
                conversation, ALICE.tenantId(), ALICE.subject());
        var longText = "历史轮次占位文字".repeat(80);
        var ticket = stack.accept(ALICE, conversation, MemoryTestStack.newConversationId(), longText).ticket();
        stack.finalizer().finish(ticket, TurnStatus.COMPLETED, longText).block(WAIT);

        var ask = stack.accept(ALICE, conversation, MemoryTestStack.newConversationId(), "我负责哪些项目？").ticket();
        try {
            var recalled = retriever.recall(ask, "我负责哪些项目？").block(WAIT);
            assertThat(recalled.relationLines()).isNotEmpty();
            var budget = new ContextBudget(stack.props());
            var assembler = new ContextAssembler(stack.history(), budget, stack.metrics(), retriever);
            // Tiny window: fixed parts + relation message leave almost no room for the long history turn.
            var window = new ModelWindow(400, 50);
            var ctx = assembler.assemble(ask, ContextPurpose.DIRECT_ANSWER, window,
                    List.of(LlmClient.Message.system("sys")), "我负责哪些项目？", Mono.just(recalled)).block(WAIT);
            var joined = ctx.messages().stream().map(LlmClient.Message::content).reduce("", (a, b) -> a + "\n" + b);
            assertThat(joined).contains(ContextAssembler.RELATION_HEADER).contains("用户（SELF）—负责→ 星河项目");
            long relationRaw = TokenEstimator.estimate(List.of(LlmClient.Message.user(
                    ContextAssembler.RELATION_HEADER + "\n1. 用户（SELF）—负责→ 星河项目")));
            assertThat(relationRaw).isPositive();
            // History of the long turn must have been squeezed: the assembled estimate stays within the window.
            assertThat(ctx.estimatedTokens()).isLessThanOrEqualTo(ctx.budgetTokens());
        } finally {
            stack.finalizer().finish(ask, TurnStatus.FAILED, "").block(WAIT);
        }
    }

    @Test
    void recallingARelationDoesNotRefreshTheItemsLastRecalledAt() {
        settings.update(ALICE, true).block(WAIT);
        var item = relatedSelf(null, "星河项目");
        stack.fixture().jdbc().update("UPDATE memory_items SET last_recalled_at = TIMESTAMP '2020-01-01 00:00:00'"
                + " WHERE id = ?", item.id());
        lines(MemoryTestStack.newConversationId(), "我负责星河项目吗？");
        assertThat(stack.fixture().jdbc().queryForObject("SELECT CAST(last_recalled_at AS VARCHAR) FROM memory_items"
                + " WHERE id = ?", String.class, item.id())).startsWith("2020-01-01");
    }
}
