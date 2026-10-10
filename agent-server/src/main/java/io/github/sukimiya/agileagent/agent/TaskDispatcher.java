package io.github.sukimiya.agileagent.agent;

import java.util.List;

import io.github.sukimiya.agileagent.service.LlmClient;
import reactor.core.publisher.Flux;

/**
 * Runs a batch of {@link DomainTask}s with the resolved tenant LLM and streams back their events. The in-process
 * implementation ({@link InProcessTaskDispatcher}) fans out with {@code Flux.merge};
 * a future Redis Streams implementation can distribute tasks across instances behind
 * the same interface.
 */
public interface TaskDispatcher {
    Flux<AgentEvent> dispatch(List<DomainTask> tasks, LlmClient llm);
}
