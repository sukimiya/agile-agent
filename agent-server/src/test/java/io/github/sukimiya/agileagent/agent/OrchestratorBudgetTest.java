package io.github.sukimiya.agileagent.agent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Function;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.web.reactive.BindingContext;
import org.springframework.web.reactive.result.method.HandlerMethodArgumentResolver;
import org.springframework.web.server.ServerWebExchange;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.github.sukimiya.agileagent.capability.CapabilityRegistry;
import io.github.sukimiya.agileagent.controller.ChatController;
import io.github.sukimiya.agileagent.memory.MemoryMaintenance;
import io.github.sukimiya.agileagent.memory.MemoryModel.AcceptedTurn;
import io.github.sukimiya.agileagent.memory.MemoryTestStack;
import io.github.sukimiya.agileagent.memory.context.ContextAssembler;
import io.github.sukimiya.agileagent.memory.context.ContextBudget;
import io.github.sukimiya.agileagent.service.AuthSessionStore.AuthUser;
import io.github.sukimiya.agileagent.service.LlmClient;
import io.github.sukimiya.agileagent.service.TenantLlmClientResolver;
import io.github.sukimiya.agileagent.service.TenantLlmClientResolver.ResolvedModel;
import io.github.sukimiya.agileagent.spi.capability.AgentCapabilityProvider;
import io.github.sukimiya.agileagent.spi.capability.AgentExecutionContext;
import io.github.sukimiya.agileagent.spi.capability.CapabilityDefinition;
import io.github.sukimiya.agileagent.spi.capability.ToolDefinition;
import io.github.sukimiya.agileagent.spi.capability.ToolInvocation;
import io.github.sukimiya.agileagent.spi.capability.ToolResult;
import io.github.sukimiya.agileagent.spi.identity.IdentityContext;
import io.github.sukimiya.agileagent.web.AuthExceptionHandler;
import io.github.sukimiya.agileagent.web.CurrentUser;
import io.github.sukimiya.agileagent.web.MemoryExceptionHandler;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Budget enforcement across one turn: the pre-accept check (413 before anything is written), tool results that
 * would overflow the capability call (placeholder for the model, full text in the tool record) and the recheck
 * after every tool round.
 */
class OrchestratorBudgetTest {

    private static final Duration WAIT = Duration.ofSeconds(10);

    private final MemoryTestStack stack = MemoryTestStack.h2();
    private final String conversation = MemoryTestStack.newConversationId();
    /** Every model call, in order: the messages it was sent. */
    private final List<List<LlmClient.Message>> calls = new CopyOnWriteArrayList<>();
    /** The max_tokens each model call asked for, in call order ({@code null}: not sent). */
    private final List<Integer> maxTokens = new CopyOnWriteArrayList<>();
    private Function<List<LlmClient.Message>, Flux<LlmClient.LlmEvent>> script;
    private final LlmClient llm = new LlmClient() {
        @Override
        public Flux<LlmEvent> chatStream(List<Message> messages, List<ToolDefinition> tools) {
            return chatStream(messages, tools, new CallOptions(null));
        }

        @Override
        public Flux<LlmEvent> chatStream(List<Message> messages, List<ToolDefinition> tools, CallOptions options) {
            return Flux.defer(() -> {
                calls.add(List.copyOf(messages));
                maxTokens.add(options.maxTokens());
                return script.apply(messages);
            });
        }
    };

    @AfterEach
    void tearDown() {
        stack.close();
    }

    private ContextBudget budget() {
        return new ContextBudget(stack.props());
    }

    private Orchestrator orchestrator(AgentCapabilityProvider provider) {
        var registry = new CapabilityRegistry(List.of(provider));
        var dispatcher = new InProcessTaskDispatcher(new DomainAgent(registry, stack.toolRecorder(), budget()));
        return new Orchestrator(dispatcher, registry, stack.lifecycle(), stack.history(),
                new ContextAssembler(stack.history(), budget(), stack.metrics()));
    }

    private static String systemOf(List<LlmClient.Message> messages) {
        return messages.getFirst().content();
    }

    /** Routing picks the capability; the capability calls the tool {@code rounds} times, then answers. */
    private void scriptToolRounds(int rounds) {
        script = messages -> {
            var system = systemOf(messages);
            if (system.contains("路由模块")) {
                return Flux.just(new LlmClient.LlmEvent.Completed("[\"DATA\"]"));
            }
            if (system.equals("data tools")) {
                long toolResults = messages.stream().filter(m -> "tool".equals(m.role())).count();
                return toolResults < rounds
                        ? Flux.just(new LlmClient.LlmEvent.ToolCallsReady(List.of(
                                new LlmClient.ToolCall("call-" + (toolResults + 1), "fetch", "{}"))))
                        : Flux.just(new LlmClient.LlmEvent.Completed("数据已取回"));
            }
            return Flux.just(new LlmClient.LlmEvent.ContentDelta("好的"), new LlmClient.LlmEvent.Completed("好的"));
        };
    }

