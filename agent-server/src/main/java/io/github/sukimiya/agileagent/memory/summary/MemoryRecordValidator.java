package io.github.sukimiya.agileagent.memory.summary;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.github.sukimiya.agileagent.memory.ToolRecordSanitizer;
import io.github.sukimiya.agileagent.memory.summary.MemoryRecord.Entity;
import io.github.sukimiya.agileagent.memory.summary.MemoryRecord.KeyFact;
import io.github.sukimiya.agileagent.memory.summary.MemoryRecord.MemoryType;

/**
 * Checks a model's {@code memory-record-v1} output (D18, D23) against the turn it was written from.
 * <ul>
 * <li>Schema: one JSON object with a non-blank string {@code summary}, arrays {@code entities},
 * {@code key_facts} and {@code keywords}, a boolean {@code decision} and {@code memory_type} FACT or EVENT (any
 * case). Anything else is an {@link InvalidRecordException}; the caller retries and skips the turn after
 * {@code max-invalid-attempts}.</li>
 * <li>Verbatim (strict): an entity's {@code name} and a key fact's {@code text} must occur exactly (case and
 * spacing included) in the source turn, else the item is dropped. The summary and keywords are not checked
 * against the source.</li>
 * <li>Limits: the summary is cut to {@value #MAX_SUMMARY_CHARS} characters, at most {@value #MAX_ITEMS} entities
 * and key facts and {@value #MAX_KEYWORDS} distinct non-blank keywords are kept; an unknown entity type or fact
 * kind becomes OTHER; malformed items are dropped.</li>
 * <li>Every kept string is masked with {@link ToolRecordSanitizer} (passwords, keys, tokens).</li>
 * </ul>
 */
public final class MemoryRecordValidator {

    public static final int MAX_SUMMARY_CHARS = 80;
    public static final int MAX_ITEMS = 20;
    public static final int MAX_KEYWORDS = 10;

    static final Set<String> ENTITY_TYPES = Set.of("PERSON", "ORG", "PLACE", "PRODUCT", "PROJECT", "OTHER");
    static final Set<String> FACT_KINDS = Set.of("CODE", "NUMBER", "VERSION", "NAME", "DATE", "AMOUNT", "OTHER");

    private static final ObjectMapper JSON = new ObjectMapper();

    /** The validated model-written part of a record. */
    public record Content(String summary, List<Entity> entities, List<KeyFact> keyFacts, boolean decision,
            List<String> keywords, MemoryType memoryType) {}

    /** The output is not a {@code memory-record-v1} object; only the reason category is in the message. */
    public static final class InvalidRecordException extends RuntimeException {
        public InvalidRecordException(String message) {
            super(message, null, false, false);
        }
    }

    private MemoryRecordValidator() {}

    public static Content validate(String output, String source) {
        JsonNode root;
        try {
            root = output == null || output.isBlank() ? null : JSON.readTree(output);
        } catch (Exception e) {
            throw new InvalidRecordException("not JSON");
        }
        if (root == null || !root.isObject()) {
            throw new InvalidRecordException("not a JSON object");
        }
        var summary = root.get("summary");
        if (summary == null || !summary.isTextual() || summary.asText().isBlank()) {
            throw new InvalidRecordException("summary missing");
        }
        var decision = root.get("decision");
        if (decision == null || !decision.isBoolean()) {
            throw new InvalidRecordException("decision missing");
        }
        var type = root.get("memory_type");
        MemoryType memoryType;
        try {
            memoryType = MemoryType.valueOf(type == null || !type.isTextual() ? ""
                    : type.asText().trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new InvalidRecordException("memory_type invalid");
        }
        var entities = array(root, "entities");
        var facts = array(root, "key_facts");
        var keywords = array(root, "keywords");
        var text = source == null ? "" : source;

        var keptEntities = new ArrayList<Entity>();
        for (var item : entities) {
            var name = item.path("name");
            if (keptEntities.size() < MAX_ITEMS && name.isTextual() && !name.asText().isEmpty()
                    && text.contains(name.asText())) {
                keptEntities.add(new Entity(mask(name.asText()), code(item.path("type"), ENTITY_TYPES)));
            }
        }
        var keptFacts = new ArrayList<KeyFact>();
        for (var item : facts) {
            var fact = item.path("text");
            if (keptFacts.size() < MAX_ITEMS && fact.isTextual() && !fact.asText().isEmpty()
                    && text.contains(fact.asText())) {
                keptFacts.add(new KeyFact(mask(fact.asText()), code(item.path("kind"), FACT_KINDS)));
            }
        }
        var keptKeywords = new LinkedHashSet<String>();
        for (var item : keywords) {
            if (keptKeywords.size() < MAX_KEYWORDS && item.isTextual() && !item.asText().isBlank()) {
                keptKeywords.add(mask(item.asText().trim()));
            }
        }
        var line = summary.asText().strip();
        line = mask(line.codePointCount(0, line.length()) <= MAX_SUMMARY_CHARS ? line
                : line.substring(0, line.offsetByCodePoints(0, MAX_SUMMARY_CHARS)));
        return new Content(line, keptEntities, keptFacts, decision.asBoolean(), List.copyOf(keptKeywords),
                memoryType);
    }

    private static JsonNode array(JsonNode root, String field) {
        var node = root.get(field);
        if (node == null || !node.isArray()) {
            throw new InvalidRecordException(field + " is not an array");
        }
        return node;
    }

    private static String code(JsonNode node, Set<String> allowed) {
        var value = node.isTextual() ? node.asText().trim().toUpperCase(Locale.ROOT) : "";
        return allowed.contains(value) ? value : "OTHER";
    }

    private static String mask(String value) {
        return ToolRecordSanitizer.sanitize(value);
    }
}
