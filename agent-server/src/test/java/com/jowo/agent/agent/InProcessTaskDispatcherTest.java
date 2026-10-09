package com.jowo.agent.agent;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.time.Duration;
import java.util.List;

import org.junit.jupiter.api.Test;

import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

class InProcessTaskDispatcherTest {

    @Test
    void mergesSuccessfulDomainResultsFromAllTasks() {
        DomainAgent domainAgent = mock(DomainAgent.class);
        var mesTask = new DomainTask("t1", "conv-1", "MES", "q1", "tok");
        var crmTask = new DomainTask("t2", "conv-1", "CRM", "q2", "tok");
        when(domainAgent.run(mesTask)).thenReturn(Flux.just(new AgentEvent.Finished("MES", "mes 结果")));
        when(domainAgent.run(crmTask)).thenReturn(Flux.just(new AgentEvent.Finished("CRM", "crm 结果")));
        var dispatcher = new InProcessTaskDispatcher(domainAgent);

        List<AgentEvent> events = dispatcher.dispatch(List.of(mesTask, crmTask)).collectList().block();

        assertThat(events).containsExactlyInAnyOrder(
                new AgentEvent.Finished("MES", "mes 结果"),
                new AgentEvent.Finished("CRM", "crm 结果"));
    }

    @Test
    void oneTaskThrowingDoesNotSuppressTheOtherTasksResult() {
        DomainAgent domainAgent = mock(DomainAgent.class);
        var mesTask = new DomainTask("t1", "conv-1", "MES", "q1", "tok");
        var crmTask = new DomainTask("t2", "conv-1", "CRM", "q2", "tok");
        when(domainAgent.run(mesTask)).thenReturn(Flux.error(new RuntimeException("boom")));
        when(domainAgent.run(crmTask)).thenReturn(Flux.just(new AgentEvent.Finished("CRM", "crm 结果")));
        var dispatcher = new InProcessTaskDispatcher(domainAgent);

        List<AgentEvent> events = dispatcher.dispatch(List.of(mesTask, crmTask)).collectList().block();

        assertThat(events).contains(new AgentEvent.Finished("CRM", "crm 结果"));
        assertThat(events).anySatisfy(e -> assertThat(e).isInstanceOfSatisfying(AgentEvent.Failed.class,
                failed -> assertThat(failed.role()).isEqualTo("MES")));
    }

    @Test
    void slowTaskTimesOutAndIsReportedAsFailedWithoutBlockingOtherResults() {
        DomainAgent domainAgent = mock(DomainAgent.class);
        var slowTask = new DomainTask("t1", "conv-1", "MES", "q1", "tok");
        var fastTask = new DomainTask("t2", "conv-1", "CRM", "q2", "tok");
        when(domainAgent.run(slowTask)).thenReturn(
                Flux.<AgentEvent>just(new AgentEvent.Finished("MES", "太晚了")).delayElements(Duration.ofSeconds(120)));
        when(domainAgent.run(fastTask)).thenReturn(Flux.just(new AgentEvent.Finished("CRM", "crm 结果")));
        var dispatcher = new InProcessTaskDispatcher(domainAgent, Duration.ofMillis(50));

        StepVerifier.create(dispatcher.dispatch(List.of(slowTask, fastTask)).collectList())
                .assertNext(events -> {
                    assertThat(events).contains(new AgentEvent.Finished("CRM", "crm 结果"));
                    assertThat(events).anySatisfy(e -> assertThat(e).isInstanceOfSatisfying(AgentEvent.Failed.class,
                            failed -> assertThat(failed.role()).isEqualTo("MES")));
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
