package io.github.sukimiya.medical.rules;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * In-memory snapshot of the approved GLOBAL SAFETY_PATTERN increment (Plan 4 spec §2
 * ruling 6, §6): new advertising-law keywords or injection markers take effect via rule
 * approval + reload — no release. Fail-safe by construction: any load failure clears the
 * increment and logs, degrading to the code baseline, which never fails. The store holds
 * only compiled patterns; the code-layer baseline stays curated inside TextSafetyScreen
 * and is non-relaxable.
 */
public final class SafetyPatternStore {

    private static final Logger log = LoggerFactory.getLogger(SafetyPatternStore.class);

    /** One compiled data pattern with its provenance for violation text. */
    public record DataPattern(String ruleCode, String fieldLabel, Pattern pattern) {}

    private final AtomicReference<java.util.List<DataPattern>> dataPatterns =
            new AtomicReference<>(java.util.List.of());
    private final MedicalRuleRepository rules;
    private final MedicalRuleCodec codec;
    private final Clock clock;

    public SafetyPatternStore(MedicalRuleRepository rules, MedicalRuleCodec codec, Clock clock) {
        this.rules = rules;
        this.codec = codec;
        this.clock = clock;
    }

    /** Atomically replaces the whole snapshot. Never errors — failure clears to baseline. */
    public Mono<Void> reload() {
        return Mono.defer(() -> rules.findApproved()
                        .filter(row -> "SAFETY_PATTERN".equals(row.ruleKind()))
                        .filter(row -> row.scopeType() == MedicalRuleRow.ScopeType.GLOBAL)
                        .filter(this::withinValidityWindow)
                        .concatMap(this::compilePatterns)
                        .collectList()
                        .doOnNext(loaded -> {
                            dataPatterns.set(java.util.List.copyOf(loaded));
                            log.info("SAFETY_PATTERN store reloaded: {} pattern(s)", loaded.size());
                        }))
                .onErrorResume(error -> {
                    log.warn("SAFETY_PATTERN reload failed; degrading to code baseline only",
                            error);
                    dataPatterns.set(java.util.List.of());
                    return Mono.empty();
                })
                .then();
    }

    public java.util.List<DataPattern> current() {
        return dataPatterns.get();
    }

    private boolean withinValidityWindow(MedicalRuleRow row) {
        LocalDateTime now = LocalDateTime.now(clock);
        return row.validFrom() != null && row.validUntil() != null
                && !now.isBefore(row.validFrom()) && !now.isAfter(row.validUntil());
    }

    /** Defense in depth: a pattern that fails to compile is skipped, never fatal. */
    private Flux<DataPattern> compilePatterns(MedicalRuleRow row) {
        return Flux.fromIterable(codec.parse(row.contentJson())
                        .filter(SafetyPatternRuleContent.class::isInstance)
                        .map(SafetyPatternRuleContent.class::cast)
                        .map(content -> content.condition() == null
                                || content.condition().patterns() == null
                                ? java.util.List.<String>of()
                                : content.condition().patterns())
                        .orElseGet(java.util.List::of))
                .concatMap(pattern -> {
                    if (pattern == null || pattern.isBlank()) {
                        return Flux.empty();
                    }
                    try {
                        return Flux.just(new DataPattern(row.ruleCode(),
                                row.contentJson() == null ? "规则数据" : fieldLabelOf(row),
                                Pattern.compile(pattern)));
                    } catch (PatternSyntaxException e) {
                        log.warn("Skipping invalid SAFETY_PATTERN regex in rule {}: {}",
                                row.ruleCode(), e.getDescription());
                        return Flux.empty();
                    }
                });
    }

    private String fieldLabelOf(MedicalRuleRow row) {
        return codec.parse(row.contentJson())
                .filter(SafetyPatternRuleContent.class::isInstance)
                .map(SafetyPatternRuleContent.class::cast)
                .map(SafetyPatternRuleContent::condition)
                .map(SafetyPatternCondition::fieldLabel)
                .filter(label -> label != null && !label.isBlank())
                .orElse("规则数据");
    }
}
