package io.github.sukimiya.agileagent.agent;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.util.List;

import org.junit.jupiter.api.Test;

import io.github.sukimiya.agileagent.capability.CapabilityRegistry;
import io.github.sukimiya.agileagent.service.ChatMemory;
import io.github.sukimiya.agileagent.service.LlmClient;
import io.github.sukimiya.agileagent.spi.capability.AgentCapabilityProvider;
import io.github.sukimiya.agileagent.spi.capability.AgentExecutionContext;
import io.github.sukimiya.agileagent.spi.capability.CapabilityDefinition;
import io.github.sukimiya.agileagent.spi.capability.ToolInvocation;
import io.github.sukimiya.agileagent.spi.capability.ToolResult;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

class OrchestratorTest {

    private final LlmClient llm = mock(LlmClient.class);
    private final TaskDispatcher dispatcher = mock(TaskDispatcher.class);
    private final ChatMemory memory = mock(ChatMemory.class);
    private final CapabilityRegistry capabilities = new CapabilityRegistry(List.of(
            new TestCapability("KNOWLEDGE"), new TestCapability("NOTES")));
    private final Orchestrator orchestrator = new Orchestrator(llm, dispatcher, capabilities, memory);

    private void stubEmptyHistory() {
        when(memory.get("conv-1")).thenReturn(Mono.just(List.of()));
        when(memory.append(any(), anyList())).thenReturn(Mono.empty());
    }

    @SuppressWarnings("unchecked")
    private List<DomainTask> capturedTasks() {
        var captor = org.mockito.ArgumentCaptor.forClass(List.class);
        verify(dispatcher).dispatch(captor.capture());
        return captor.getValue();
    }

    @Test
    void singleCapabilityRoutesDispatchesAndAggregatesFinalAnswer() {
        stubEmptyHistory();
        when(llm.chatStream(any(), eq(List.of())))
                .thenReturn(Flux.just(new LlmClient.LlmEvent.Completed("[\"KNOWLEDGE\"]")))
                .thenReturn(Flux.just(
                        new LlmClient.LlmEvent.ContentDelta("有"),
                        new LlmClient.LlmEvent.ContentDelta(" 48 条记录"),
                        new LlmClient.LlmEvent.Completed("有 48 条记录")));
        when(dispatcher.dispatch(anyList())).thenReturn(Flux.just(new AgentEvent.Finished("KNOWLEDGE", "共 48 条记录")));

        List<String> lines = orchestrator.chat("conv-1", "sl-tok", "文档有多少条记录").collectList().block();

        assertThat(lines).anySatisfy(l -> assertThat(l).contains("\"type\":\"thinking\""));
        assertThat(lines).anySatisfy(l -> assertThat(l).contains("\"type\":\"delta\"").contains("有"));
        assertThat(lines).anySatisfy(l -> assertThat(l).isEqualTo("有 48 条记录\n"));
        assertThat(lines.get(lines.size() - 1)).contains("\"type\":\"done\"");
        assertThat(capturedTasks()).extracting(DomainTask::roleCode).containsExactly("KNOWLEDGE");
        verify(memory).append(eq("conv-1"), eq(List.of(
                LlmClient.Message.user("文档有多少条记录"), LlmClient.Message.assistantText("有 48 条记录"))));
    }

    @Test
    void propagatesAuthenticatedIdentityConversationAndTokenToCapabilityTask() {
        stubEmptyHistory();
        when(llm.chatStream(any(), eq(List.of())))
                .thenReturn(Flux.just(new LlmClient.LlmEvent.Completed("[\"KNOWLEDGE\"]")))
                .thenReturn(Flux.just(new LlmClient.LlmEvent.Completed("回答")));
        when(dispatcher.dispatch(anyList())).thenReturn(Flux.just(new AgentEvent.Finished("KNOWLEDGE", "知识结果")));
        var identity = new io.github.sukimiya.agileagent.spi.identity.IdentityContext("user-7", "Lin", "tenant-1", "Tenant", null, null,
                java.util.Set.of("member"), java.util.Set.of(), java.util.Set.of(), false, false);

        orchestrator.chat("conv-1", "secret-token", "question", identity).collectList().block();

        var task = capturedTasks().getFirst();
        assertThat(task.sessionId()).isEqualTo("conv-1");
        assertThat(task.identity()).isSameAs(identity);
        assertThat(task.accessToken()).isEqualTo("secret-token");
    }

