package io.github.sukimiya.agileagent.agent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.github.sukimiya.agileagent.capability.CapabilityRegistry;
import io.github.sukimiya.agileagent.memory.MemoryModel.AcceptedTurn;
import io.github.sukimiya.agileagent.memory.MemoryTestStack;
import io.github.sukimiya.agileagent.service.LlmClient;
import io.github.sukimiya.agileagent.spi.capability.AgentCapabilityProvider;
import io.github.sukimiya.agileagent.spi.capability.AgentExecutionContext;
import io.github.sukimiya.agileagent.spi.capability.CapabilityDefinition;
import io.github.sukimiya.agileagent.spi.capability.ToolDefinition;
import io.github.sukimiya.agileagent.spi.capability.ToolInvocation;
import io.github.sukimiya.agileagent.spi.capability.ToolResult;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Client disconnect during the capability branch must cancel the dispatch upstream: no further tool calls,
 * no aggregate model call, and the turn ends CANCELLED. A shared {@code cache()} would keep the dispatch running
 * after the subscriber left.
 */
class OrchestratorCapabilityCancelTest {

    private static final Duration SETTLE = Duration.ofMillis(800);

    private final MemoryTestStack stack = MemoryTestStack.h2();
    private final LlmClient llm = mock(LlmClient.class);
    private final String conversation = MemoryTestStack.newConversationId();

    @AfterEach
    void tearDown() {
        stack.close();
    }

    private Orchestrator orchestrator(TaskDispatcher dispatcher, AgentCapabilityProvider provider) {
        return new Orchestrator(dispatcher, new CapabilityRegistry(List.of(provider)), stack.lifecycle(),
                stack.history());
    }

    /** Consumes the stream until the first tool_call line, then disconnects (cancels). */
    private static List<String> disconnectAfterFirstToolCall(Flux<String> stream) {
        return stream.takeUntil(line -> line.contains("\"type\":\"tool_call\"")).collectList()
                .block(Duration.ofSeconds(10));
    }

    @Test
    void capabilityBranchDisconnectCancelsDispatchUpstream() {
        var cancelled = new AtomicBoolean();
        var dispatcher = mock(TaskDispatcher.class);
        when(dispatcher.dispatch(anyList(), same(llm))).thenReturn(Flux.<AgentEvent>concat(
                        Flux.just(new AgentEvent.ToolCalled("SLOW", "slowTool", "{}")), Flux.never())
                .doOnCancel(() -> cancelled.set(true)));
        when(llm.chatStream(any(), any())).thenReturn(Flux.just(new LlmClient.LlmEvent.Completed("[\"SLOW\"]")));
        var accepted = stack.accept(conversation, "查一下");

        var lines = disconnectAfterFirstToolCall(orchestrator(dispatcher, new SlowCapability(new AtomicInteger()))
                .chat(accepted, "tok", null, llm));

        assertThat(lines.getLast()).contains("tool_call");
        assertThat(cancelled).isTrue();
    }

    @Test
    void capabilityBranchDisconnectSkipsNotStartedTools() throws Exception {
        var executions = new AtomicInteger();
        var provider = new SlowCapability(executions);
        var dispatcher = new InProcessTaskDispatcher(new DomainAgent(new CapabilityRegistry(List.of(provider)),
                stack.toolRecorder()));
        when(llm.chatStream(any(), any()))
                .thenReturn(Flux.just(new LlmClient.LlmEvent.Completed("[\"SLOW\"]")))
                .thenReturn(Flux.just(new LlmClient.LlmEvent.ToolCallsReady(List.of(
                        new LlmClient.ToolCall("c1", "slowTool", "{}"),
                        new LlmClient.ToolCall("c2", "slowTool", "{}"),
                        new LlmClient.ToolCall("c3", "slowTool", "{}")))))
                .thenReturn(Flux.just(new LlmClient.LlmEvent.Completed("never")));
        var accepted = stack.accept(conversation, "查一下");

        disconnectAfterFirstToolCall(orchestrator(dispatcher, provider).chat(accepted, "tok", null, llm));
        Thread.sleep(SETTLE.toMillis());

        assertThat(executions.get()).isLessThanOrEqualTo(1);
        verify(llm, times(2)).chatStream(any(), any());
    }

    @Test
    void capabilityBranchDisconnectStartsNoAggregateModelCall() throws Exception {
        var dispatcher = mock(TaskDispatcher.class);
        when(dispatcher.dispatch(anyList(), same(llm))).thenReturn(Flux.<AgentEvent>concat(
                Flux.just(new AgentEvent.ToolCalled("SLOW", "slowTool", "{}")),
                Mono.delay(Duration.ofMillis(200)).thenReturn(new AgentEvent.Finished("SLOW", "结果"))));
        when(llm.chatStream(any(), any()))
                .thenReturn(Flux.just(new LlmClient.LlmEvent.Completed("[\"SLOW\"]")))
                .thenReturn(Flux.just(new LlmClient.LlmEvent.Completed("aggregate")));
        var accepted = stack.accept(conversation, "查一下");

        disconnectAfterFirstToolCall(orchestrator(dispatcher, new SlowCapability(new AtomicInteger()))
                .chat(accepted, "tok", null, llm));
        Thread.sleep(SETTLE.toMillis());

        verify(llm, times(1)).chatStream(any(), any());
    }

    @Test
    void capabilityBranchDisconnectMarksCancelled() {
        var dispatcher = mock(TaskDispatcher.class);
        when(dispatcher.dispatch(anyList(), same(llm))).thenReturn(Flux.<AgentEvent>concat(
                Flux.just(new AgentEvent.ToolCalled("SLOW", "slowTool", "{}")), Flux.never()));
        when(llm.chatStream(any(), any())).thenReturn(Flux.just(new LlmClient.LlmEvent.Completed("[\"SLOW\"]")));
        AcceptedTurn accepted = stack.accept(conversation, "查一下");

        disconnectAfterFirstToolCall(orchestrator(dispatcher, new SlowCapability(new AtomicInteger()))
                .chat(accepted, "tok", null, llm));

        await().atMost(Duration.ofSeconds(5)).untilAsserted(() ->
                assertThat(stack.status(accepted.ticket().turnId())).isEqualTo("CANCELLED"));
        assertThat(stack.activeTurn(conversation)).isNull();
    }

    /** One tool whose every execution takes 300 ms, counting how many executions started. */
    private record SlowCapability(AtomicInteger executions) implements AgentCapabilityProvider {

        @Override public String code() { return "SLOW"; }
        @Override public String displayName() { return "Slow"; }
        @Override public String routingDescription() { return "slow test capability"; }

        @Override
        public Mono<CapabilityDefinition> definition(AgentExecutionContext context) {
            var schema = new ObjectMapper().valueToTree(Map.of("type", "object"));
            return Mono.just(new CapabilityDefinition("slow",
                    List.of(new ToolDefinition("slowTool", "slow", (com.fasterxml.jackson.databind.JsonNode) schema))));
        }

        @Override
        public Mono<ToolResult> execute(ToolInvocation invocation, AgentExecutionContext context) {
            return Mono.defer(() -> {
                executions.incrementAndGet();
                return Mono.delay(Duration.ofMillis(300)).thenReturn(new ToolResult(true, "done"));
            });
        }
    }
}
