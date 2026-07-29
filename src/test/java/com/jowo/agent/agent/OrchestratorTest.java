package com.jowo.agent.agent;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.jowo.agent.knowledge.KnowledgeCatalog;
import com.jowo.agent.service.ApiCatalog;
import com.jowo.agent.service.ChatMemory;
import com.jowo.agent.service.LlmClient;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

class OrchestratorTest {

    private final LlmClient llm = mock(LlmClient.class);
    private final TaskDispatcher dispatcher = mock(TaskDispatcher.class);
    private final ChatMemory memory = mock(ChatMemory.class);
    private final ApiCatalog apiCatalog = mock(ApiCatalog.class);
    private final KnowledgeCatalog knowledgeCatalog = mock(KnowledgeCatalog.class);
    private final AgentRoleRegistry roles = new AgentRoleRegistry(apiCatalog, knowledgeCatalog);
    private final Orchestrator orchestrator = new Orchestrator(llm, dispatcher, roles, memory);

    private void stubEmptyHistory() {
        when(memory.get("conv-1")).thenReturn(Mono.just(List.of()));
        when(memory.append(any(), anyList())).thenReturn(Mono.empty());
    }

    @SuppressWarnings("unchecked")
    private List<DomainTask> capturedTasks() {
        ArgumentCaptor<List<DomainTask>> captor = ArgumentCaptor.forClass(List.class);
        verify(dispatcher).dispatch(captor.capture());
        return captor.getValue();
    }

    @Test
    void singleDomainRoutesDispatchesAndAggregatesFinalAnswer() {
        stubEmptyHistory();
        when(llm.chatStream(any(), eq(List.of())))
                .thenReturn(Flux.just(new LlmClient.LlmEvent.Completed("[\"MES\"]")))
                .thenReturn(Flux.just(
                        new LlmClient.LlmEvent.ContentDelta("共"),
                        new LlmClient.LlmEvent.ContentDelta("有 48 个客户"),
                        new LlmClient.LlmEvent.Completed("共有 48 个客户")));
        when(dispatcher.dispatch(anyList())).thenReturn(Flux.just(new AgentEvent.Finished("MES", "mes: 48 个客户")));

        List<String> lines = orchestrator.chat("conv-1", "sl-tok", "有多少个客户").collectList().block();

        assertThat(lines).anySatisfy(l -> assertThat(l).contains("\"type\":\"thinking\""));
        assertThat(lines).anySatisfy(l -> assertThat(l).contains("\"type\":\"delta\"").contains("共"));
        assertThat(lines).anySatisfy(l -> assertThat(l).isEqualTo("共有 48 个客户\n"));
        assertThat(lines.get(lines.size() - 1)).contains("\"type\":\"done\"");
        assertThat(capturedTasks()).extracting(DomainTask::roleCode).containsExactly("MES");
        verify(memory).append(eq("conv-1"), eq(List.of(
                LlmClient.Message.user("有多少个客户"), LlmClient.Message.assistantText("共有 48 个客户"))));
    }

    @Test
    void multiDomainRoutesToBothAndSurfacesToolCallProgress() {
        stubEmptyHistory();
        when(llm.chatStream(any(), eq(List.of())))
                .thenReturn(Flux.just(new LlmClient.LlmEvent.Completed("[\"MES\",\"CRM\"]")))
                .thenReturn(Flux.just(new LlmClient.LlmEvent.Completed("综合结果")));
        when(dispatcher.dispatch(anyList())).thenReturn(Flux.just(
                new AgentEvent.ToolCalled("MES", "callEnterpriseApi", "{}"),
                new AgentEvent.Finished("MES", "mes 结果"),
                new AgentEvent.Finished("CRM", "crm 结果")));

        List<String> lines = orchestrator.chat("conv-1", "sl-tok", "客户和生产情况").collectList().block();

        assertThat(lines).anySatisfy(l -> assertThat(l).contains("\"type\":\"tool_call\""));
        assertThat(lines).anySatisfy(l -> assertThat(l).isEqualTo("综合结果\n"));
        assertThat(capturedTasks()).extracting(DomainTask::roleCode).containsExactlyInAnyOrder("MES", "CRM");
    }

    @Test
    void emptyRoutingArrayAnswersDirectlyWithoutDispatching() {
        stubEmptyHistory();
        when(llm.chatStream(any(), eq(List.of())))
                .thenReturn(Flux.just(new LlmClient.LlmEvent.Completed("[]")))
                .thenReturn(Flux.just(new LlmClient.LlmEvent.Completed("你好！我可以帮你查询企业系统数据。")));

        List<String> lines = orchestrator.chat("conv-1", "sl-tok", "你好").collectList().block();

        assertThat(lines).anySatisfy(l -> assertThat(l).isEqualTo("你好！我可以帮你查询企业系统数据。\n"));
        verifyNoInteractions(dispatcher);
    }

    @Test
    void malformedRoutingJsonDegradesToDirectAnswer() {
        stubEmptyHistory();
        when(llm.chatStream(any(), eq(List.of())))
                .thenReturn(Flux.just(new LlmClient.LlmEvent.Completed("这不是 JSON")))
                .thenReturn(Flux.just(new LlmClient.LlmEvent.Completed("直接回答")));

        List<String> lines = orchestrator.chat("conv-1", "sl-tok", "随便问问").collectList().block();

        assertThat(lines).anySatisfy(l -> assertThat(l).isEqualTo("直接回答\n"));
        verifyNoInteractions(dispatcher);
    }

    @Test
    void aggregateStillAnswersWhenOneDomainFails() {
        stubEmptyHistory();
        when(llm.chatStream(any(), eq(List.of())))
                .thenReturn(Flux.just(new LlmClient.LlmEvent.Completed("[\"MES\",\"CRM\"]")))
                .thenReturn(Flux.just(new LlmClient.LlmEvent.Completed("MES 数据已给出，CRM 暂时无法获取")));
        when(dispatcher.dispatch(anyList())).thenReturn(Flux.just(
                new AgentEvent.Finished("MES", "mes 结果"),
                new AgentEvent.Failed("CRM", "查询超时")));

        List<String> lines = orchestrator.chat("conv-1", "sl-tok", "客户和生产情况").collectList().block();

        assertThat(lines).anySatisfy(l -> assertThat(l).isEqualTo("MES 数据已给出，CRM 暂时无法获取\n"));
        assertThat(lines.get(lines.size() - 1)).contains("\"type\":\"done\"");
    }
}
