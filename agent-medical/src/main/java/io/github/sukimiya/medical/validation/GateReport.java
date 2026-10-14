package io.github.sukimiya.medical.validation;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * The six-gate verdict as an administrative audit document: which gate produced which
 * violations, which gates were short-circuited, and the version fence the verdict belongs to.
 * Hostnames may appear here (admin side); credentials must never (spec §7). Persisted into
 * hospital_capability_packages.last_gate_report (V10). Gate order is preserved by the
 * LinkedHashMap copy in the compact constructor.
 */
public record GateReport(
        String hospitalCode,
        long approvalVersion,
        boolean passed,
        Map<String, List<String>> violations,
        List<String> skippedGates) {

    private static final ObjectMapper JSON = new ObjectMapper();

    public GateReport {
        Map<String, List<String>> copied = new LinkedHashMap<>();
        if (violations != null) {
            violations.forEach((gate, found) ->
                    copied.put(gate, found == null ? List.of() : List.copyOf(found)));
        }
        violations = Collections.unmodifiableMap(copied);
        skippedGates = skippedGates == null ? List.of() : List.copyOf(skippedGates);
    }

    public String toJson() {
        try {
            return JSON.writeValueAsString(this);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("gate report is not serializable", e);
        }
    }

    /** Parse failure is data: a corrupt stored report simply means "no green verdict". */
    public static Optional<GateReport> fromJson(String json) {
        if (json == null || json.isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.ofNullable(JSON.readValue(json, GateReport.class));
        } catch (JsonProcessingException e) {
            return Optional.empty();
        }
    }
}
