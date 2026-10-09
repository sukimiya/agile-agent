package io.github.sukimiya.agileagent.spi.capability;

import com.fasterxml.jackson.databind.JsonNode;

/** A tool call and a normalized JSON argument snapshot isolated from caller mutations. */
public record ToolInvocation(String toolName, String callId, JsonNode arguments) {

    public ToolInvocation {
        if (toolName == null || toolName.isBlank()) {
            throw new IllegalArgumentException("toolName must not be blank");
        }
        if (callId == null || callId.isBlank()) {
            throw new IllegalArgumentException("callId must not be blank");
        }
        arguments = JsonSnapshots.copyOf(arguments, "arguments");
    }

    @Override
    public JsonNode arguments() {
        return arguments.deepCopy();
    }
}
