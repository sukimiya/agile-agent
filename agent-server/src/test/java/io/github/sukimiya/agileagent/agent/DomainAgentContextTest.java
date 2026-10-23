package io.github.sukimiya.agileagent.agent;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import java.lang.reflect.RecordComponent;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.github.sukimiya.agileagent.capability.CapabilityRegistry;
import io.github.sukimiya.agileagent.memory.MemoryModel.TurnStatus;
import io.github.sukimiya.agileagent.memory.MemoryTestStack;
import io.github.sukimiya.agileagent.memory.context.ContextAssembler;
import io.github.sukimiya.agileagent.memory.context.ContextBudget;
import io.github.sukimiya.agileagent.service.LlmClient;
import io.github.sukimiya.agileagent.service.TenantLlmClientResolver.ResolvedModel;
import io.github.sukimiya.agileagent.spi.capability.AgentCapabilityProvider;
import io.github.sukimiya.agileagent.spi.capability.AgentExecutionContext;
import io.github.sukimiya.agileagent.spi.capability.CapabilityDefinition;
import io.github.sukimiya.agileagent.spi.capability.ToolDefinition;
import io.github.sukimiya.agileagent.spi.capability.ToolInvocation;
import io.github.sukimiya.agileagent.spi.capability.ToolResult;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * M2a Task 3: a domain agent gets the recent conversation as background, in the order
 * {@code system(definition) -> background -> user(question)}, sized to its own budget (system prompt, question and
 * tool schemas charged first, background limited to the recent-window share so tool results keep room).
 */
class DomainAgentContextTest {

    private static final Duration WAIT = Duration.ofSeconds(10);
    private static final String CAPABILITY_PROMPT = "data tools";

    private MemoryTestStack stack = MemoryTestStack.h2();
    private final String conversation = MemoryTestStack.newConversationId();
    /** Capability calls only, in order: the messages and tool definitions they were sent. */
    private final List<List<LlmClient.Message>> capabilityCalls = new CopyOnWriteArrayList<>();
    private final List<List<LlmClient.ToolDefinition>> capabilityTools = new CopyOnWriteArrayList<>();
    private Function<List<LlmClient.Message>, Flux<LlmClient.LlmEvent>> capabilityScript =
            messages -> Flux.just(new LlmClient.LlmEvent.Completed("能力已回答"));
    private final LlmClient llm = (messages, tools) -> Flux.defer(() -> {
        var system = messages.getFirst().content();
        if (system.contains("路由模块")) {
            return Flux.just(new LlmClient.LlmEvent.Completed("[\"DATA\"]"));
        }
        if (system.equals(CAPABILITY_PROMPT)) {
            capabilityCalls.add(List.copyOf(messages));
            capabilityTools.add(List.copyOf(tools));
            return capabilityScript.apply(messages);
        }
        return Flux.just(new LlmClient.LlmEvent.ContentDelta("好的"), new LlmClient.LlmEvent.Completed("好的"));
    });

    @AfterEach
    void tearDown() {
        stack.close();
    }

    private Orchestrator orchestrator(AgentCapabilityProvider provider) {
        var registry = new CapabilityRegistry(List.of(provider));
        var budget = new ContextBudget(stack.props());
        var dispatcher = new InProcessTaskDispatcher(
                new DomainAgent(registry, stack.toolRecorder(), budget, stack.metrics()));
        return new Orchestrator(dispatcher, registry, stack.lifecycle(), stack.history(),
                new ContextAssembler(stack.history(), budget, stack.metrics()));
    }

    private void completedTurn(String question, String answer) {
        var ticket = stack.accept(conversation, question).ticket();
        assertThat(stack.finalizer().finish(ticket, TurnStatus.COMPLETED, answer).block(WAIT)).isTrue();
    }

