package io.github.sukimiya.medical.rules;

import java.util.List;
import java.util.Optional;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * The rule-review verdict as an administrative audit document, mirroring GateReport:
 * persisted into medical_rules.last_validation_report and re-checked at approve time
 * against the current approval_version (green-report-at-current-version fence).
 * Parse failure is data: a corrupt stored report simply means "no green verdict".
 */
public record RuleValidationReport(
        String ruleCode, long approvalVersion, boolean passed, List<String> violations) {

    private static final ObjectMapper JSON = new ObjectMapper();

    public RuleValidationReport {
        violations = violations == null ? List.of() : List.copyOf(violations);
    }

    public String toJson() {
        try {
            return JSON.writeValueAsString(this);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("validation report is not serializable", e);
        }
    }

    public static Optional<RuleValidationReport> fromJson(String json) {
        if (json == null || json.isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.ofNullable(JSON.readValue(json, RuleValidationReport.class));
        } catch (JsonProcessingException e) {
            return Optional.empty();
        }
    }
}
