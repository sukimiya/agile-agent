package io.github.sukimiya.agileagent.memory;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;

/**
 * Removes credentials from tool arguments and results before they are stored. JSON input is walked to any
 * depth: values under sensitive keys (tokens, passwords, secrets, API keys, authorization, cookies,
 * credentials, private keys) become {@value #REDACTED}, and every string value is also scrubbed as text. Plain
 * text is scrubbed for structured credentials: bearer/basic authorization values, JWTs, well-known API key
 * formats, PEM private keys and {@code key=value} / {@code "key": "value"} pairs with a sensitive key. Ordinary
 * counters such as {@code max_tokens} are kept. This is defence in depth for records and logs; it does not make
 * arbitrary content safe to show.
 */
public final class ToolRecordSanitizer {

    public static final String REDACTED = "[REDACTED]";

    private static final ObjectMapper JSON = new ObjectMapper();

    /** Sensitive key name, already lower-cased with {@code _ - .} removed. */
    private static final Pattern SENSITIVE_KEY = Pattern.compile(
            ".*(password|passwd|secret|apikey|authorization|cookie|credential|privatekey|accesskey|signature).*"
                    + "|.*token|pwd");

    private static final String KEY_WORD = "[A-Za-z0-9_.-]*(?:token|secret|password|passwd|api[_-]?key|apikey"
            + "|credential|private[_-]?key|access[_-]?key)";

    private static final List<Replacement> TEXT_RULES = List.of(
            new Replacement(Pattern.compile(
                    "-----BEGIN [A-Z ]*PRIVATE KEY-----[\\s\\S]*?-----END [A-Z ]*PRIVATE KEY-----"), REDACTED),
            new Replacement(Pattern.compile("(?i)\\b(bearer|basic)\\s+[A-Za-z0-9._~+/=-]+"), "$1 " + REDACTED),
            new Replacement(Pattern.compile("\\beyJ[A-Za-z0-9_-]{4,}\\.[A-Za-z0-9_-]{4,}\\.[A-Za-z0-9_-]{4,}"), REDACTED),
            new Replacement(Pattern.compile("\\bsk-[A-Za-z0-9_-]{16,}"), REDACTED),
            new Replacement(Pattern.compile("\\b(?:ghp|gho|ghu|ghs|ghr)_[A-Za-z0-9]{20,}|\\bgithub_pat_[A-Za-z0-9_]{20,}"),
                    REDACTED),
            new Replacement(Pattern.compile("\\b(?:AKIA|ASIA)[0-9A-Z]{16}\\b"), REDACTED),
            new Replacement(Pattern.compile("\\bxox[abprs]-[A-Za-z0-9-]{10,}"), REDACTED),
            new Replacement(Pattern.compile("(?i)(\"?\\b" + KEY_WORD + "\\b\"?)(\\s*[:=]\\s*)(\"?)[^\\s\"'&,;}]+"),
                    "$1$2$3" + REDACTED),
            new Replacement(Pattern.compile("(?i)\\b(cookie|set-cookie)(\\s*:\\s*)[^\\r\\n]+"), "$1$2" + REDACTED));

    private record Replacement(Pattern pattern, String replacement) {}

    private ToolRecordSanitizer() {}

    /** Redacted copy of {@code content}; {@code null} stays {@code null}. */
    public static String sanitize(String content) {
        if (content == null || content.isEmpty()) {
            return content;
        }
        var trimmed = content.strip();
        if (trimmed.startsWith("{") || trimmed.startsWith("[")) {
            try {
                var node = JSON.readTree(trimmed);
                if (node != null && (node.isObject() || node.isArray())) {
                    return JSON.writeValueAsString(clean(node));
                }
            } catch (Exception notJson) {
                // fall through to text scrubbing
            }
        }
        return scrubText(content);
    }

    private static JsonNode clean(JsonNode node) {
        if (node instanceof ObjectNode object) {
            var fields = object.fields();
            var result = JSON.createObjectNode();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> field = fields.next();
                result.set(field.getKey(), isSensitiveKey(field.getKey()) && !field.getValue().isNull()
                        ? TextNode.valueOf(REDACTED) : clean(field.getValue()));
            }
            return result;
        }
        if (node instanceof ArrayNode array) {
            var result = JSON.createArrayNode();
            array.forEach(element -> result.add(clean(element)));
            return result;
        }
        if (node.isTextual()) {
            return TextNode.valueOf(scrubText(node.asText()));
        }
        return node;
    }

    static boolean isSensitiveKey(String key) {
        var normalized = key.toLowerCase(Locale.ROOT).replaceAll("[_.\\-]", "");
        return SENSITIVE_KEY.matcher(normalized).matches();
    }

    private static String scrubText(String text) {
        var result = text;
        for (var rule : TEXT_RULES) {
            result = rule.pattern().matcher(result).replaceAll(rule.replacement());
        }
        return result;
    }
}
