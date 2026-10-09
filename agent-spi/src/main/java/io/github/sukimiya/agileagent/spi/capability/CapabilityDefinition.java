package io.github.sukimiya.agileagent.spi.capability;

import java.util.List;
import java.util.Objects;

/** The system prompt and tools resolved for an execution context. */
public record CapabilityDefinition(String systemPrompt, List<ToolDefinition> tools) {

    public CapabilityDefinition {
        Objects.requireNonNull(systemPrompt, "systemPrompt must not be null");
        tools = List.copyOf(tools);
    }
}