    private List<LlmClient.Message> toolMessagesOfLastCapabilityCall() {
        return calls.stream().filter(c -> systemOf(c).equals("data tools")).reduce((a, b) -> b).orElseThrow()
                .stream().filter(m -> "tool".equals(m.role())).toList();
    }

    private List<String> run(Orchestrator orchestrator, String question, int window, int output) {
        AcceptedTurn accepted = stack.accept(conversation, question);
        return orchestrator.chat(accepted, "tok", null, new ResolvedModel(llm, window, output)).collectList()
                .block(WAIT);
    }

    @Test
    void oversizedToolResultReplacedWithPlaceholderButStoredInFull() {
        var full = "行".repeat(5_000);
        scriptToolRounds(1);

        var lines = run(orchestrator(new DataTools(full)), "查数据", 3_000, 1_000);

        assertThat(lines).anyMatch(l -> l.contains("\"done\"")).noneMatch(l -> l.contains("\"error\""));
        var toolMessage = toolMessagesOfLastCapabilityCall().getFirst();
        assertThat(toolMessage.toolCallId()).isEqualTo("call-1");
        assertThat(toolMessage.content()).contains("结果过长").contains("call-1").doesNotContain("行行");
        var turnId = stack.rows("SELECT id FROM chat_turns WHERE conversation_id = ?", conversation).getFirst()
                .get("ID");
        assertThat(stack.toolEventRows((String) turnId)).singleElement()
                .satisfies(row -> assertThat(row.get("RESULT")).isEqualTo(full));
        // Every call stayed within its input budget.
        for (var call : calls) {
            assertThat(budget().estimate(call)).isLessThanOrEqualTo(2_000);
        }
    }

    @Test
    void everyModelCallSendsTheEffectiveOutputReserveAsMaxTokens() {
        scriptToolRounds(1);

        run(orchestrator(new DataTools("ok")), "查数据", 3_000, 1_000);

        // routing, capability round 1, capability round 2, aggregate
        assertThat(calls).hasSize(4);
        assertThat(maxTokens).hasSize(4).containsOnly(1_000);
    }

    @Test
    void fallbackReserveIsWhatGetsSentWhenThePresetReserveDoesNotFit() {
        scriptToolRounds(0);

        // 4,096 window with the default 4,096 reserve: the budget uses a quarter of the window instead.
        AcceptedTurn accepted = stack.accept(conversation, "你好");
        orchestrator(new DataTools("ok")).chat(accepted, "tok", null, new ResolvedModel(llm, 4_096, null))
                .collectList().block(WAIT);

        assertThat(maxTokens).isNotEmpty().containsOnly(1_024);
    }

    @Test
    void disabledBudgetSendsNoMaxTokens() {
        try (var disabled = MemoryTestStack.h2(p -> p.getContext().setEnabled(false))) {
            scriptToolRounds(1);
            var registry = new CapabilityRegistry(List.of(new DataTools("ok")));
            var budget = new ContextBudget(disabled.props());
            var orchestrator = new Orchestrator(
                    new InProcessTaskDispatcher(new DomainAgent(registry, disabled.toolRecorder(), budget)),
                    registry, disabled.lifecycle(), disabled.history(),
                    new ContextAssembler(disabled.history(), budget, disabled.metrics()));
            var conv = MemoryTestStack.newConversationId();
            orchestrator.chat(disabled.accept(conv, "查数据"), "tok", null, new ResolvedModel(llm, 3_000, 1_000))
                    .collectList().block(WAIT);
        }

        assertThat(maxTokens).hasSize(4).containsOnlyNulls();
    }

    @Test
    void budgetRecheckedAfterEachToolRound() {
        // Each result alone fits (1,000 x1.3 = 1,300 of 2,000), but not on top of the first one.
        scriptToolRounds(2);

        run(orchestrator(new DataTools("段".repeat(1_000))), "查两次", 3_000, 1_000);

        var tools = toolMessagesOfLastCapabilityCall();
        assertThat(tools).hasSize(2);
        assertThat(tools.get(0).content()).isEqualTo("段".repeat(1_000));
        assertThat(tools.get(1).content()).contains("结果过长").contains("call-2");
        for (var call : calls) {
            assertThat(budget().estimate(call)).isLessThanOrEqualTo(2_000);
        }
    }

