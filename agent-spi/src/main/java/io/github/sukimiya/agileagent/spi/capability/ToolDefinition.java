package io.github.sukimiya.agileagent.spi.capability;

import java.util.Objects;

import com.fasterxml.jackson.databind.JsonNode;

/** One LLM tool, with a JSON Schema snapshot isolated from caller mutations. */
public record ToolDefinition(String name, String description, JsonNode parametersSchema) {

    public ToolDefinition {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("tool name must not be blank");
        }
        Objects.requireNonNull(description, "description must not be null");
        parametersSchema = Objects.requireNonNull(parametersSchema, "parametersSchema must not be null").deepCopy();
    }

    @Override
    public JsonNode parametersSchema() {
        return parametersSchema.deepCopy();
    }
}
