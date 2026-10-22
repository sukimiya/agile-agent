package io.github.sukimiya.agileagent.memory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.ObjectMapper;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import io.github.sukimiya.agileagent.agent.DomainAgent;
import io.github.sukimiya.agileagent.agent.DomainTask;
import io.github.sukimiya.agileagent.agent.InProcessTaskDispatcher;
import io.github.sukimiya.agileagent.agent.Orchestrator;
import io.github.sukimiya.agileagent.capability.CapabilityRegistry;
import io.github.sukimiya.agileagent.memory.MemoryModel.TurnTicket;
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
 * Tool execution provenance: a fenced start record before the provider runs, a scoped, redacted outcome, the
 * TOOL_RESULT source tag, and UNCERTAIN for calls whose outcome was never recorded (disconnect, interruption,
 * recovery). A recording failure after an external success stops the turn and never re-executes the tool.
 */
class TurnToolRecorderTest {

    private static final Duration WAIT = Duration.ofSeconds(10);

    protected MemoryTestStack stack;
    private final LlmClient llm = mock(LlmClient.class);
    private String conversation;

    protected MemoryTestFixture newFixture() {
        return MemoryTestFixture.h2();
    }

    protected MemoryTestStack newStack(Function<ToolEventRepository, ToolEventRepository> decorate) {
        return MemoryTestStack.of(newFixture(), decorate::apply);
    }

    @BeforeEach
    void setUp() {
        stack = newStack(r -> r);
        conversation = MemoryTestStack.newConversationId();
    }

    @AfterEach
    void tearDown() {
        stack.close();
    }

    private TurnTicket acceptTurn() {
        return stack.accept(conversation, "问题").ticket();
    }

    private static LlmClient.ToolCall call(String id, String arguments) {
        return new LlmClient.ToolCall(id, "lookup", arguments);
    }

    private DomainTask task(TurnTicket ticket) {
        return new DomainTask("task-1", ticket.conversationId(), "TOOLS", "问题", "sl-SECRET-access-token",
                MemoryTestFixture.identity("alice", "tenant-1"), ticket);
    }

    private DomainAgent agent(AgentCapabilityProvider provider) {
        return new DomainAgent(new CapabilityRegistry(List.of(provider)), stack.toolRecorder());
    }

    private void scriptOneToolCall(String arguments) {
        when(llm.chatStream(any(), any()))
                .thenReturn(Flux.just(new LlmClient.LlmEvent.ToolCallsReady(List.of(call("c1", arguments)))))
                .thenReturn(Flux.just(new LlmClient.LlmEvent.Completed("done")));
    }

    @Test
    void toolStartRecordedBeforeExecute() {
        var ticket = acceptTurn();
        var seenAtExecute = new AtomicReference<List<Map<String, Object>>>();
        var provider = new Tools(invocation -> {
            seenAtExecute.set(stack.toolEventRows(ticket.turnId()));
            return Mono.just(new ToolResult(true, "found"));
        });
        scriptOneToolCall("{\"q\":\"stage\"}");

        agent(provider).run(task(ticket), llm).collectList().block(WAIT);

        assertThat(seenAtExecute.get()).singleElement().satisfies(row -> {
            assertThat(row.get("STATUS")).isEqualTo("STARTED");
            assertThat(row.get("CALL_ID")).isEqualTo("c1");
            assertThat(row.get("TOOL_NAME")).isEqualTo("lookup");
            assertThat(row.get("CAPABILITY")).isEqualTo("TOOLS");
            assertThat((String) row.get("ARGUMENTS")).contains("stage");
        });
        assertThat(stack.toolEventRows(ticket.turnId())).singleElement()
                .satisfies(row -> assertThat(row.get("STATUS")).isEqualTo("SUCCEEDED"));
    }

    @Test
    void toolResultTaggedWithSourceType() {
        var ticket = acceptTurn();
        scriptOneToolCall("{}");

        agent(new Tools(invocation -> Mono.just(new ToolResult(true, "共 48 条记录")))).run(task(ticket), llm)
                .collectList().block(WAIT);

        assertThat(stack.toolEventRows(ticket.turnId())).singleElement().satisfies(row -> {
            assertThat(row.get("SOURCE_TYPE")).isEqualTo(SourceType.TOOL_RESULT.name());
            assertThat(row.get("RESULT")).isEqualTo("共 48 条记录");
            assertThat(row.get("ERROR_TYPE")).isNull();
            assertThat(row.get("FINISHED_AT")).isNotNull();
            assertThat(row.get("TENANT_ID")).isEqualTo("tenant-1");
            assertThat(row.get("SUBJECT")).isEqualTo("alice");
            assertThat(row.get("CONVERSATION_ID")).isEqualTo(conversation);
        });
    }

