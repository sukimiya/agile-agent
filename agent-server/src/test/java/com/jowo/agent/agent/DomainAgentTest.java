package com.jowo.agent.agent;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jowo.agent.knowledge.KnowledgeCatalog;
import com.jowo.agent.service.ApiCatalog;
import com.jowo.agent.service.EnterpriseApiClient;
import com.jowo.agent.service.LlmClient;

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
        return new DomainAgent(llm, enterpriseApi, catalog, knowledgeCatalog, roles);
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
}
