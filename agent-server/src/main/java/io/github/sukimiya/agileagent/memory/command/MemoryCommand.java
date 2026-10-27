package io.github.sukimiya.agileagent.memory.command;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

import io.github.sukimiya.agileagent.memory.recall.EntityTerms;

/**
 * An in-chat memory command: "忘记…" forgets matching items, "记起来…" restores forgotten ones. Recognised by
 * {@link MemoryCommandClassifier} (keyword prefilter, then an LLM classification of the user's own current input
 * only); this record also holds the deterministic parts: numbered picks ({@link #selection}) and the topic match
 * score ({@link #score}).
 */
public record MemoryCommand(Kind kind, String topic) {

    public enum Kind { FORGET, RESTORE }

    private static final Pattern TRAILING_PUNCTUATION = Pattern.compile("[\\s。！!.~～]+$");
    private static final Pattern ALL = Pattern.compile(
            "^(?:全部|都|全都|所有|全选)(?:的)?(?:忘记|忘掉|忘了|记起来|恢复)?(?:吧)?$");
    private static final Pattern NUMBERS = Pattern.compile(
            "^第?\\d+(?:条|个|项)?(?:\\s*[、,，和\\s]\\s*第?\\d+(?:条|个|项)?)*$");
    private static final Pattern NUMBER = Pattern.compile("\\d+");

    /**
     * The 1-based numbers a reply picks out of {@code size} listed candidates ("2", "1、3", "第1条", "全部"), or
     * empty when the reply is not a pick (any other text, or a number out of range).
     */
    public static Optional<List<Integer>> selection(String reply, int size) {
        if (reply == null || size < 1) {
            return Optional.empty();
        }
        var text = TRAILING_PUNCTUATION.matcher(reply.strip()).replaceAll("");
        if (ALL.matcher(text).matches()) {
            var all = new ArrayList<Integer>();
            for (int i = 1; i <= size; i++) {
                all.add(i);
            }
            return Optional.of(List.copyOf(all));
        }
        if (!NUMBERS.matcher(text).matches()) {
            return Optional.empty();
        }
        var picks = new LinkedHashSet<Integer>();
        var m = NUMBER.matcher(text);
        while (m.find()) {
            int n;
            try {
                n = Integer.parseInt(m.group());
            } catch (NumberFormatException e) {
                return Optional.empty();
            }
            if (n < 1 || n > size) {
                return Optional.empty();
            }
            picks.add(n);
        }
        return picks.isEmpty() ? Optional.empty() : Optional.of(List.copyOf(picks));
    }

    /**
     * How well {@code content} matches {@code topic}: the share of the topic's terms (CJK bigrams and ASCII words,
     * as in recall) and codes it contains, 0..1.
     */
    public static double score(String topic, String content) {
        var terms = EntityTerms.terms(topic);
        var codes = EntityTerms.codes(topic);
        int total = terms.size() + codes.size();
        if (total == 0 || content == null) {
            return 0;
        }
        return (EntityTerms.matched(terms, content) + EntityTerms.matchedCodes(codes, content)) / (double) total;
    }
}