    @Test
    void multipleCapabilitiesDispatchAndSurfaceToolProgress() {
        stubEmptyHistory();
        when(llm.chatStream(any(), eq(List.of())))
                .thenReturn(Flux.just(new LlmClient.LlmEvent.Completed("[\"KNOWLEDGE\",\"NOTES\"]")))
                .thenReturn(Flux.just(new LlmClient.LlmEvent.Completed("综合结果")));
        when(dispatcher.dispatch(anyList())).thenReturn(Flux.just(
                new AgentEvent.ToolCalled("KNOWLEDGE", "readKnowledgeDoc", "{}"),
                new AgentEvent.Finished("KNOWLEDGE", "知识结果"),
                new AgentEvent.Finished("NOTES", "笔记结果")));

        List<String> lines = orchestrator.chat("conv-1", "sl-tok", "总结资料").collectList().block();

        assertThat(lines).anySatisfy(l -> assertThat(l).contains("\"type\":\"tool_call\""));
        assertThat(lines).anySatisfy(l -> assertThat(l).isEqualTo("综合结果\n"));
        assertThat(capturedTasks()).extracting(DomainTask::roleCode)
                .containsExactlyInAnyOrder("KNOWLEDGE", "NOTES");
    }

    @Test
    void emptyRoutingArrayAnswersDirectlyWithoutDispatching() {
        stubEmptyHistory();
        when(llm.chatStream(any(), eq(List.of())))
                .thenReturn(Flux.just(new LlmClient.LlmEvent.Completed("[]")))
                .thenReturn(Flux.just(new LlmClient.LlmEvent.Completed("你好！我可以帮助你查阅已审核资料。")));

        List<String> lines = orchestrator.chat("conv-1", "sl-tok", "你好").collectList().block();

        assertThat(lines).anySatisfy(l -> assertThat(l).isEqualTo("你好！我可以帮助你查阅已审核资料。\n"));
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
    void aggregateStillAnswersWhenOneCapabilityFails() {
        stubEmptyHistory();
        when(llm.chatStream(any(), eq(List.of())))
                .thenReturn(Flux.just(new LlmClient.LlmEvent.Completed("[\"KNOWLEDGE\",\"NOTES\"]")))
                .thenReturn(Flux.just(new LlmClient.LlmEvent.Completed("资料已给出，笔记暂时无法获取")));
        when(dispatcher.dispatch(anyList())).thenReturn(Flux.just(
                new AgentEvent.Finished("KNOWLEDGE", "知识结果"),
                new AgentEvent.Failed("NOTES", "查询超时")));

        List<String> lines = orchestrator.chat("conv-1", "sl-tok", "汇总").collectList().block();

        assertThat(lines).anySatisfy(l -> assertThat(l).isEqualTo("资料已给出，笔记暂时无法获取\n"));
        assertThat(lines.get(lines.size() - 1)).contains("\"type\":\"done\"");
    }

    private record TestCapability(String code) implements AgentCapabilityProvider {

        @Override public String displayName() { return code; }
        @Override public String routingDescription() { return "Registered test capability"; }
        @Override public Mono<CapabilityDefinition> definition(AgentExecutionContext context) {
            return Mono.just(new CapabilityDefinition("test", List.of()));
        }
        @Override public Mono<ToolResult> execute(ToolInvocation invocation, AgentExecutionContext context) {
            return Mono.just(new ToolResult(false, "unknown tool"));
        }
    }
}
