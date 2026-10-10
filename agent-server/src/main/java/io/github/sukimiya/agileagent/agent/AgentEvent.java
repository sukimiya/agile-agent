package io.github.sukimiya.agileagent.agent;

/** Events emitted by a domain agent while it works on one {@link DomainTask}. */
public sealed interface AgentEvent {
    record ToolCalled(String role, String tool, String argsSummary) implements AgentEvent {}
    record Delta(String role, String text) implements AgentEvent {}
    record Usage(String role, long promptTokens, long completionTokens, long totalTokens) implements AgentEvent {
        public Usage {
            if (promptTokens < 0 || completionTokens < 0 || totalTokens < 0) {
                throw new IllegalArgumentException("Usage token counts must be nonnegative");
            }
        }
    }
    record Finished(String role, String text) implements AgentEvent {}
    record Failed(String role, String reason) implements AgentEvent {}
}
