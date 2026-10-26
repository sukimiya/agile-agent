package io.github.sukimiya.agileagent.memory.recall;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Deterministic query analysis for recall, without a model (D22).
 * <ul>
 * <li>{@link #codes}: code-like tokens (plates, order numbers, dates, versions): ASCII letters and digits joined by
 * {@code - _ / .}, at least one digit, at least 3 characters and, without letters, at least 4. Upper-cased, in
 * order of first appearance, distinct.</li>
 * <li>{@link #terms}: what a full-text hit is scored on: the character bigrams of every CJK run (the same units as
 * MySQL's ngram parser with {@code ngram_token_size=2}) minus a small list of function bigrams, plus lower-cased
 * ASCII words of at least 2 characters.</li>
 * </ul>
 */
public final class EntityTerms {

    private static final Pattern CODE = Pattern.compile("[A-Za-z0-9]+(?:[-_/.][A-Za-z0-9]+)*");
    private static final Pattern WORD = Pattern.compile("[A-Za-z0-9]+(?:[-_/.][A-Za-z0-9]+)*");
    /** Bigrams too common in questions to signal a topic. */
    static final Set<String> STOP = Set.of("怎么", "么样", "什么", "一下", "我的", "你的", "他的", "是不", "不是",
            "这个", "那个", "可以", "如何", "多少", "知道", "不知", "回答", "只回", "我们", "你们", "他们", "的是",
            "了吗", "是什", "是多", "为什", "哪个", "哪些", "还是", "就是", "一个", "上次", "之前", "刚才", "请问",
            "帮我", "告诉", "诉我", "一些", "现在", "今天", "好的", "谢谢", "如果", "或者", "然后", "所以", "因为");
    private static final int MAX_TERMS = 32;

    private EntityTerms() {}

    public static List<String> codes(String text) {
        var out = new LinkedHashSet<String>();
        if (text == null) {
            return List.of();
        }
        var m = CODE.matcher(text);
        while (m.find()) {
            var token = m.group();
            boolean digit = token.chars().anyMatch(Character::isDigit);
            boolean letter = token.chars().anyMatch(c -> (c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z'));
            if (digit && token.length() >= 3 && (letter || token.length() >= 4)) {
                out.add(token.toUpperCase(Locale.ROOT));
            }
        }
        return List.copyOf(out);
    }

    public static List<String> terms(String text) {
        var out = new LinkedHashSet<String>();
        if (text == null) {
            return List.of();
        }
        var run = new StringBuilder();
        for (int i = 0; i < text.length(); ) {
            int cp = text.codePointAt(i);
            i += Character.charCount(cp);
            if (Character.UnicodeScript.of(cp) == Character.UnicodeScript.HAN) {
                run.appendCodePoint(cp);
            } else {
                bigrams(run, out);
                run.setLength(0);
            }
        }
        bigrams(run, out);
        var m = WORD.matcher(text);
        while (m.find()) {
            if (m.group().length() >= 2) {
                out.add(m.group().toLowerCase(Locale.ROOT));
            }
        }
        var list = new ArrayList<>(out);
        return List.copyOf(list.size() > MAX_TERMS ? list.subList(0, MAX_TERMS) : list);
    }

    private static void bigrams(CharSequence run, Set<String> out) {
        var cps = run.codePoints().toArray();
        for (int i = 0; i + 1 < cps.length; i++) {
            var bigram = new String(cps, i, 2);
            if (!STOP.contains(bigram)) {
                out.add(bigram);
            }
        }
    }

    /** How many distinct {@code terms} occur in {@code text} (case-insensitive for ASCII). */
    public static int matched(List<String> terms, String text) {
        if (text == null || terms.isEmpty()) {
            return 0;
        }
        var lower = text.toLowerCase(Locale.ROOT);
        int n = 0;
        for (var term : terms) {
            if (lower.contains(term)) {
                n++;
            }
        }
        return n;
    }

    /** The codes of {@code codes} that occur in {@code text} (case-insensitive). */
    public static int matchedCodes(List<String> codes, String text) {
        if (text == null || codes.isEmpty()) {
            return 0;
        }
        var upper = text.toUpperCase(Locale.ROOT);
        int n = 0;
        for (var code : codes) {
            if (upper.contains(code)) {
                n++;
            }
        }
        return n;
    }
}
