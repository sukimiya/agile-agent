package io.github.sukimiya.medical.rules;

import java.util.Optional;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Strict rule-content codec (Plan 4 spec §6), same posture as HospitalPackageCodec:
 * Jackson defaults keep FAIL_ON_UNKNOWN_PROPERTIES on, so unknown fields are violations,
 * not ignorable noise. Two-phase parse — read the tree, branch on "kind", then bind the
 * concrete record — because the sealed interface carries no Jackson type metadata.
 * Parse failure is always data (Optional.empty), never an exception.
 */
public final class MedicalRuleCodec {

    public static final int SCHEMA_VERSION = 1;

    private static final ObjectMapper JSON = new ObjectMapper();

    public Optional<MedicalRuleContent> parse(String contentJson) {
        if (contentJson == null || contentJson.isBlank()) {
            return Optional.empty();
        }
        try {
            JsonNode tree = JSON.readTree(contentJson);
            JsonNode kindNode = tree.get("kind");
            if (kindNode == null || !kindNode.isTextual()) {
                return Optional.empty();
            }
            MedicalRuleContent content = switch (kindNode.asText()) {
                case "EMERGENCY" -> JSON.treeToValue(tree, EmergencyRuleContent.class);
                case "SAFETY_PATTERN" -> JSON.treeToValue(tree, SafetyPatternRuleContent.class);
                default -> null;
            };
            return Optional.ofNullable(content);
        } catch (JsonProcessingException | IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    public String write(MedicalRuleContent content) {
        if (content == null) {
            throw new IllegalArgumentException("rule content is required");
        }
        try {
            return JSON.writeValueAsString(content);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("rule content is not serializable", e);
        }
    }
}
