package io.github.sukimiya.agileagent.agent;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.time.Duration;
import java.util.List;

import org.junit.jupiter.api.Test;

import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

class InProcessTaskDispatcherTest {

    @Test
    void mergesSuccessfulCapabilityResultsFromAllTasks() {
        DomainAgent domainAgent = mock(DomainAgent.class);
        var alphaTask = new DomainTask("t1", "conv-1", "ALPHA", "q1", "tok");
        var betaTask = new DomainTask("t2", "conv-1", "BETA", "q2", "tok");
        when(domainAgent.run(alphaTask)).thenReturn(Flux.just(new AgentEvent.Finished("ALPHA", "结果一")));
        when(domainAgent.run(betaTask)).thenReturn(Flux.just(new AgentEvent.Finished("BETA", "结果二")));
        var dispatcher = new InProcessTaskDispatcher(domainAgent);

        List<AgentEvent> events = dispatcher.dispatch(List.of(alphaTask, betaTask)).collectList().block();

        assertThat(events).containsExactlyInAnyOrder(
                new AgentEvent.Finished("ALPHA", "结果一"),
                new AgentEvent.Finished("BETA", "结果二"));
    }

    @Test
    void oneTaskThrowingDoesNotSuppressTheOtherTasksResult() {
        DomainAgent domainAgent = mock(DomainAgent.class);
        var alphaTask = new DomainTask("t1", "conv-1", "ALPHA", "q1", "tok");
        var betaTask = new DomainTask("t2", "conv-1", "BETA", "q2", "tok");
        when(domainAgent.run(alphaTask)).thenReturn(Flux.error(new RuntimeException("boom")));
        when(domainAgent.run(betaTask)).thenReturn(Flux.just(new AgentEvent.Finished("BETA", "结果二")));
        var dispatcher = new InProcessTaskDispatcher(domainAgent);

        List<AgentEvent> events = dispatcher.dispatch(List.of(alphaTask, betaTask)).collectList().block();

        assertThat(events).contains(new AgentEvent.Finished("BETA", "结果二"));
        assertThat(events).anySatisfy(e -> assertThat(e).isInstanceOfSatisfying(AgentEvent.Failed.class,
                failed -> assertThat(failed.role()).isEqualTo("ALPHA")));
    }

    @Test
    void slowTaskTimesOutAndIsReportedAsFailedWithoutBlockingOtherResults() {
        DomainAgent domainAgent = mock(DomainAgent.class);
        var slowTask = new DomainTask("t1", "conv-1", "ALPHA", "q1", "tok");
        var fastTask = new DomainTask("t2", "conv-1", "BETA", "q2", "tok");
        when(domainAgent.run(slowTask)).thenReturn(
                Flux.<AgentEvent>just(new AgentEvent.Finished("ALPHA", "太晚了")).delayElements(Duration.ofSeconds(120)));
        when(domainAgent.run(fastTask)).thenReturn(Flux.just(new AgentEvent.Finished("BETA", "结果二")));
        var dispatcher = new InProcessTaskDispatcher(domainAgent, Duration.ofMillis(50));

        StepVerifier.create(dispatcher.dispatch(List.of(slowTask, fastTask)).collectList())
                .assertNext(events -> {
                    assertThat(events).contains(new AgentEvent.Finished("BETA", "结果二"));
                    assertThat(events).anySatisfy(e -> assertThat(e).isInstanceOfSatisfying(AgentEvent.Failed.class,
                            failed -> assertThat(failed.role()).isEqualTo("ALPHA")));
                })
                .verifyComplete();
    }

    @Test
    void emptyTaskListYieldsEmptyFlux() {
        DomainAgent domainAgent = mock(DomainAgent.class);
        var dispatcher = new InProcessTaskDispatcher(domainAgent);

        List<AgentEvent> events = dispatcher.dispatch(List.of()).collectList().block();

        assertThat(events).isEmpty();
    }
}
