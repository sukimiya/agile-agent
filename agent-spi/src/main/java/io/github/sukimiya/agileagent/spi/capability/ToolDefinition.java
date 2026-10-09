package io.github.sukimiya.agileagent.spi.capability;

import java.util.Objects;

import com.fasterxml.jackson.databind.JsonNode;

/** One LLM tool, with a normalized JSON Schema snapshot isolated from caller mutations. */
public record ToolDefinition(String name, String description, JsonNode parametersSchema) {

    public ToolDefinition {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("tool name must not be blank");
        }
        Objects.requireNonNull(description, "description must not be null");
        parametersSchema = JsonSnapshots.copyOf(parametersSchema, "parametersSchema");
    }

    @Override
    public JsonNode parametersSchema() {
        return parametersSchema.deepCopy();
    }
}
