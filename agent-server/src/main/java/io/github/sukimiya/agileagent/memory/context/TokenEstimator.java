package io.github.sukimiya.agileagent.memory.context;

import java.util.List;

import io.github.sukimiya.agileagent.service.LlmClient;

/**
 * Conservative token estimate without a tokenizer (D3): CJK characters (Han, kana, Hangul, CJK and full-width
 * punctuation) count one token each, every other non-whitespace character a quarter token, rounded up. This is the
 * M1 heuristic unchanged; budgets apply a safety factor on top ({@link #budgeted}) and the deviation from the
 * provider's {@code prompt_tokens} is recorded as {@code memory.context.estimate.ratio}.
 */
public final class TokenEstimator {

    /** Absorbs floating-point noise so that, e.g., 10 x 1.3 stays 13. */
    private static final double EPSILON = 1e-9;

    private TokenEstimator() {}

    public static long estimate(String text) {
        if (text == null || text.isEmpty()) {
            return 0;
        }
        long cjk = 0;
        long other = 0;
        for (int i = 0; i < text.length(); ) {
            int cp = text.codePointAt(i);
            i += Character.charCount(cp);
            if (Character.isWhitespace(cp)) {
                continue;
            }
            var script = Character.UnicodeScript.of(cp);
            if (script == Character.UnicodeScript.HAN || script == Character.UnicodeScript.HIRAGANA
                    || script == Character.UnicodeScript.KATAKANA || script == Character.UnicodeScript.HANGUL
                    || (cp >= 0x3000 && cp <= 0x303F) || (cp >= 0xFF00 && cp <= 0xFFEF)) {
                cjk++;
            } else {
                other++;
            }
        }
        return cjk + (other + 3) / 4;
    }

    /** Content plus tool-call names and arguments of every message. */
    public static long estimate(List<LlmClient.Message> messages) {
        long total = 0;
        for (var message : messages) {
            total += estimate(message.content());
            if (message.toolCalls() != null) {
                for (var call : message.toolCalls()) {
                    total += estimate(call.name()) + estimate(call.argumentsJson());
                }
            }
        }
        return total;
    }

    /** {@code raw} scaled by the safety {@code factor}, rounded up. */
    public static int budgeted(long raw, double factor) {
        var scaled = Math.ceil(raw * factor - EPSILON);
        return scaled >= Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) Math.max(0, scaled);
    }
}
