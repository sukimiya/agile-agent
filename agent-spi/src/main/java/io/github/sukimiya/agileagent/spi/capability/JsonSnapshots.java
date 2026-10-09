package io.github.sukimiya.agileagent.spi.capability;

import java.util.Objects;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/** Converts embedded binary/POJO values into ordinary, defensively copyable JSON. */
final class JsonSnapshots {

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS);

    private JsonSnapshots() {}

    static JsonNode copyOf(JsonNode value, String fieldName) {
        Objects.requireNonNull(value, fieldName + " must not be null");
        try {
            return MAPPER.readTree(MAPPER.writeValueAsString(value));
        } catch (JsonProcessingException ignored) {
            // Serialization failures can contain payload data; do not expose their messages or causes.
            throw new IllegalArgumentException(fieldName + " must be serializable JSON");
        }
    }
}