    private List<String> run(AgentCapabilityProvider provider, String question, Integer window, Integer output) {
        var accepted = stack.accept(conversation, question);
        return orchestrator(provider).chat(accepted, "tok", null, new ResolvedModel(llm, window, output))
                .collectList().block(WAIT);
    }

    private static List<String> rolesOf(List<LlmClient.Message> messages) {
        return messages.stream().map(LlmClient.Message::role).toList();
    }

    @Test
    void capabilityReceivesRecentReferenceForFollowUp() {
        completedTurn("列出我名下的车辆", "第一辆车牌 A7-3921，第二辆车牌 W-5518");

        run(new DataTools("unused"), "那第二个呢", null, null);

        var call = capabilityCalls.getFirst();
        assertThat(rolesOf(call)).containsExactly("system", "user", "assistant", "user");
        assertThat(call.get(0).content()).isEqualTo(CAPABILITY_PROMPT);
        assertThat(call.get(1).content()).isEqualTo("列出我名下的车辆");
        assertThat(call.get(2).content()).contains("W-5518");
        assertThat(call.get(3).content()).isEqualTo("那第二个呢");
    }

    @Test
    void backgroundFitsCapabilityBudgetWithToolSchemas() {
        for (int i = 0; i < 6; i++) {
            completedTurn("旧问题" + i + "甲".repeat(200), "旧回答" + i + "乙".repeat(200));
        }
        completedTurn("最近的问题", "最近的回答");
        // 1,500-char tool description: about 1,950 budgeted tokens of the 5,000 input budget.
        var provider = new DataTools("unused", "说".repeat(1_500));
        var budget = new ContextBudget(stack.props());

        run(provider, "现在的问题", 6_000, 1_000);

        var call = capabilityCalls.getFirst();
        var schema = new LlmClient.Message("system", new ObjectMapper().valueToTree(capabilityTools.getFirst())
                .toString(), null, null);
        var fixed = List.of(call.getFirst(), call.getLast(), schema);
        var background = call.subList(1, call.size() - 1);
        var withSchema = new java.util.ArrayList<>(call);
        withSchema.add(schema);
        assertThat(budget.estimate(withSchema)).isLessThanOrEqualTo(5_000);
        // Background is whole turns, newest kept, oldest dropped.
        assertThat(background).isNotEmpty();
        assertThat(background.getFirst().role()).isEqualTo("user");
        assertThat(background.getLast().content()).isEqualTo("最近的回答");
        assertThat(call).noneMatch(m -> m.content() != null && m.content().startsWith("旧问题0"));
        // At most the recent-window share of what the fixed parts leave (D5: 50%), unless within the 2-turn floor.
        int remaining = 5_000 - budget.estimate(fixed);
        if (background.size() > 4) {
            assertThat(budget.estimate(withSchema) - budget.estimate(fixed)).isLessThanOrEqualTo(remaining / 2 + 2);
        }
        assertThat(stack.meters().find("memory.context.history.truncated").tag("purpose", "CAPABILITY").counter())
                .isNotNull()
                .satisfies(c -> assertThat(c.count()).isEqualTo(1.0));
    }

    @Test
    void backgroundNeverContainsCurrentTurn() {
        completedTurn("上一轮问题", "上一轮回答");
        capabilityScript = messages -> messages.stream().anyMatch(m -> "tool".equals(m.role()))
                ? Flux.just(new LlmClient.LlmEvent.Completed("取到了"))
                : Flux.just(new LlmClient.LlmEvent.ToolCallsReady(List.of(
                        new LlmClient.ToolCall("call-1", "fetch", "{}"))));

        run(new DataTools("工具结果"), "本轮唯一问题", null, null);

        assertThat(capabilityCalls).hasSize(2);
        for (var call : capabilityCalls) {
            assertThat(call).filteredOn(m -> "本轮唯一问题".equals(m.content())).hasSize(1);
            var currentQuestion = call.stream().map(LlmClient.Message::content).toList().indexOf("本轮唯一问题");
            // Background sits strictly before the current question; only this turn's tool exchange follows it.
            assertThat(call.subList(1, currentQuestion)).extracting(LlmClient.Message::content)
                    .containsExactly("上一轮问题", "上一轮回答");
            assertThat(call.subList(currentQuestion + 1, call.size()))
                    .allSatisfy(m -> assertThat(m.role()).isIn("assistant", "tool"));
        }
    }

