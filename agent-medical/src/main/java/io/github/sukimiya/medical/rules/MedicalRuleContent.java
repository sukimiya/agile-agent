package io.github.sukimiya.medical.rules;

/**
 * Rule schema v1 content (Plan 4 spec §6): a versioned, sealed vocabulary. Adding a rule
 * kind means a new permitted record; upgrading the schema means schemaVersion+1 with a
 * codec that still accepts v1 knowledge bases.
 */
public sealed interface MedicalRuleContent
        permits EmergencyRuleContent, SafetyPatternRuleContent {

    int schemaVersion();

    String kind();
}
