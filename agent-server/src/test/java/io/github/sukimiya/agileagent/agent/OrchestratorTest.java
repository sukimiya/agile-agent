package io.github.sukimiya.agileagent.agent;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import io.github.sukimiya.agileagent.capability.CapabilityRegistry;
import io.github.sukimiya.agileagent.memory.MemoryModel.AcceptedTurn;
import io.github.sukimiya.agileagent.memory.MemoryTestStack;
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
    private final MemoryTestStack stack = MemoryTestStack.h2();
    private final CapabilityRegistry capabilities = new CapabilityRegistry(List.of(
            new TestCapability("KNOWLEDGE"), new TestCapability("NOTES")));
    private final Orchestrator orchestrator = new Orchestrator(dispatcher, capabilities, stack.lifecycle(),
            stack.history());
    private final String conversation = MemoryTestStack.newConversationId();

    @AfterEach
    void tearDown() {
        stack.close();
    }

    private AcceptedTurn accept(String question) {
        return stack.accept(conversation, question);
    }

    private List<String> chat(String question) {
        return chat(accept(question));
    }

    private List<String> chat(AcceptedTurn accepted) {
        return orchestrator.chat(accepted, "sl-tok", null, llm).collectList().block();
    }

    @SuppressWarnings("unchecked")
    private List<DomainTask> capturedTasks() {
        var captor = org.mockito.ArgumentCaptor.forClass(List.class);
        verify(dispatcher).dispatch(captor.capture(), same(llm));
        return captor.getValue();
    }

    @Test
    void singleCapabilityRoutesDispatchesAndAggregatesFinalAnswer() {
        when(llm.chatStream(any(), eq(List.of())))
                .thenReturn(Flux.just(new LlmClient.LlmEvent.Completed("[\"KNOWLEDGE\"]")))
                .thenReturn(Flux.just(
                        new LlmClient.LlmEvent.ContentDelta("有"),
                        new LlmClient.LlmEvent.ContentDelta(" 48 条记录"),
                        new LlmClient.LlmEvent.Completed("有 48 条记录")));
        when(dispatcher.dispatch(anyList(), same(llm))).thenReturn(Flux.just(new AgentEvent.Finished("KNOWLEDGE", "共 48 条记录")));

        var accepted = accept("文档有多少条记录");
        List<String> lines = chat(accepted);

        assertThat(lines).anySatisfy(l -> assertThat(l).contains("\"type\":\"thinking\""));
        assertThat(lines).anySatisfy(l -> assertThat(l).contains("\"type\":\"delta\"").contains("有"));
        assertThat(lines).anySatisfy(l -> assertThat(l).isEqualTo("有 48 条记录\n"));
        assertThat(lines.get(lines.size() - 1)).contains("\"type\":\"done\"");
        assertThat(capturedTasks()).extracting(DomainTask::roleCode).containsExactly("KNOWLEDGE");
        assertThat(stack.status(accepted.ticket().turnId())).isEqualTo("COMPLETED");
        assertThat(stack.assistantText(accepted.ticket().turnId())).isEqualTo("有 48 条记录");
        assertThat(stack.messageCount(conversation, "user")).isEqualTo(1);
    }

    @Test
    void propagatesAuthenticatedIdentityConversationAndTokenToCapabilityTask() {
        when(llm.chatStream(any(), eq(List.of())))
                .thenReturn(Flux.just(new LlmClient.LlmEvent.Completed("[\"KNOWLEDGE\"]")))
                .thenReturn(Flux.just(new LlmClient.LlmEvent.Completed("回答")));
        when(dispatcher.dispatch(anyList(), same(llm))).thenReturn(Flux.just(new AgentEvent.Finished("KNOWLEDGE", "知识结果")));
        var identity = new io.github.sukimiya.agileagent.spi.identity.IdentityContext("user-7", "Lin", "tenant-1", "Tenant", null, null,
                java.util.Set.of("member"), java.util.Set.of(), java.util.Set.of(), false, false);

        orchestrator.chat(accept("question"), "secret-token", identity, llm).collectList().block();

        var task = capturedTasks().getFirst();
        assertThat(task.sessionId()).isEqualTo(conversation);
        assertThat(task.userMessage()).isEqualTo("question");
        assertThat(task.identity()).isSameAs(identity);
        assertThat(task.accessToken()).isEqualTo("secret-token");
    }

    @Test
    void multipleCapabilitiesDispatchAndSurfaceToolProgress() {
        when(llm.chatStream(any(), eq(List.of())))
                .thenReturn(Flux.just(new LlmClient.LlmEvent.Completed("[\"KNOWLEDGE\",\"NOTES\"]"),
                        new LlmClient.LlmEvent.Usage(1, 2, 3)))
                .thenReturn(Flux.just(new LlmClient.LlmEvent.Usage(7, 8, 15),
                        new LlmClient.LlmEvent.Completed("综合结果")));
        when(dispatcher.dispatch(anyList(), same(llm))).thenReturn(Flux.just(
                new AgentEvent.ToolCalled("KNOWLEDGE", "readKnowledgeDoc", "{}"),
                new AgentEvent.Usage("KNOWLEDGE", 3, 4, 7),
                new AgentEvent.Finished("KNOWLEDGE", "知识结果"),
                new AgentEvent.Usage("NOTES", 5, 6, 11),
                new AgentEvent.Finished("NOTES", "笔记结果")));

        List<String> lines = chat("总结资料");

        assertThat(lines).anySatisfy(l -> assertThat(l).contains("\"type\":\"tool_call\""));
        assertThat(lines).anySatisfy(l -> assertThat(l).isEqualTo("综合结果\n"));
        assertThat(lines.stream().filter(line -> line.contains("\"type\":\"usage\"")).toList())
                .containsExactly("{\"type\":\"usage\",\"prompt_tokens\":16,\"completion_tokens\":20,\"total_tokens\":36}\n");
        assertThat(capturedTasks()).extracting(DomainTask::roleCode)
                .containsExactlyInAnyOrder("KNOWLEDGE", "NOTES");
    }

    @Test
    void emptyRoutingArrayAnswersDirectlyWithoutDispatching() {
        when(llm.chatStream(any(), eq(List.of())))
                .thenReturn(Flux.just(new LlmClient.LlmEvent.Completed("[]")))
                .thenReturn(Flux.just(new LlmClient.LlmEvent.Completed("你好！我可以帮助你查阅已审核资料。")));

        List<String> lines = chat("你好");

        assertThat(lines).anySatisfy(l -> assertThat(l).isEqualTo("你好！我可以帮助你查阅已审核资料。\n"));
        verifyNoInteractions(dispatcher);
    }

    @Test
    void malformedRoutingJsonDegradesToDirectAnswer() {
        when(llm.chatStream(any(), eq(List.of())))
                .thenReturn(Flux.just(new LlmClient.LlmEvent.Completed("这不是 JSON")))
                .thenReturn(Flux.just(new LlmClient.LlmEvent.Completed("直接回答")));

        List<String> lines = chat("随便问问");

        assertThat(lines).anySatisfy(l -> assertThat(l).isEqualTo("直接回答\n"));
        verifyNoInteractions(dispatcher);
    }

    @Test
    void aggregatesRoutingAndFinalUsageOnceAfterVisibleAnswerAndBeforeDone() {
        when(llm.chatStream(any(), eq(List.of())))
                .thenReturn(Flux.just(new LlmClient.LlmEvent.Completed("[]"),
                        new LlmClient.LlmEvent.Usage(2, 1, 3)))
                .thenReturn(Flux.just(new LlmClient.LlmEvent.Usage(5, 4, 9),
                        new LlmClient.LlmEvent.Completed("hello")));

        List<String> lines = chat("hi");

        var usage = lines.stream().filter(line -> line.contains("\"type\":\"usage\"")).toList();
        assertThat(usage).containsExactly("{\"type\":\"usage\",\"prompt_tokens\":7,\"completion_tokens\":5,\"total_tokens\":12}\n");
        assertThat(lines.indexOf("hello\n")).isLessThan(lines.indexOf(usage.getFirst()));
        assertThat(lines.indexOf(usage.getFirst())).isLessThan(lines.size() - 1);
        assertThat(lines.getLast()).contains("\"type\":\"done\"");
    }

    @Test
    void aggregateStillAnswersWhenOneCapabilityFails() {
        when(llm.chatStream(any(), eq(List.of())))
                .thenReturn(Flux.just(new LlmClient.LlmEvent.Completed("[\"KNOWLEDGE\",\"NOTES\"]")))
                .thenReturn(Flux.just(new LlmClient.LlmEvent.Completed("资料已给出，笔记暂时无法获取")));
        when(dispatcher.dispatch(anyList(), same(llm))).thenReturn(Flux.just(
                new AgentEvent.Finished("KNOWLEDGE", "知识结果"),
                new AgentEvent.Failed("NOTES", "查询超时")));

        List<String> lines = chat("汇总");

        assertThat(lines).anySatisfy(l -> assertThat(l).isEqualTo("资料已给出，笔记暂时无法获取\n"));
        assertThat(lines.get(lines.size() - 1)).contains("\"type\":\"done\"");
    }

    @Test
    void promptHistoryComesFromCompletedTurnsAndExcludesTheCurrentQuestion() {
        when(llm.chatStream(any(), eq(List.of())))
                .thenReturn(Flux.just(new LlmClient.LlmEvent.Completed("[]")))
                .thenReturn(Flux.just(new LlmClient.LlmEvent.Completed("第一次回答")))
                .thenReturn(Flux.just(new LlmClient.LlmEvent.Completed("[]")))
                .thenReturn(Flux.just(new LlmClient.LlmEvent.Completed("第二次回答")));
        chat("第一个问题");

        chat("第二个问题");

        @SuppressWarnings("unchecked")
        var captor = org.mockito.ArgumentCaptor.forClass(List.class);
        verify(llm, times(4)).chatStream(captor.capture(), eq(List.of()));
        List<LlmClient.Message> finalPrompt = captor.getAllValues().get(3);
        assertThat(finalPrompt.subList(1, finalPrompt.size())).containsExactly(
                LlmClient.Message.user("第一个问题"), LlmClient.Message.assistantText("第一次回答"),
                LlmClient.Message.user("第二个问题"));
    }

    @Test
    void completedReplayEmitsOneDeltaObservedUsageAndDoneWithoutCallingTheModel() {
        when(llm.chatStream(any(), eq(List.of())))
                .thenReturn(Flux.just(new LlmClient.LlmEvent.Completed("[]")))
                .thenReturn(Flux.just(new LlmClient.LlmEvent.Completed("原回答"), new LlmClient.LlmEvent.Usage(4, 5, 9)));
        var first = stack.accept(MemoryTestStack.ALICE, conversation, "key-1", "问题");
        chat(first);

        var replay = stack.accept(MemoryTestStack.ALICE, conversation, "key-1", "问题");
        List<String> lines = chat(replay);

        assertThat(replay.replay()).isTrue();
        assertThat(lines).containsExactly(
                "{\"type\":\"delta\",\"content\":\"原回答\"}\n",
                "{\"type\":\"usage\",\"prompt_tokens\":4,\"completion_tokens\":5,\"total_tokens\":9}\n",
                "{\"type\":\"done\"}\n");
        verify(llm, times(2)).chatStream(any(), any());
    }

    @Test
    void usageArrivingAfterCompletedIsPersisted() {
        when(llm.chatStream(any(), eq(List.of())))
                .thenReturn(Flux.just(new LlmClient.LlmEvent.Completed("[]"), new LlmClient.LlmEvent.Usage(1, 1, 2)))
                .thenReturn(Flux.just(new LlmClient.LlmEvent.Completed("答"), new LlmClient.LlmEvent.Usage(3, 4, 7)));
        var accepted = accept("q");

        chat(accepted);

        var usage = stack.usage(accepted.ticket().turnId());
        assertThat(usage.get("USAGE_OBSERVED")).isEqualTo(true);
        assertThat(((Number) usage.get("PROMPT_TOKENS")).longValue()).isEqualTo(4);
        assertThat(((Number) usage.get("COMPLETION_TOKENS")).longValue()).isEqualTo(5);
        assertThat(((Number) usage.get("TOTAL_TOKENS")).longValue()).isEqualTo(9);
    }

    @Test
    void streamWithoutCompletedIsFailureAndNeverSucceeds() {
        when(llm.chatStream(any(), eq(List.of())))
                .thenReturn(Flux.just(new LlmClient.LlmEvent.Completed("[]")))
                .thenReturn(Flux.just(new LlmClient.LlmEvent.ContentDelta("半截")));
        var accepted = accept("q");

        List<String> lines = chat(accepted);

        assertThat(lines).anySatisfy(l -> assertThat(l).contains("\"type\":\"error\"").contains("\"code\":\"TURN_FAILED\""));
        assertThat(lines.getLast()).contains("\"type\":\"done\"");
        assertThat(stack.status(accepted.ticket().turnId())).isEqualTo("FAILED");
        assertThat(stack.errorCode(accepted.ticket().turnId())).isEqualTo("MISSING_COMPLETED");
    }

    @Test
    void modelErrorMarksFailedWithStableErrorEvent() {
        when(llm.chatStream(any(), eq(List.of())))
                .thenReturn(Flux.just(new LlmClient.LlmEvent.Completed("[]")))
                .thenReturn(Flux.error(new IllegalStateException("upstream secret detail")));
        var accepted = accept("q");

        List<String> lines = chat(accepted);

        assertThat(lines).noneSatisfy(l -> assertThat(l).contains("secret"));
        assertThat(lines).anySatisfy(l -> assertThat(l).contains("\"code\":\"TURN_FAILED\""));
        assertThat(stack.status(accepted.ticket().turnId())).isEqualTo("FAILED");
    }

    @Test
    void secondSubscriptionOfTheSameTicketNeverCallsTheModelAgain() {
        when(llm.chatStream(any(), eq(List.of())))
                .thenReturn(Flux.just(new LlmClient.LlmEvent.Completed("[]")))
                .thenReturn(Flux.just(new LlmClient.LlmEvent.Completed("一次")));
        var accepted = accept("q");
        chat(accepted);

        List<String> again = chat(accepted);

        assertThat(again).anySatisfy(l -> assertThat(l).contains("\"code\":\"TURN_TERMINATED\""));
        verify(llm, times(2)).chatStream(any(), any());
    }

    @Test
    void oversizedFinalAnswerFailsWithMessageTooLarge() {
        stack.props().setMaxMessageBytes(400);
        when(llm.chatStream(any(), eq(List.of())))
                .thenReturn(Flux.just(new LlmClient.LlmEvent.Completed("[]")))
                .thenReturn(Flux.just(new LlmClient.LlmEvent.Completed("x".repeat(1000))));
        var accepted = accept("q");

        List<String> lines = chat(accepted);

        assertThat(lines).anySatisfy(l -> assertThat(l).contains("\"code\":\"MESSAGE_TOO_LARGE\""));
        assertThat(stack.status(accepted.ticket().turnId())).isEqualTo("FAILED");
        assertThat(stack.errorCode(accepted.ticket().turnId())).isEqualTo("MESSAGE_TOO_LARGE");
        assertThat(stack.assistantText(accepted.ticket().turnId())).isNull();
    }

    @Test
    void multiByteDeltasCheckpointByUtf8SizeBeforeCompletion() {
        // 3000 three-byte characters = 9000 UTF-8 bytes (> 8 KiB) but only 3000 chars (< 8192).
        var chunk = "记".repeat(3000);
        var checkpointed = new java.util.concurrent.atomic.AtomicReference<String>();
        var accepted = accept("q");
        when(llm.chatStream(any(), eq(List.of())))
                .thenReturn(Flux.just(new LlmClient.LlmEvent.Completed("[]")))
                .thenReturn(Flux.concat(
                        Flux.just(new LlmClient.LlmEvent.ContentDelta(chunk)),
                        Mono.fromCallable(() -> {
                            checkpointed.set(stack.assistantText(accepted.ticket().turnId()));
                            return new LlmClient.LlmEvent.Completed(chunk);
                        })));

        chat(accepted);

        assertThat(checkpointed.get()).isEqualTo(chunk);
        assertThat(stack.status(accepted.ticket().turnId())).isEqualTo("COMPLETED");
    }

    @Test
    void lostExecutionRightEndsWithTurnInterruptedAndNoSuccess() {
        var accepted = accept("q");
        var turnId = accepted.ticket().turnId();
        when(llm.chatStream(any(), eq(List.of())))
                .thenReturn(Flux.just(new LlmClient.LlmEvent.Completed("[]")))
                .thenReturn(Flux.concat(
                        Mono.fromCallable(() -> {
                            stack.revokeFence(turnId);
                            return new LlmClient.LlmEvent.ContentDelta("x".repeat(9000));
                        }),
                        Flux.just(new LlmClient.LlmEvent.Completed("never committed"))));

        List<String> lines = chat(accepted);

        assertThat(lines).anySatisfy(l -> assertThat(l).contains("\"type\":\"error\"")
                .contains("\"code\":\"TURN_INTERRUPTED\""));
        assertThat(lines).noneSatisfy(l -> assertThat(l).isEqualTo("never committed\n"));
        assertThat(stack.status(turnId)).isEqualTo("INTERRUPTED");
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