    @Test
    void failedToolOutcomesRecordAnErrorTypeWithoutDetail() {
        var ticket = acceptTurn();
        when(llm.chatStream(any(), any()))
                .thenReturn(Flux.just(new LlmClient.LlmEvent.ToolCallsReady(List.of(
                        call("reported", "{}"), new LlmClient.ToolCall("unknown", "notAllowed", "{}"),
                        call("bad-json", "{not json"), call("thrown", "{}")))))
                .thenReturn(Flux.just(new LlmClient.LlmEvent.Completed("done")));
        var provider = new Tools(invocation -> switch (invocation.callId()) {
            case "reported" -> Mono.just(new ToolResult(false, "not found"));
            case "thrown" -> Mono.error(new IllegalStateException("jdbc:secret-host failure"));
            default -> Mono.just(new ToolResult(true, "unexpected"));
        });

        agent(provider).run(task(ticket), llm).onErrorResume(e -> Flux.empty()).collectList().block(WAIT);

        var rows = stack.toolEventRows(ticket.turnId());
        assertThat(rows).extracting(r -> r.get("CALL_ID") + ":" + r.get("STATUS") + ":" + r.get("ERROR_TYPE"))
                .containsExactlyInAnyOrder("reported:FAILED:TOOL_REPORTED_FAILURE", "unknown:FAILED:UNKNOWN_TOOL",
                        "bad-json:FAILED:INVALID_ARGUMENTS", "thrown:FAILED:IllegalStateException");
        assertThat(rows).allSatisfy(r -> assertThat(String.valueOf(r.get("RESULT"))).doesNotContain("secret-host"));
        assertThat(provider.executions).hasValue(2);
    }

    @Test
    void sameCallIdUpsertsWithinSameTurnOnly() {
        var first = acceptTurn();
        var recorder = stack.toolRecorder();
        recorder.started(first, "TOOLS", call("c1", "{\"round\":1}")).block(WAIT);
        recorder.started(first, "TOOLS", call("c1", "{\"round\":2}")).block(WAIT);

        assertThat(stack.toolEventRows(first.turnId())).singleElement()
                .satisfies(row -> assertThat((String) row.get("ARGUMENTS")).contains("2"));

        stack.revokeFence(first.turnId());
        var second = acceptTurn();
        recorder.started(second, "TOOLS", call("c1", "{\"round\":3}")).block(WAIT);

        assertThat(stack.toolEventRowsOfConversation(conversation)).hasSize(2)
                .extracting(r -> r.get("TURN_ID")).containsExactlyInAnyOrder(first.turnId(), second.turnId());
        assertThat(stack.toolEventRows(first.turnId())).singleElement()
                .satisfies(row -> assertThat((String) row.get("ARGUMENTS")).contains("2"));
    }

    @Test
    void tokenFieldsAreRedacted() {
        var ticket = acceptTurn();
        var logs = captureLogs();
        try {
            scriptOneToolCall("{\"q\":\"stage\",\"access_token\":\"tok-SECRET-1\",\"auth\":{\"password\":\"pw-SECRET-2\"}}");
            var provider = new Tools(invocation -> Mono.just(new ToolResult(true,
                    "ok Authorization: Bearer abc.SECRET3.def")));

            agent(provider).run(task(ticket), llm).collectList().block(WAIT);

            assertThat(stack.toolEventRows(ticket.turnId())).singleElement().satisfies(row -> {
                assertThat((String) row.get("ARGUMENTS")).contains("stage").doesNotContain("SECRET");
                assertThat((String) row.get("RESULT")).startsWith("ok").doesNotContain("SECRET");
            });
            assertThat(provider.lastArguments.get()).contains("tok-SECRET-1");
            assertThat(logs.list).allSatisfy(e -> assertThat(e.getFormattedMessage())
                    .doesNotContain("SECRET").doesNotContain("sl-SECRET-access-token"));
        } finally {
            detach(logs);
        }
    }

