package io.github.sukimiya.medical.rules;

/** One symptom→disease mapping entry from submitTriage (Plan 4 spec §5). */
public record SymptomDisease(String symptom, String disease) {
}
