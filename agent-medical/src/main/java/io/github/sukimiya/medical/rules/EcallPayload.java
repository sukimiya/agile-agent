package io.github.sukimiya.medical.rules;

import java.util.List;
import java.util.Optional;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * The JSON payload stored in medical_human_tasks.arguments_json for ECALL rows:
 * everything the restricted-state prompts and the escalation loop need, snapshotted from
 * the winning rule at creation time (later rule edits never mutate an open E-call). Reads
 * are lenient (empty on any failure) so consumers can fail closed on corrupt rows.
 */
public record EcallPayload(
        String ruleCode,
        String triageCategory,
        String notes,
        int priority,
        int slaSeconds,
        List<Integer> escalationLevels,
        String awaitingTakeoverScript,
        String takenOverScript) {

    private static final ObjectMapper JSON = new ObjectMapper();

    public String toJson() {
        try {
            return JSON.writeValueAsString(this);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("EcallPayload must be serializable", e);
        }
    }

    public static Optional<EcallPayload> fromJson(String json) {
        if (json == null || json.isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.of(JSON.readValue(json, EcallPayload.class));
        } catch (JsonProcessingException e) {
            return Optional.empty();
        }
    }
}
