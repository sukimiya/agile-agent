package io.github.sukimiya.agileagent.agent;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.sukimiya.agileagent.capability.CapabilityRegistry;
import io.github.sukimiya.agileagent.knowledge.KnowledgeCapabilityProvider;
import io.github.sukimiya.agileagent.knowledge.KnowledgeCatalog;
import io.github.sukimiya.agileagent.service.LlmClient;
import io.github.sukimiya.agileagent.spi.capability.AgentCapabilityProvider;
import io.github.sukimiya.agileagent.spi.capability.AgentExecutionContext;
import io.github.sukimiya.agileagent.spi.capability.CapabilityDefinition;
import io.github.sukimiya.agileagent.spi.capability.ToolDefinition;
import io.github.sukimiya.agileagent.spi.capability.ToolInvocation;
import io.github.sukimiya.agileagent.spi.capability.ToolResult;
import io.github.sukimiya.agileagent.spi.identity.IdentityContext;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

class DomainAgentTest {

    private static final ObjectMapper JSON = new ObjectMapper();

    @Test
    void finalAnswerStreamsDeltasThenFinished() {
        LlmClient llm = mock(LlmClient.class);
        var provider = new RecordingProvider();
        when(llm.chatStream(any(), any())).thenReturn(Flux.just(
                new LlmClient.LlmEvent.ContentDelta("你好，"),
                new LlmClient.LlmEvent.ContentDelta("答案在资料中"),
                new LlmClient.LlmEvent.Completed("你好，答案在资料中")));
        var agent = new DomainAgent(registry(provider));

        List<AgentEvent> events = agent.run(task("NOTES"), llm).collectList().block();

        assertThat(events).containsExactly(
                new AgentEvent.Delta("NOTES", "你好，"),
                new AgentEvent.Delta("NOTES", "答案在资料中"),
                new AgentEvent.Finished("NOTES", "你好，答案在资料中"));
        assertThat(provider.executeCalls).isZero();
    }

    @Test
    void knowledgeToolReadsDocumentThenCompletes() {
        LlmClient llm = mock(LlmClient.class);
        KnowledgeCatalog catalog = mock(KnowledgeCatalog.class);
        when(catalog.compactIndex()).thenReturn("doc-1 | Stage API | stage 对象用法\n");
        when(catalog.content("doc-1")).thenReturn(Mono.just("# Stage API\nstage.set(key, value)"));
        var toolCall = new LlmClient.ToolCall("call_1", "readKnowledgeDoc", "{\"id\":\"doc-1\"}");
        when(llm.chatStream(any(), any()))
                .thenReturn(Flux.just(new LlmClient.LlmEvent.Usage(2, 1, 3),
                        new LlmClient.LlmEvent.ToolCallsReady(List.of(toolCall))))
                .thenReturn(Flux.just(new LlmClient.LlmEvent.Usage(5, 4, 9),
                        new LlmClient.LlmEvent.Completed("stage.set 用于设置变量")));
        var agent = new DomainAgent(registry(new KnowledgeCapabilityProvider(catalog)));

        List<AgentEvent> events = agent.run(task("KNOWLEDGE"), llm).collectList().block();

        assertThat(events).contains(new AgentEvent.Finished("KNOWLEDGE", "stage.set 用于设置变量"));
        assertThat(events).filteredOn(AgentEvent.Usage.class::isInstance).containsExactly(
                new AgentEvent.Usage("KNOWLEDGE", 2, 1, 3), new AgentEvent.Usage("KNOWLEDGE", 5, 4, 9));
        verify(catalog).content("doc-1");
    }

    @Test
    void maxRoundsExhaustedEmitsFailedEvent() {
        LlmClient llm = mock(LlmClient.class);
        var provider = new RecordingProvider();
        var call = new LlmClient.ToolCall("call-1", "read", "{}");
        when(llm.chatStream(any(), any())).thenReturn(Flux.just(new LlmClient.LlmEvent.ToolCallsReady(List.of(call))));
        var agent = new DomainAgent(registry(provider));

        List<AgentEvent> events = agent.run(task("NOTES"), llm).collectList().block();

        assertThat(events).anySatisfy(event -> assertThat(event).isInstanceOf(AgentEvent.Failed.class));
        verify(llm, times(DomainAgent.MAX_ROUNDS)).chatStream(any(), any());
    }

    @Test
    void unknownCapabilityEmitsFailureWithoutCallingLlm() {
        LlmClient llm = mock(LlmClient.class);
        var agent = new DomainAgent(registry(new RecordingProvider()));

        List<AgentEvent> events = agent.run(task("UNKNOWN"), llm).collectList().block();

        assertThat(events).containsExactly(new AgentEvent.Failed("UNKNOWN", "unknown role: UNKNOWN"));
        verifyNoInteractions(llm);
    }

