package io.github.sukimiya.agileagent.spi.capability;

import java.util.Objects;

import com.fasterxml.jackson.databind.JsonNode;

/** A tool call and an argument snapshot isolated from caller mutations. */
public record ToolInvocation(String toolName, String callId, JsonNode arguments) {

    public ToolInvocation {
        if (toolName == null || toolName.isBlank()) {
            throw new IllegalArgumentException("toolName must not be blank");
        }
        if (callId == null || callId.isBlank()) {
            throw new IllegalArgumentException("callId must not be blank");
        }
        arguments = Objects.requireNonNull(arguments, "arguments must not be null").deepCopy();
    }

    @Override
    public JsonNode arguments() {
        return arguments.deepCopy();
    }
}
