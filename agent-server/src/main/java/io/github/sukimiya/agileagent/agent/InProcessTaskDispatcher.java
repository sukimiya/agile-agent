package io.github.sukimiya.agileagent.agent;

import java.time.Duration;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import io.github.sukimiya.agileagent.service.LlmClient;
import reactor.core.publisher.Flux;

/**
 * Runs {@link DomainTask}s in the current JVM with {@code Flux.merge}, each independently
 * timed out so one stuck domain can't stall the rest. A future Redis Streams implementation
 * can swap in behind {@link TaskDispatcher} without Orchestrator changes.
 */
@Component
public class InProcessTaskDispatcher implements TaskDispatcher {

    private static final Logger log = LoggerFactory.getLogger(InProcessTaskDispatcher.class);
    private static final Duration DEFAULT_TASK_TIMEOUT = Duration.ofSeconds(90);

    private final DomainAgent domainAgent;
    private final Duration taskTimeout;

    @Autowired
    public InProcessTaskDispatcher(DomainAgent domainAgent) {
        this(domainAgent, DEFAULT_TASK_TIMEOUT);
    }

    InProcessTaskDispatcher(DomainAgent domainAgent, Duration taskTimeout) {
        this.domainAgent = domainAgent;
        this.taskTimeout = taskTimeout;
    }

    @Override
    public Flux<AgentEvent> dispatch(List<DomainTask> tasks, LlmClient llm) {
        return Flux.merge(tasks.stream().map(task -> runOne(task, llm)).toList());
    }

    private Flux<AgentEvent> runOne(DomainTask task, LlmClient llm) {
        log.info("dispatch role={} taskId={}", task.roleCode(), task.taskId());
        return domainAgent.run(task, llm)
                .timeout(taskTimeout)
                .onErrorResume(e -> {
                    log.warn("domain task failed role={} taskId={} error={}", task.roleCode(), task.taskId(), e.toString());
                    return Flux.just(new AgentEvent.Failed(task.roleCode(), describe(e)));
                });
    }

    private static String describe(Throwable e) {
        if (e instanceof java.util.concurrent.TimeoutException) {
            return "查询超时";
        }
        return e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
    }
}
