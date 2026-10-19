package io.github.sukimiya.medical.rules;

import java.util.List;

/** Regex/keyword patterns (Chinese + English) and the label used in violation text. */
public record SafetyPatternCondition(List<String> patterns, String fieldLabel) {
}
