package io.github.sukimiya.medical.validation;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import io.github.sukimiya.medical.rules.SafetyPatternStore;

/**
 * Curated pattern screening over every hospital-supplied text field (spec §7): prompt-injection
 * markers and claims banned by China's medical advertising rules (Advertising Law art. 16 —
 * guarantee-style promises such as 根治/保证有效/无副作用). The pattern lists are deliberately
 * curated and auditable: this screen is a review gate that flags a submission for human eyes,
 * not a parser. At most one violation per pattern family per field keeps GateReport readable.
 * Since Plan 4 the screen is a composite: the curated constants above are a frozen, non-relaxable
 * baseline, and an injected {@link io.github.sukimiya.medical.rules.SafetyPatternStore}
 * contributes approved GLOBAL SAFETY_PATTERN increments (fail-safe: baseline only when the store
 * is empty or absent).
 */
public final class TextSafetyScreen {

    private static final List<Pattern> INJECTION_PATTERNS = List.of(
            Pattern.compile("ignore (all |any )?(previous|prior|above|earlier) instructions?",
                    Pattern.CASE_INSENSITIVE),
            Pattern.compile("disregard.{0,40}instructions", Pattern.CASE_INSENSITIVE),
            Pattern.compile("you are now", Pattern.CASE_INSENSITIVE),
            Pattern.compile("system prompt", Pattern.CASE_INSENSITIVE),
            Pattern.compile("reveal.{0,40}instructions", Pattern.CASE_INSENSITIVE),
            Pattern.compile("developer mode", Pattern.CASE_INSENSITIVE),
            Pattern.compile("忽略.{0,12}(指令|规则|提示)"),
            Pattern.compile("无视.{0,20}(指令|规则|限制)"),
            Pattern.compile("你现在是"),
            Pattern.compile("扮演.{0,12}(角色|医生|系统)"),
            Pattern.compile("系统提示词"),
            Pattern.compile("开发者模式"));

    private static final List<Pattern> MEDICAL_CLAIM_PATTERNS = List.of(
            Pattern.compile("根治"),
            Pattern.compile("包治"),
            Pattern.compile("(百分百|100%)治愈"),
            Pattern.compile("无副作用"),
            Pattern.compile("保证(治愈|有效|痊愈)"),
            Pattern.compile("永不复发"),
            Pattern.compile("祖传秘方"),
            Pattern.compile("治愈率.{0,8}(第一|最高)"));

    private final SafetyPatternStore dataPatterns;

    /** Baseline-only screen: the frozen curated constants (Plan 3 behavior, unchanged). */
    public TextSafetyScreen() {
        this(null);
    }

    /** Composite screen: frozen baseline + approved GLOBAL SAFETY_PATTERN increment. */
    public TextSafetyScreen(SafetyPatternStore dataPatterns) {
        this.dataPatterns = dataPatterns;
    }

    /**
     * Screens one hospital-supplied text field. Returns at most one violation per pattern
     * family; null/blank text yields an empty list.
     */
    public List<String> screen(String fieldLabel, String text) {
        if (text == null || text.isBlank()) {
            return List.of();
        }
        List<String> violations = new ArrayList<>();
        if (matchesAny(INJECTION_PATTERNS, text)) {
            violations.add(fieldLabel + ": 疑似提示注入标记");
        }
        if (matchesAny(MEDICAL_CLAIM_PATTERNS, text)) {
            violations.add(fieldLabel + ": 疑似医疗广告法禁用声明");
        }
        if (dataPatterns != null) {
            for (SafetyPatternStore.DataPattern data : dataPatterns.current()) {
                if (data.pattern().matcher(text).find()) {
                    violations.add(fieldLabel + ": 命中数据驱动安全模式（" + data.fieldLabel() + "）");
                }
            }
        }
        return List.copyOf(violations);
    }

    private static boolean matchesAny(List<Pattern> patterns, String text) {
        return patterns.stream().anyMatch(pattern -> pattern.matcher(text).find());
    }
}
