package com.jowo.agent.agent;

/** Events emitted by a domain agent while it works on one {@link DomainTask}. */
public sealed interface AgentEvent {
    record ToolCalled(String role, String tool, String argsSummary) implements AgentEvent {}
    record Delta(String role, String text) implements AgentEvent {}
    record Finished(String role, String text) implements AgentEvent {}
    record Failed(String role, String reason) implements AgentEvent {}
}