    @Test
    void oversizedToolRecordIsRejectedBeforeExecution() {
        stack.props().setMaxMessageBytes(1024);
        var ticket = acceptTurn();

        assertThatThrownBy(() -> stack.toolRecorder()
                .started(ticket, "TOOLS", call("c1", "{\"q\":\"" + "x".repeat(4096) + "\"}")).block(WAIT))
                .isInstanceOfSatisfying(MemoryException.class,
                        e -> assertThat(e.code()).isEqualTo(MemoryException.ErrorCode.MESSAGE_TOO_LARGE));
        assertThat(stack.toolEventRows(ticket.turnId())).isEmpty();
    }

    @Test
    void lostFenceStopsToolExecution() {
        var ticket = acceptTurn();
        stack.revokeFence(ticket.turnId());
        var provider = new Tools(invocation -> Mono.just(new ToolResult(true, "should not run")));
        scriptOneToolCall("{}");

        assertThatThrownBy(() -> agent(provider).run(task(ticket), llm).collectList().block(WAIT))
                .isInstanceOf(TurnLifecycle.ExecutionRightLostException.class);
        assertThat(provider.executions).hasValue(0);
        assertThat(stack.toolEventRows(ticket.turnId())).isEmpty();
    }

    @Test
    void toolCallOutsideAnAcceptedTurnIsNeverExecuted() {
        var provider = new Tools(invocation -> Mono.just(new ToolResult(true, "should not run")));
        scriptOneToolCall("{}");
        var untracked = new DomainTask("task-1", conversation, "TOOLS", "问题", "tok");

        assertThatThrownBy(() -> agent(provider).run(untracked, llm).collectList().block(WAIT))
                .isInstanceOf(IllegalStateException.class);
        assertThat(provider.executions).hasValue(0);
    }

    @Test
    void externalSuccessDatabaseFailureIsNotRetried() {
        stack.close();
        stack = newStack(repository -> {
            var spied = spy(repository);
            doReturn(Mono.error(new org.springframework.dao.DataAccessResourceFailureException("db down")))
                    .when(spied).finish(any(), any(), any(), any(), any());
            return spied;
        });
        var accepted = stack.accept(conversation, "查一下");
        var provider = new Tools(invocation -> Mono.just(new ToolResult(true, "external write done")));
        when(llm.chatStream(any(), any()))
                .thenReturn(Flux.just(new LlmClient.LlmEvent.Completed("[\"TOOLS\"]")))
                .thenReturn(Flux.just(new LlmClient.LlmEvent.ToolCallsReady(List.of(call("c1", "{}")))))
                .thenReturn(Flux.just(new LlmClient.LlmEvent.ToolCallsReady(List.of(call("c1", "{}")))))
                .thenReturn(Flux.just(new LlmClient.LlmEvent.Completed("aggregate")));

        var lines = orchestrator(provider).chat(accepted, "tok", null, llm).collectList().block(WAIT);

        assertThat(provider.executions).hasValue(1);
        verify(llm, times(2)).chatStream(any(), any());
        assertThat(lines).anySatisfy(l -> assertThat(l).contains("\"code\":\"STORAGE_UNAVAILABLE\""));
        var turnId = accepted.ticket().turnId();
        assertThat(stack.status(turnId)).isEqualTo("FAILED");
        assertThat(stack.toolEventRows(turnId)).singleElement()
                .satisfies(row -> assertThat(row.get("STATUS")).isEqualTo("UNCERTAIN"));
    }

    @Test
    void cancelMarksInFlightToolCallUncertain() throws Exception {
        var accepted = stack.accept(conversation, "查一下");
        var executing = new CountDownLatch(1);
        var provider = new Tools(invocation -> Mono.<ToolResult>never().doOnSubscribe(s -> executing.countDown()));
        when(llm.chatStream(any(), any()))
                .thenReturn(Flux.just(new LlmClient.LlmEvent.Completed("[\"TOOLS\"]")))
                .thenReturn(Flux.just(new LlmClient.LlmEvent.ToolCallsReady(List.of(call("c1", "{}")))));

        var subscription = orchestrator(provider).chat(accepted, "tok", null, llm).subscribe();
        assertThat(executing.await(5, TimeUnit.SECONDS)).isTrue();
        subscription.dispose();

        var turnId = accepted.ticket().turnId();
        await().atMost(WAIT).untilAsserted(() -> assertThat(stack.status(turnId)).isEqualTo("CANCELLED"));
        assertThat(stack.toolEventRows(turnId)).singleElement()
                .satisfies(row -> assertThat(row.get("STATUS")).isEqualTo("UNCERTAIN"));
    }