    @Test
    void providerSpiSignatureUnchanged() {
        assertThat(Arrays.stream(AgentCapabilityProvider.class.getDeclaredMethods())
                .map(DomainAgentContextTest::signature).sorted().toList())
                .containsExactly(
                        "code()",
                        "definition(AgentExecutionContext)",
                        "displayName()",
                        "execute(ToolInvocation,AgentExecutionContext)",
                        "routingDescription()");
        assertThat(Arrays.stream(AgentExecutionContext.class.getRecordComponents())
                .map(RecordComponent::getName).toList())
                .containsExactly("identity", "conversationId", "accessTokenAccessor");
    }

    private static String signature(Method method) {
        return method.getName() + "(" + String.join(",", Arrays.stream(method.getParameterTypes())
                .map(Class::getSimpleName).toList()) + ")";
    }

    @Test
    void backgroundExcludedWhenContextDisabled() {
        stack.close();
        stack = MemoryTestStack.h2(p -> p.getContext().setEnabled(false));
        completedTurn("列出我名下的车辆", "第一辆车牌 A7-3921，第二辆车牌 W-5518");

        run(new DataTools("unused"), "那第二个呢", null, null);

        assertThat(rolesOf(capabilityCalls.getFirst())).containsExactly("system", "user");
        assertThat(capabilityCalls.getFirst().getLast().content()).isEqualTo("那第二个呢");
    }

    @Test
    void disconnectStillCancelsCapabilityBranch() {
        completedTurn("上一轮问题", "上一轮回答");
        var subscribed = new AtomicBoolean();
        var cancelled = new AtomicBoolean();
        // The capability's model call hangs; the client leaves while it is in flight.
        capabilityScript = messages -> Flux.<LlmClient.LlmEvent>never()
                .doOnSubscribe(s -> subscribed.set(true))
                .doOnCancel(() -> cancelled.set(true));
        var accepted = stack.accept(conversation, "查数据");

        var subscription = orchestrator(new DataTools("unused"))
                .chat(accepted, "tok", null, new ResolvedModel(llm, null, null)).subscribe();
        awaitTrue(subscribed);
        subscription.dispose();

        awaitTrue(cancelled);
        assertThat(capabilityCalls.getFirst()).extracting(LlmClient.Message::content).contains("上一轮回答");
    }

    private static void awaitTrue(AtomicBoolean flag) {
        long deadline = System.nanoTime() + WAIT.toNanos();
        while (!flag.get() && System.nanoTime() < deadline) {
            Thread.onSpinWait();
        }
        assertThat(flag).isTrue();
    }

    private static class DataTools implements AgentCapabilityProvider {
        private final String result;
        private final String toolDescription;

        DataTools(String result) {
            this(result, "fetch data");
        }

        DataTools(String result, String toolDescription) {
            this.result = result;
            this.toolDescription = toolDescription;
        }

        @Override public String code() { return "DATA"; }
        @Override public String displayName() { return "Data"; }
        @Override public String routingDescription() { return "data lookup"; }

        @Override
        public Mono<CapabilityDefinition> definition(AgentExecutionContext context) {
            var schema = new ObjectMapper().createObjectNode().put("type", "object");
            return Mono.just(new CapabilityDefinition(CAPABILITY_PROMPT,
                    List.of(new ToolDefinition("fetch", toolDescription, schema))));
        }

        @Override
        public Mono<ToolResult> execute(ToolInvocation invocation, AgentExecutionContext context) {
            return Mono.just(new ToolResult(true, result));
        }
    }
}