    @Test
    void oversizedCapabilityAnswerIsOmittedFromAggregate() {
        script = messages -> {
            var system = systemOf(messages);
            if (system.contains("路由模块")) {
                return Flux.just(new LlmClient.LlmEvent.Completed("[\"DATA\"]"));
            }
            if (system.equals("data tools")) {
                return Flux.just(new LlmClient.LlmEvent.Completed("答".repeat(3_000)));
            }
            return Flux.just(new LlmClient.LlmEvent.Completed("汇总完成"));
        };

        var lines = run(orchestrator(new DataTools("unused")), "汇总", 3_000, 1_000);

        assertThat(lines).anyMatch(l -> l.contains("汇总完成"));
        var aggregate = calls.getLast();
        assertThat(aggregate.getLast().content()).contains("DATA").contains("结果过长").doesNotContain("答答");
        assertThat(budget().estimate(aggregate)).isLessThanOrEqualTo(2_000);
    }

    @Test
    void oversizedCurrentMessageReturns413BeforeAccept() {
        var resolver = mock(TenantLlmClientResolver.class);
        when(resolver.resolveModel("tenant-1")).thenReturn(Mono.just(new ResolvedModel(llm, 2_000, 1_000)));
        var controller = new ChatController(orchestrator(new DataTools("x")), stack.sessionService(),
                stack.lifecycle(), stack.history(), resolver, mock(MemoryMaintenance.class));
        var client = WebTestClient.bindToController(controller)
                .controllerAdvice(new MemoryExceptionHandler(), new AuthExceptionHandler())
                .argumentResolvers(c -> c.addCustomResolver(new FixedUser()))
                .configureClient().responseTimeout(WAIT)
                .build();
        var longText = "长".repeat(800); // 800 x1.3 = 1,040 > 1,000 even before the routing prompt

        for (var request : List.of(
                client.post().uri("/api/chat").contentType(MediaType.APPLICATION_JSON)
                        .bodyValue("{\"conversationId\":\"" + conversation + "\",\"message\":\"" + longText + "\"}"),
                client.post().uri("/api/chat/completions").contentType(MediaType.APPLICATION_JSON)
                        .bodyValue("{\"messages\":[{\"role\":\"user\",\"content\":\"" + longText + "\"}]}"))) {
            request.exchange()
                    .expectStatus().isEqualTo(413)
                    .expectBody().jsonPath("$.code").isEqualTo("CONTEXT_BUDGET_EXCEEDED");
        }

        assertThat(calls).isEmpty();
        assertThat(stack.rows("SELECT COUNT(*) AS n FROM chat_turns").getFirst().get("N")).isEqualTo(0L);
        assertThat(stack.rows("SELECT COUNT(*) AS n FROM chat_messages").getFirst().get("N")).isEqualTo(0L);
        assertThat(stack.rows("SELECT COUNT(*) AS n FROM sessions").getFirst().get("N")).isEqualTo(0L);

        // A message that fits is accepted and answered as before.
        script = messages -> Flux.just(new LlmClient.LlmEvent.ContentDelta("好"), new LlmClient.LlmEvent.Completed("好"));
        client.post().uri("/api/chat").contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"conversationId\":\"" + conversation + "\",\"message\":\"你好\"}")
                .exchange().expectStatus().isOk();
    }

    private static final IdentityContext IDENTITY = new IdentityContext("alice", "Alice", "tenant-1", "Tenant", null,
            null, Set.of(), Set.of(), Set.of(), false, false);

    private static final class FixedUser implements HandlerMethodArgumentResolver {
        @Override
        public boolean supportsParameter(MethodParameter parameter) {
            return parameter.hasParameterAnnotation(CurrentUser.class);
        }

        @Override
        public Mono<Object> resolveArgument(MethodParameter parameter, BindingContext context,
                ServerWebExchange exchange) {
            return Mono.just(new AuthUser("alice", "Alice", "access-token", "refresh",
                    Instant.now().plusSeconds(3600), IDENTITY, Instant.now()));
        }
    }

    private static final class DataTools implements AgentCapabilityProvider {
        private final String result;

        DataTools(String result) {
            this.result = result;
        }

        @Override public String code() { return "DATA"; }
        @Override public String displayName() { return "Data"; }
        @Override public String routingDescription() { return "data lookup"; }

        @Override
        public Mono<CapabilityDefinition> definition(AgentExecutionContext context) {
            var schema = new ObjectMapper().createObjectNode().put("type", "object");
            return Mono.just(new CapabilityDefinition("data tools",
                    List.of(new ToolDefinition("fetch", "fetch data", schema))));
        }

        @Override
        public Mono<ToolResult> execute(ToolInvocation invocation, AgentExecutionContext context) {
            return Mono.just(new ToolResult(true, result));
        }
    }
}