    @Test
    void recoveryMarksOpenToolCallsUncertain() {
        var ticket = acceptTurn();
        stack.toolRecorder().started(ticket, "TOOLS", call("open", "{}")).block(WAIT);
        stack.toolRecorder().started(ticket, "TOOLS", call("done", "{}")).block(WAIT);
        stack.toolRecorder().finished(ticket, "TOOLS", "done", "ok", true).block(WAIT);
        stack.expireLease(ticket.turnId());

        assertThat(stack.recoverExpired()).isEqualTo(1);

        assertThat(stack.toolEventRows(ticket.turnId()))
                .extracting(r -> r.get("CALL_ID") + ":" + r.get("STATUS"))
                .containsExactlyInAnyOrder("open:UNCERTAIN", "done:SUCCEEDED");
    }

    @Test
    void markUncertainOnlyAffectsOpenCallsOfThatTurnAndALaterOutcomeWins() {
        var ticket = acceptTurn();
        var recorder = stack.toolRecorder();
        recorder.started(ticket, "TOOLS", call("c1", "{}")).block(WAIT);
        recorder.started(ticket, "TOOLS", call("c2", "{}")).block(WAIT);
        recorder.finished(ticket, "TOOLS", "c2", "ok", true).block(WAIT);

        recorder.markUncertain(ticket, "c1").block(WAIT);
        recorder.markUncertain(ticket, "c2").block(WAIT);
        assertThat(stack.toolEventRows(ticket.turnId())).extracting(r -> r.get("CALL_ID") + ":" + r.get("STATUS"))
                .containsExactlyInAnyOrder("c1:UNCERTAIN", "c2:SUCCEEDED");

        recorder.finished(ticket, "TOOLS", "c1", "late result", true).block(WAIT);
        assertThat(stack.toolEventRows(ticket.turnId())).extracting(r -> r.get("CALL_ID") + ":" + r.get("STATUS"))
                .containsExactlyInAnyOrder("c1:SUCCEEDED", "c2:SUCCEEDED");

        var foreign = new TurnTicket(TurnTestSupport.ALICE_OTHER_TENANT, conversation, ticket.turnId(), ticket.fence());
        recorder.started(ticket, "TOOLS", call("c3", "{}")).block(WAIT);
        recorder.markUncertain(foreign, "c3").block(WAIT);
        assertThat(stack.toolEventRows(ticket.turnId())).anySatisfy(r -> {
            assertThat(r.get("CALL_ID")).isEqualTo("c3");
            assertThat(r.get("STATUS")).isEqualTo("STARTED");
        });
    }

    private Orchestrator orchestrator(AgentCapabilityProvider provider) {
        var registry = new CapabilityRegistry(List.of(provider));
        var dispatcher = new InProcessTaskDispatcher(new DomainAgent(registry, stack.toolRecorder()));
        return new Orchestrator(dispatcher, registry, stack.lifecycle(), stack.history());
    }

    private static ListAppender<ILoggingEvent> captureLogs() {
        var appender = new ListAppender<ILoggingEvent>();
        appender.start();
        ((Logger) LoggerFactory.getLogger("io.github.sukimiya.agileagent")).addAppender(appender);
        return appender;
    }

    private static void detach(ListAppender<ILoggingEvent> appender) {
        ((Logger) LoggerFactory.getLogger("io.github.sukimiya.agileagent")).detachAppender(appender);
    }

    /** One "lookup" tool whose behaviour is scripted per invocation. */
    private static final class Tools implements AgentCapabilityProvider {
        private final Function<ToolInvocation, Mono<ToolResult>> behaviour;
        final AtomicInteger executions = new AtomicInteger();
        final AtomicReference<String> lastArguments = new AtomicReference<>();

        Tools(Function<ToolInvocation, Mono<ToolResult>> behaviour) {
            this.behaviour = behaviour;
        }

        @Override public String code() { return "TOOLS"; }
        @Override public String displayName() { return "Tools"; }
        @Override public String routingDescription() { return "test tools"; }

        @Override
        public Mono<CapabilityDefinition> definition(AgentExecutionContext context) {
            var schema = new ObjectMapper().createObjectNode().put("type", "object");
            return Mono.just(new CapabilityDefinition("tools", List.of(new ToolDefinition("lookup", "look up", schema))));
        }

        @Override
        public Mono<ToolResult> execute(ToolInvocation invocation, AgentExecutionContext context) {
            return Mono.defer(() -> {
                executions.incrementAndGet();
                lastArguments.set(invocation.arguments().toString());
                return behaviour.apply(invocation);
            });
        }
    }
}
