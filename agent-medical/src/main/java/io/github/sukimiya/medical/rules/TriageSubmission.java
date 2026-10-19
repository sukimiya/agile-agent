package io.github.sukimiya.medical.rules;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * The submitTriage parameter object (Plan 4 spec §5, schema v2): a code-curated triage
 * category plus symptom→disease pairs; notes are audit-only and never a matching signal.
 * Parsed strictly — unknown fields, missing required fields, or an invalid category all
 * yield empty (safe failure: no hit, no pass-through of a fabricated conclusion).
 */
public record TriageSubmission(TriageCategory triageCategory,
        List<SymptomDisease> symptomToDisease, String notes) {

    private static final Set<String> KNOWN_FIELDS =
            Set.of("triageCategory", "symptomToDisease", "notes");
    private static final Set<String> KNOWN_PAIR_FIELDS = Set.of("symptom", "disease");

    public static Optional<TriageSubmission> parse(JsonNode args) {
        if (args == null || !args.isObject()) {
            return Optional.empty();
        }
        var fieldNames = args.fieldNames();
        while (fieldNames.hasNext()) {
            if (!KNOWN_FIELDS.contains(fieldNames.next())) {
                return Optional.empty();
            }
        }
        JsonNode categoryNode = args.get("triageCategory");
        if (categoryNode == null || !categoryNode.isTextual()) {
            return Optional.empty();
        }
        TriageCategory category;
        try {
            category = TriageCategory.valueOf(categoryNode.asText());
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
        JsonNode pairsNode = args.get("symptomToDisease");
        if (pairsNode == null || !pairsNode.isArray()) {
            return Optional.empty();
        }
        List<SymptomDisease> pairs = new ArrayList<>();
        for (JsonNode pair : pairsNode) {
            var parsedPair = parsePair(pair);
            if (parsedPair.isEmpty()) {
                return Optional.empty();
            }
            pairs.add(parsedPair.get());
        }
        String notes = null;
        JsonNode notesNode = args.get("notes");
        if (notesNode != null && !notesNode.isNull()) {
            if (!notesNode.isTextual()) {
                return Optional.empty();
            }
            notes = notesNode.asText();
        }
        return Optional.of(new TriageSubmission(category, List.copyOf(pairs), notes));
    }

    private static Optional<SymptomDisease> parsePair(JsonNode pair) {
        if (pair == null || !pair.isObject()) {
            return Optional.empty();
        }
        var fieldNames = pair.fieldNames();
        while (fieldNames.hasNext()) {
            if (!KNOWN_PAIR_FIELDS.contains(fieldNames.next())) {
                return Optional.empty();
            }
        }
        JsonNode symptom = pair.get("symptom");
        JsonNode disease = pair.get("disease");
        if (symptom == null || !symptom.isTextual() || disease == null || !disease.isTextual()) {
            return Optional.empty();
        }
        return Optional.of(new SymptomDisease(symptom.asText(), disease.asText()));
    }
}
