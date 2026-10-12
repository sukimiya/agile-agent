package io.github.sukimiya.medical.hospital;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * One tool of a hospital capability package: a name from the unified catalog, an LLM-facing
 * description, a JSON Schema for parameters, and the curated transport binding.
 */
public record HospitalToolSpec(String name, String description, JsonNode parametersSchema,
        TransportBinding transport) {

    /** Mirrors agent-spi's ToolDefinition deep-copy convention: JsonNode object/array nodes are mutable. */
    public HospitalToolSpec {
        parametersSchema = parametersSchema == null ? null : parametersSchema.deepCopy();
    }
}
