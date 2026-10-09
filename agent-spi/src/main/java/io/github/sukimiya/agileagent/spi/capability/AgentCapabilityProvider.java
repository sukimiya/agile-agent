package io.github.sukimiya.agileagent.spi.capability;

import reactor.core.publisher.Mono;

/** Supplies identity-aware prompts, tools, and execution for one capability. */
public interface AgentCapabilityProvider {
    String code();
    String displayName();
    String routingDescription();
    Mono<CapabilityDefinition> definition(AgentExecutionContext context);
    Mono<ToolResult> execute(ToolInvocation invocation, AgentExecutionContext context);
}
