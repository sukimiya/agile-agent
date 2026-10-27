package io.github.sukimiya.agileagent.memory.command;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

import io.github.sukimiya.agileagent.memory.recall.EntityTerms;

/**
 * An in-chat memory command recognised by deterministic rules (no model call): "忘记…" forgets matching items,
 * "记起来…" restores forgotten ones. Only the whole current user input counts, and only when it is the
 * instruction itself: it must start with the trigger (optionally after a polite lead such as 请 / 帮我) or be
 * "把…忘掉" / "把…记起来"; a statement ("我忘了…"), a question (ending with 吗 / ？), text quoting a document or a
 * web page, a long paste, or a trigger followed by a comma is not a command. Tool results, web pages and
 * documents never reach this parser: the orchestrator only passes the user's own stored input.
 */
public record MemoryCommand(Kind kind, String topic) {

    public enum Kind { FORGET, RESTORE }

    /** Longer inputs are pasted material, not an instruction. */
    static final int MAX_INPUT = 200;

    private static final String LEAD = "(?:请你?|麻烦你?|帮我|你|就)*";
    private static final String SEPARATOR = "[:：\\s]*";
    private static final List<Rule> RULES = List.of(
            new Rule(Kind.FORGET, Pattern.compile("^" + LEAD + "把(.+?)(?:给我)?(?:忘记|忘掉|忘了)(?:吧|掉)?$")),
            new Rule(Kind.RESTORE, Pattern.compile("^" + LEAD + "把(.+?)(?:重新)?(?:记起来|想起来)(?:吧)?$")),
            new Rule(Kind.FORGET, Pattern.compile("^" + LEAD
                    + "(?:忘记|忘掉|忘了|不要再记着|不要记着|别再记着|别记着)" + SEPARATOR + "([^，,了吗].*)$")),
            new Rule(Kind.RESTORE, Pattern.compile("^" + LEAD + "(?:记起来|想起来|重新记住|恢复记忆)" + SEPARATOR
                    + "([^，,了吗].*)$")));
    private static final Pattern TRAILING_PUNCTUATION = Pattern.compile("[\\s。！!.~～]+$");
    private static final Pattern TOPIC_LEAD = Pattern.compile("^(?:我的|我|关于|有关|一下|掉|这条|那条)+");
    private static final Pattern TOPIC_TAIL = Pattern.compile(
            "(?:吧|了|啊|呀|这件事情|这件事|这事|那件事|的事情|的事|这条记忆|的记忆|这条|这个|[\\s。！!.?？，,])+$");
    private static final Pattern ALL = Pattern.compile(
            "^(?:全部|都|全都|所有|全选)(?:的)?(?:忘记|忘掉|忘了|记起来|恢复)?(?:吧)?$");
    private static final Pattern NUMBERS = Pattern.compile(
            "^第?\\d+(?:条|个|项)?(?:\\s*[、,，和\\s]\\s*第?\\d+(?:条|个|项)?)*$");
    private static final Pattern NUMBER = Pattern.compile("\\d+");

    private record Rule(Kind kind, Pattern pattern) {}

    /** The command in {@code userInput}, or empty when it is not one. */
    public static Optional<MemoryCommand> parse(String userInput) {
        if (userInput == null) {
            return Optional.empty();
        }
        var text = TRAILING_PUNCTUATION.matcher(userInput.strip()).replaceAll("");
        if (text.isEmpty() || text.length() > MAX_INPUT || text.endsWith("吗") || text.endsWith("?")
                || text.endsWith("？") || text.endsWith("么")) {
            return Optional.empty();
        }
        for (var rule : RULES) {
            var m = rule.pattern().matcher(text);
            if (m.matches()) {
                var topic = cleanTopic(m.group(1));
                return topic.isEmpty() ? Optional.empty() : Optional.of(new MemoryCommand(rule.kind(), topic));
            }
        }
        return Optional.empty();
    }

    private static String cleanTopic(String raw) {
        var topic = raw.strip();
        String before;
        do {
            before = topic;
            topic = TOPIC_TAIL.matcher(topic).replaceAll("");
            topic = TOPIC_LEAD.matcher(topic).replaceAll("").strip();
        } while (!topic.equals(before));
        return topic;
    }

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
