package io.github.sukimiya.medical.rules;

import java.util.List;

/** Match inputs: triage categories and disease names, both exact-match curated lists. */
public record EmergencyCondition(List<String> triageCategories, List<String> diseaseNames) {
}