    @Test
    void undefinedToolIsRejectedWithoutCallingProviderExecute() {
        LlmClient llm = mock(LlmClient.class);
        var provider = new RecordingProvider();
        var toolCall = new LlmClient.ToolCall("call-1", "notAllowed", "{}");
        when(llm.chatStream(any(), any()))
                .thenReturn(Flux.just(new LlmClient.LlmEvent.ToolCallsReady(List.of(toolCall))))
                .thenReturn(Flux.just(new LlmClient.LlmEvent.Completed("rejected")));
        var agent = new DomainAgent(registry(provider));

        agent.run(task("NOTES"), llm).collectList().block();

        assertThat(provider.executeCalls).isZero();
        assertThat(provider.definitionContext.conversationId()).isEqualTo("conv-9");
        assertThat(provider.definitionContext.identity().subject()).isEqualTo("conv-9");
        assertThat(provider.definitionContext.toString()).doesNotContain("secret-token");
    }

    @Test
    void passesSameIdentityConversationAndLazyCredentialContextToProvider() {
        LlmClient llm = mock(LlmClient.class);
        var provider = new RecordingProvider();
        var toolCall = new LlmClient.ToolCall("call-1", "read", "{}");
        when(llm.chatStream(any(), any()))
                .thenReturn(Flux.just(new LlmClient.LlmEvent.ToolCallsReady(List.of(toolCall))))
                .thenReturn(Flux.just(new LlmClient.LlmEvent.Completed("done")));
        var identity = new IdentityContext("user-7", "Lin", null, null, null, null,
                java.util.Set.of(), java.util.Set.of(), java.util.Set.of(), false, false);
        var agent = new DomainAgent(registry(provider));

        agent.run(new DomainTask("task-1", "conv-9", "NOTES", "question", "secret-token", identity), llm)
                .collectList().block();

        assertThat(provider.definitionContext.identity()).isSameAs(identity);
        assertThat(provider.definitionContext.conversationId()).isEqualTo("conv-9");
        assertThat(provider.executionContext).isSameAs(provider.definitionContext);
        assertThat(provider.executionToken).isEqualTo("secret-token");
        assertThat(provider.executionContext.toString()).doesNotContain("secret-token");
    }

    @Test
    void taskDoesNotExposeAccessTokenInStringOrJson() throws Exception {
        var task = new DomainTask("task-1", "conv-9", "KNOWLEDGE", "question", "sentinel-token");

        assertThat(task.toString()).doesNotContain("sentinel-token");
        assertThat(JSON.writeValueAsString(task)).doesNotContain("sentinel-token").doesNotContain("accessToken");
    }

    @Test
    void invalidDynamicDefinitionFailsBeforeAnyLlmCall() {
        LlmClient llm = mock(LlmClient.class);
        var provider = new RecordingProvider() {
            @Override
            public Mono<CapabilityDefinition> definition(AgentExecutionContext context) {
                return Mono.just(new CapabilityDefinition("prompt", List.of(
                        new ToolDefinition("duplicate", "one", JSON.createObjectNode().put("type", "object")),
                        new ToolDefinition("duplicate", "two", JSON.createObjectNode().put("type", "object")))));
            }
        };
        var agent = new DomainAgent(registry(provider));

        assertThatThrownBy(() -> agent.run(task("NOTES"), llm).collectList().block())
                .isInstanceOf(IllegalStateException.class).hasMessage("capability definition failed");
        verifyNoInteractions(llm);
    }

    private static CapabilityRegistry registry(AgentCapabilityProvider provider) {
        return new CapabilityRegistry(List.of(provider));
    }

    private static DomainTask task(String code) {
        return new DomainTask("task-1", "conv-9", code, "question", "secret-token");
    }

    private static class RecordingProvider implements AgentCapabilityProvider {
        private int executeCalls;
        private AgentExecutionContext definitionContext;
        private AgentExecutionContext executionContext;
        private String executionToken;

        @Override public String code() { return "NOTES"; }
        @Override public String displayName() { return "Notes"; }
        @Override public String routingDescription() { return "Use reviewed notes"; }

        @Override
        public Mono<CapabilityDefinition> definition(AgentExecutionContext context) {
            definitionContext = context;
            return Mono.just(new CapabilityDefinition("prompt", List.of(
                    new ToolDefinition("read", "Read a note", JSON.createObjectNode().put("type", "object")))));
        }

        @Override
        public Mono<ToolResult> execute(ToolInvocation invocation, AgentExecutionContext context) {
            executeCalls++;
            executionContext = context;
            executionToken = context.accessTokenAccessor().get();
            return Mono.just(new ToolResult(true, "ok"));
        }
    }
}
