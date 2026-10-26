package io.github.sukimiya.agileagent.memory.items;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

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

class EntityGraphRepositoryTest {

    static final Duration WAIT = Duration.ofSeconds(15);
    static final MemoryScope ALICE = MemoryTestStack.ALICE;

    MemoryTestFixture fixture;
    EntityGraphRepository graph;

    protected MemoryTestFixture newFixture() {
        return MemoryTestFixture.h2();
    }

    @BeforeEach
    void setUp() {
        fixture = newFixture();
        graph = new EntityGraphRepository(fixture.databaseClient());
    }

    @Test
    void supersededRelationNotUsedAsCurrentFact() {
        var a = graph.upsertEntity(ALICE, "项目A", "PROJECT").block(WAIT);
        var b = graph.upsertEntity(ALICE, "方案B", "DECISION").block(WAIT);
        var old = graph.saveRelation(ALICE, a.id(), "USES", b.id(), null, "ACTIVE").block(WAIT);
        graph.supersedeRelation(ALICE, old.id()).block(WAIT);
        var c = graph.upsertEntity(ALICE, "方案C", "DECISION").block(WAIT);
        graph.saveRelation(ALICE, a.id(), "USES", c.id(), null, "ACTIVE").block(WAIT);

        var hops = graph.oneHop(ALICE, List.of("项目A"), 10).collectList().block(WAIT);
        assertThat(hops).hasSize(1);
        assertThat(hops.getFirst().toEntityId()).isEqualTo(c.id());
        assertThat(hops).noneMatch(r -> r.id().equals(old.id()));
    }

    @Test
    void oneHopRelationLimited() {
        var user = graph.upsertEntity(ALICE, "用户", "PERSON").block(WAIT);
        for (int i = 0; i < 15; i++) {
            var other = graph.upsertEntity(ALICE, "项目" + i, "PROJECT").block(WAIT);
            graph.saveRelation(ALICE, user.id(), "OWNS", other.id(), null, "ACTIVE").block(WAIT);
        }
        var hops = graph.oneHop(ALICE, List.of("用户"), 5).collectList().block(WAIT);
        assertThat(hops).hasSize(5);
    }

    @Test
    void onlyRelevantSubgraphInjected() {
        var a = graph.upsertEntity(ALICE, "项目甲", "PROJECT").block(WAIT);
        var b = graph.upsertEntity(ALICE, "方案乙", "DECISION").block(WAIT);
        graph.saveRelation(ALICE, a.id(), "USES", b.id(), null, "ACTIVE").block(WAIT);
        var x = graph.upsertEntity(ALICE, "无关实体", "OTHER").block(WAIT);
        var y = graph.upsertEntity(ALICE, "另一无关", "OTHER").block(WAIT);
        graph.saveRelation(ALICE, x.id(), "LINKS", y.id(), null, "ACTIVE").block(WAIT);

        var hops = graph.oneHop(ALICE, List.of("项目甲"), 10).collectList().block(WAIT);
        assertThat(hops).hasSize(1);
        assertThat(hops.getFirst().fromEntityId()).isEqualTo(a.id());

        // Assembler injects only relation lines from RecallResult — not the full graph.
        var recalled = new RecallResult(List.of(), false, Duration.ZERO, null,
                List.of("项目甲 —USES→ 方案乙"));
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
            assertThat(joined).contains("项目甲 —USES→ 方案乙");
            assertThat(joined).doesNotContain("无关实体");
        }
    }
}
