package io.github.sukimiya.medical.rules;

/** kind = SAFETY_PATTERN: approved data increment for the composite safety screen. */
public record SafetyPatternRuleContent(
        int schemaVersion, String kind, SafetyPatternCondition condition, SafetyPatternAction action)
        implements MedicalRuleContent {
}
