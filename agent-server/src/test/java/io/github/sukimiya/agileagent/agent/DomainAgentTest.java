package io.github.sukimiya.agileagent.agent;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.sukimiya.agileagent.knowledge.KnowledgeCatalog;
import io.github.sukimiya.agileagent.capability.CapabilityRegistry;
import io.github.sukimiya.agileagent.spi.capability.AgentCapabilityProvider;
import io.github.sukimiya.agileagent.spi.capability.AgentExecutionContext;
import io.github.sukimiya.agileagent.spi.capability.CapabilityDefinition;
import io.github.sukimiya.agileagent.spi.capability.ToolDefinition;
import io.github.sukimiya.agileagent.spi.capability.ToolInvocation;
import io.github.sukimiya.agileagent.spi.capability.ToolResult;
import io.github.sukimiya.agileagent.spi.identity.IdentityContext;
import io.github.sukimiya.agileagent.service.ApiCatalog;
import io.github.sukimiya.agileagent.service.EnterpriseApiClient;
import io.github.sukimiya.agileagent.service.LlmClient;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

class DomainAgentTest {

    private final ObjectMapper json = new ObjectMapper();

    private String argsJson(Map<String, Object> args) {
        try {
            return json.writeValueAsString(args);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private DomainAgent newAgent(LlmClient llm, EnterpriseApiClient enterpriseApi, ApiCatalog catalog,
                                  KnowledgeCatalog knowledgeCatalog) {
        var roles = new AgentRoleRegistry(catalog, knowledgeCatalog);
        return new DomainAgent(llm, roles.capabilityRegistry(enterpriseApi));
    }

    @Test
    void finalAnswerStreamsDeltasThenFinished() {
        LlmClient llm = mock(LlmClient.class);
        EnterpriseApiClient enterpriseApi = mock(EnterpriseApiClient.class);
        ApiCatalog catalog = mock(ApiCatalog.class);
        KnowledgeCatalog knowledgeCatalog = mock(KnowledgeCatalog.class);
        when(catalog.compactIndex("MES")).thenReturn("MES | x | GET /api/x | desc\n");
        when(llm.chatStream(any(), any())).thenReturn(Flux.just(
                new LlmClient.LlmEvent.ContentDelta("你好，"),
                new LlmClient.LlmEvent.ContentDelta("答案是 5 个"),
                new LlmClient.LlmEvent.Completed("你好，答案是 5 个")));
        var agent = newAgent(llm, enterpriseApi, catalog, knowledgeCatalog);
        var task = new DomainTask("t1", "conv-1", "MES", "有多少个客户", "sl-tok");

        List<AgentEvent> events = agent.run(task).collectList().block();

        assertThat(events).containsExactly(
                new AgentEvent.Delta("MES", "你好，"),
                new AgentEvent.Delta("MES", "答案是 5 个"),
                new AgentEvent.Finished("MES", "你好，答案是 5 个"));
        verifyNoInteractions(enterpriseApi);
    }

    @Test
    void toolCallRoundExecutesApiThenSecondRoundFinishes() throws Exception {
        LlmClient llm = mock(LlmClient.class);
        EnterpriseApiClient enterpriseApi = mock(EnterpriseApiClient.class);
        ApiCatalog catalog = mock(ApiCatalog.class);
        KnowledgeCatalog knowledgeCatalog = mock(KnowledgeCatalog.class);
        when(catalog.compactIndex("MES")).thenReturn("MES | x | GET /api/mes/customers/count | desc\n");
        when(catalog.isWhitelisted("MES", "GET", "/api/mes/customers/count")).thenReturn(true);
        JsonNode fakeResult = json.readTree("{\"count\":48}");
        when(enterpriseApi.call(eq("MES"), eq("GET"), eq("/api/mes/customers/count"), anyMap(), eq("sl-tok")))
                .thenReturn(Mono.just(fakeResult));
        var toolCall = new LlmClient.ToolCall("call_1", "callEnterpriseApi",
                argsJson(Map.of("service", "MES", "method", "GET", "path", "/api/mes/customers/count", "params", Map.of())));
        when(llm.chatStream(any(), any()))
                .thenReturn(Flux.just(new LlmClient.LlmEvent.ToolCallsReady(List.of(toolCall))))
                .thenReturn(Flux.just(new LlmClient.LlmEvent.Completed("共有 48 个客户")));
        var agent = newAgent(llm, enterpriseApi, catalog, knowledgeCatalog);
        var task = new DomainTask("t1", "conv-1", "MES", "有多少个客户", "sl-tok");

        List<AgentEvent> events = agent.run(task).collectList().block();

        assertThat(events).anySatisfy(e -> assertThat(e).isInstanceOf(AgentEvent.ToolCalled.class));
        assertThat(events).contains(new AgentEvent.Finished("MES", "共有 48 个客户"));
        verify(enterpriseApi).call("MES", "GET", "/api/mes/customers/count", Map.of(), "sl-tok");
        verify(llm, times(2)).chatStream(any(), any());
    }

    @Test
    void nonWhitelistedCallIsRejectedWithoutHittingEnterpriseApi() {
        LlmClient llm = mock(LlmClient.class);
        EnterpriseApiClient enterpriseApi = mock(EnterpriseApiClient.class);
        ApiCatalog catalog = mock(ApiCatalog.class);
        KnowledgeCatalog knowledgeCatalog = mock(KnowledgeCatalog.class);
        when(catalog.compactIndex("MES")).thenReturn("MES | x | GET /a | d\n");
        when(catalog.isWhitelisted("MES", "POST", "/etc/passwd")).thenReturn(false);
        var badCall = new LlmClient.ToolCall("call_1", "callEnterpriseApi",
                argsJson(Map.of("service", "MES", "method", "POST", "path", "/etc/passwd", "params", Map.of())));
        when(llm.chatStream(any(), any()))
                .thenReturn(Flux.just(new LlmClient.LlmEvent.ToolCallsReady(List.of(badCall))))
                .thenReturn(Flux.just(new LlmClient.LlmEvent.Completed("抱歉，我找不到相关接口")));
        var agent = newAgent(llm, enterpriseApi, catalog, knowledgeCatalog);
        var task = new DomainTask("t1", "conv-1", "MES", "q", "sl-tok");

        agent.run(task).collectList().block();

        verifyNoInteractions(enterpriseApi);
    }

    @Test
    void maxRoundsExhaustedEmitsFailedEvent() {
        LlmClient llm = mock(LlmClient.class);
        EnterpriseApiClient enterpriseApi = mock(EnterpriseApiClient.class);
        ApiCatalog catalog = mock(ApiCatalog.class);
        KnowledgeCatalog knowledgeCatalog = mock(KnowledgeCatalog.class);
        when(catalog.compactIndex("MES")).thenReturn("MES | x | GET /a | d\n");
        when(catalog.detail(any(), any())).thenReturn(Optional.of("doc"));
        var call = new LlmClient.ToolCall("c", "getApiDetail", argsJson(Map.of("service", "MES", "path", "/a")));
        when(llm.chatStream(any(), any()))
                .thenReturn(Flux.just(new LlmClient.LlmEvent.ToolCallsReady(List.of(call))));
        var agent = newAgent(llm, enterpriseApi, catalog, knowledgeCatalog);
        var task = new DomainTask("t1", "conv-1", "MES", "q", "sl-tok");

        List<AgentEvent> events = agent.run(task).collectList().block();

        assertThat(events).anySatisfy(e -> assertThat(e).isInstanceOf(AgentEvent.Failed.class));
        verify(llm, times(DomainAgent.MAX_ROUNDS)).chatStream(any(), any());
    }

    @Test
    void readKnowledgeDocToolReturnsDocContentToLlmThenFinishes() {
        LlmClient llm = mock(LlmClient.class);
        EnterpriseApiClient enterpriseApi = mock(EnterpriseApiClient.class);
        ApiCatalog catalog = mock(ApiCatalog.class);
        KnowledgeCatalog knowledgeCatalog = mock(KnowledgeCatalog.class);
        when(knowledgeCatalog.compactIndex()).thenReturn("doc-1 | Stage API | stage 对象用法\n");
        when(knowledgeCatalog.content("doc-1")).thenReturn(Mono.just("# Stage API\nstage.set(key, value)"));
        var toolCall = new LlmClient.ToolCall("call_1", "readKnowledgeDoc", argsJson(Map.of("id", "doc-1")));
        when(llm.chatStream(any(), any()))
                .thenReturn(Flux.just(new LlmClient.LlmEvent.ToolCallsReady(List.of(toolCall))))
                .thenReturn(Flux.just(new LlmClient.LlmEvent.Completed("stage.set 用于设置变量")));
        var agent = newAgent(llm, enterpriseApi, catalog, knowledgeCatalog);
        var task = new DomainTask("t1", "conv-1", "KNOWLEDGE", "stage.set 怎么用", "sl-tok");

        List<AgentEvent> events = agent.run(task).collectList().block();

        assertThat(events).contains(new AgentEvent.Finished("KNOWLEDGE", "stage.set 用于设置变量"));
        verify(knowledgeCatalog).content("doc-1");
    }

    @Test
    void unknownRoleCodeEmitsFailedEventWithoutCallingLlm() {
        LlmClient llm = mock(LlmClient.class);
        EnterpriseApiClient enterpriseApi = mock(EnterpriseApiClient.class);
        ApiCatalog catalog = mock(ApiCatalog.class);
        KnowledgeCatalog knowledgeCatalog = mock(KnowledgeCatalog.class);
        var agent = newAgent(llm, enterpriseApi, catalog, knowledgeCatalog);
        var task = new DomainTask("t1", "conv-1", "NOPE", "q", "sl-tok");

        List<AgentEvent> events = agent.run(task).collectList().block();

        assertThat(events).containsExactly(new AgentEvent.Failed("NOPE", "unknown role: NOPE"));
        verifyNoInteractions(llm);
    }

    @Test
    void undefinedToolIsRejectedWithoutCallingProviderExecute() {
        LlmClient llm = mock(LlmClient.class);
        var provider = new RecordingProvider();
        var agent = new DomainAgent(llm, new CapabilityRegistry(List.of(provider)));
        var toolCall = new LlmClient.ToolCall("call-1", "notAllowed", "{}");
        when(llm.chatStream(any(), any())).thenReturn(Flux.just(new LlmClient.LlmEvent.ToolCallsReady(List.of(toolCall))))
                .thenReturn(Flux.just(new LlmClient.LlmEvent.Completed("rejected")));
        var task = new DomainTask("task-1", "conv-9", "KNOWLEDGE", "question", "secret-token");

        agent.run(task).collectList().block();

        assertThat(provider.executeCalls).isZero();
        assertThat(provider.definitionContext.conversationId()).isEqualTo("conv-9");
        assertThat(provider.definitionContext.identity().subject()).isEqualTo("conv-9");
        assertThat(provider.definitionContext.toString()).doesNotContain("secret-token");
    }

    @Test
    void passesSameIdentityConversationAndLazyCredentialContextToDefinitionAndExecution() {
        LlmClient llm = mock(LlmClient.class);
        var provider = new RecordingProvider();
        var agent = new DomainAgent(llm, new CapabilityRegistry(List.of(provider)));
        var toolCall = new LlmClient.ToolCall("call-1", "read", "{}");
        when(llm.chatStream(any(), any())).thenReturn(Flux.just(new LlmClient.LlmEvent.ToolCallsReady(List.of(toolCall))))
                .thenReturn(Flux.just(new LlmClient.LlmEvent.Completed("done")));
        var identity = new IdentityContext("user-7", "Lin", null, null, null, null,
                java.util.Set.of(), java.util.Set.of(), java.util.Set.of(), false, false);
        var task = new DomainTask("task-1", "conv-9", "KNOWLEDGE", "question", "secret-token", identity);

        agent.run(task).collectList().block();

        assertThat(provider.definitionContext.identity()).isSameAs(identity);
        assertThat(provider.definitionContext.conversationId()).isEqualTo("conv-9");
        assertThat(provider.executionContext).isSameAs(provider.definitionContext);
        assertThat(provider.executionToken).isEqualTo("secret-token");
        assertThat(provider.executionContext.toString()).doesNotContain("secret-token");
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
        var agent = new DomainAgent(llm, new CapabilityRegistry(List.of(provider)));

        assertThatThrownBy(() -> agent.run(new DomainTask("task-1", "conv-1", "KNOWLEDGE", "q", "token"))
                .collectList().block()).isInstanceOf(IllegalStateException.class)
                .hasMessage("capability definition failed");
        verifyNoInteractions(llm);
    }

    @Test
    void accessTokenIsRedactedFromProviderToolResults() throws Exception {
        LlmClient llm = mock(LlmClient.class);
        EnterpriseApiClient enterpriseApi = mock(EnterpriseApiClient.class);
        ApiCatalog catalog = mock(ApiCatalog.class);
        KnowledgeCatalog knowledgeCatalog = mock(KnowledgeCatalog.class);
        when(catalog.compactIndex("MES")).thenReturn("MES | x | GET /a | d\n");
        when(catalog.isWhitelisted("MES", "GET", "/a")).thenReturn(true);
        when(enterpriseApi.call(eq("MES"), eq("GET"), eq("/a"), anyMap(), eq("secret-token")))
                .thenReturn(Mono.just(json.readTree("{\"echo\":\"secret-token\"}")));
        var call = new LlmClient.ToolCall("call-1", "callEnterpriseApi",
                argsJson(Map.of("service", "MES", "method", "GET", "path", "/a")));
        @SuppressWarnings("unchecked")
        var toolRoundMessages = new java.util.concurrent.atomic.AtomicReference<List<LlmClient.Message>>();
        when(llm.chatStream(any(), any())).thenReturn(Flux.just(new LlmClient.LlmEvent.ToolCallsReady(List.of(call))))
                .thenAnswer(invocation -> {
                    toolRoundMessages.set(invocation.getArgument(0));
                    return Flux.just(new LlmClient.LlmEvent.Completed("done"));
                });
        var task = new DomainTask("t1", "conv-1", "MES", "q", "secret-token");
        var agent = newAgent(llm, enterpriseApi, catalog, knowledgeCatalog);

        agent.run(task).collectList().block();

        verify(enterpriseApi).call("MES", "GET", "/a", Map.of(), "secret-token");
        assertThat(toolRoundMessages.get()).filteredOn(m -> "tool".equals(m.role()))
                .extracting(LlmClient.Message::content).allSatisfy(content -> assertThat(content).doesNotContain("secret-token"));
    }

    private static class RecordingProvider implements AgentCapabilityProvider {
        protected static final ObjectMapper JSON = new ObjectMapper();
        private int executeCalls;
        private AgentExecutionContext definitionContext;
        private AgentExecutionContext executionContext;
        private String executionToken;

        @Override public String code() { return "KNOWLEDGE"; }
        @Override public String displayName() { return "Knowledge"; }
        @Override public String routingDescription() { return "Read knowledge"; }

        @Override
        public Mono<CapabilityDefinition> definition(AgentExecutionContext context) {
            definitionContext = context;
            return Mono.just(new CapabilityDefinition("prompt", List.of(new ToolDefinition("read", "Read", JSON.createObjectNode().put("type", "object")))));
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
