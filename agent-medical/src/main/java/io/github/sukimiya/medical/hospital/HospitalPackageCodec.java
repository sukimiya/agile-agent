package io.github.sukimiya.medical.hospital;

import java.util.Optional;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Parses and serializes package content JSON. Parse failure is data, not an exception:
 * callers fail closed and skip the package (spec §5 status gate spirit).
 */
public final class HospitalPackageCodec {

    private static final ObjectMapper JSON = new ObjectMapper();

    public Optional<HospitalPackageContent> parse(String contentJson) {
        if (contentJson == null || contentJson.isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.of(JSON.readValue(contentJson, HospitalPackageContent.class));
        } catch (JsonProcessingException e) {
            return Optional.empty();
        }
    }

    public String write(HospitalPackageContent content) {
        try {
            return JSON.writeValueAsString(content);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("package content is not serializable", e);
        }
    }
}
