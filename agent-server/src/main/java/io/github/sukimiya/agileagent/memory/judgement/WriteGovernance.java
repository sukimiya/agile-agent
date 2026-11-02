package io.github.sukimiya.agileagent.memory.judgement;

import java.util.regex.Pattern;

import io.github.sukimiya.agileagent.memory.ToolRecordSanitizer;
import io.github.sukimiya.agileagent.memory.items.MemoryItem;

/**
 * F3 write governance (M2b): reject PII/credential fields on memory items (not merely redact), and cap
 * confidence of {@link MemoryItem.Assertion#INFERRED} items at 0.8.
 */
public final class WriteGovernance {

    public static final double INFERRED_CONFIDENCE_CAP = 0.8;

    private static final Pattern CN_MOBILE = Pattern.compile("(?<!\\d)1[3-9]\\d{9}(?!\\d)");
    private static final Pattern CN_ID = Pattern.compile("(?<!\\d)\\d{17}[0-9Xx](?!\\d)");
    private static final Pattern BANK = Pattern.compile("(?<!\\d)\\d{16,19}(?!\\d)");

    private WriteGovernance() {}

    /** True when {@code text} contains phone / national id / bank card or residual credential markers. */
    public static boolean containsBlockedPii(String text) {
        if (text == null || text.isBlank()) {
            return false;
        }
        if (text.contains(ToolRecordSanitizer.REDACTED)) {
            // After sanitizer: still blocked for item writes (reject the field).
            return true;
        }
        String scrubbed = ToolRecordSanitizer.sanitize(text);
        if (!scrubbed.equals(text) || scrubbed.contains(ToolRecordSanitizer.REDACTED)) {
            return true;
        }
        return CN_MOBILE.matcher(text).find() || CN_ID.matcher(text).find() || BANK.matcher(text).find();
    }

    public static Double capConfidence(MemoryItem.Assertion assertion, Double confidence) {
        if (confidence == null) {
            return null;
        }
        if (assertion == MemoryItem.Assertion.INFERRED && confidence > INFERRED_CONFIDENCE_CAP) {
            return INFERRED_CONFIDENCE_CAP;
        }
        return confidence;
    }
}
