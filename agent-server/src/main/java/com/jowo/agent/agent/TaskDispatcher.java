package com.jowo.agent.agent;

import java.util.List;

import reactor.core.publisher.Flux;

/**
 * Runs a batch of {@link DomainTask}s and streams back their events. The in-process
 * implementation ({@link InProcessTaskDispatcher}) fans out with {@code Flux.merge};
 * a future Redis Streams implementation can distribute tasks across instances behind
 * the same interface.
 */
public interface TaskDispatcher {
    Flux<AgentEvent> dispatch(List<DomainTask> tasks);
}
