package io.github.sukimiya.medical.rules;

/** kind = EMERGENCY: curated triage categories / disease names → E-call action. */
public record EmergencyRuleContent(
        int schemaVersion, String kind, EmergencyCondition condition, EmergencyAction action)
        implements MedicalRuleContent {
}
