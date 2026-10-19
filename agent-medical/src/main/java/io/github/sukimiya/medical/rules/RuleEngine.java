package io.github.sukimiya.medical.rules;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeSet;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.JsonNode;

import io.github.sukimiya.agileagent.spi.capability.AgentExecutionContext;
import io.github.sukimiya.agileagent.spi.capability.ToolResult;
import reactor.core.publisher.Mono;

/**
 * The deterministic rule engine (Plan 4 spec §7): the code hard gate behind submitTriage.
 * Strict parameter validation → load identity-visible approved in-window EMERGENCY rules
 * (the GLOBAL baseline is always among them) → exact match against the curated lists
 * (trim + lowercase normalization only — no fuzzy matching, no LLM discretion) → the
 * strictest hit wins → idempotent E-call creation and the rule's awaiting-takeover script.
 * Same-rule_code rows across scopes merge strictest, so lower scopes can only tighten the
 * GLOBAL baseline (spec §6). Misses and errors have pinned safe results; the engine NEVER
 * invents a triage conclusion (medical red line). Non-final so provider tests can mock it.
 */
public class RuleEngine {

    /** Safe-failure result for any engine error (spec §7.6). */
    public static final String SAFE_FALLBACK = "服务暂时不可用，请联系人工客服";
    /** Pinned result for a validated miss (spec §7.5). */
    public static final String TRIAGE_MISS =
            "分诊完成：未发现紧急指征，请按普通流程继续行政协调。";
    /** Pinned result for invalid submitTriage parameters (spec §7.1: safe failure). */
    public static final String TRIAGE_INVALID =
            "分诊参数不合法，未能完成分诊。如情况危急，请立即拨打 120 或前往就近急诊。";

    private static final Logger log = LoggerFactory.getLogger(RuleEngine.class);

    private final MedicalRuleRepository rules;
    private final MedicalRuleCodec codec;
    private final EscalationService escalation;
    private final Clock clock;

    public RuleEngine(MedicalRuleRepository rules, MedicalRuleCodec codec,
            EscalationService escalation, Clock clock) {
        this.rules = rules;
        this.codec = codec;
        this.escalation = escalation;
        this.clock = clock;
    }

    /** Runs one submitTriage call through the gate. Never errors; never fabricates. */
    public Mono<ToolResult> triage(JsonNode arguments, AgentExecutionContext context) {
        return TriageSubmission.parse(arguments)
                .map(submission -> evaluate(submission, context))
                .orElseGet(() -> Mono.just(new ToolResult(false, TRIAGE_INVALID)));
    }

    private Mono<ToolResult> evaluate(TriageSubmission submission, AgentExecutionContext context) {
        LocalDateTime now = LocalDateTime.now(clock);
        return rules.findApproved()
                .filter(row -> "EMERGENCY".equals(row.ruleKind()))
                .filter(row -> withinWindow(row, now))
                .filter(row -> visibleTo(row, context))
                .concatMap(row -> Mono.justOrEmpty(codec.parse(row.contentJson())
                        .filter(EmergencyRuleContent.class::isInstance)
                        .map(EmergencyRuleContent.class::cast)
                        .map(content -> new LoadedRule(row, content))))
                .collectList()
                .flatMap(loaded -> act(mergeByCode(loaded), submission, context))
                .onErrorResume(e -> {
                    log.error("Rule engine failure; returning safe fallback", e);
                    return Mono.just(new ToolResult(false, SAFE_FALLBACK));
                });
    }

    /** Ruling R3: GLOBAL always visible; TENANT/HOSPITAL rows only inside their own tenant. */
    private static boolean visibleTo(MedicalRuleRow row, AgentExecutionContext context) {
        if (row.scopeType() == MedicalRuleRow.ScopeType.GLOBAL) {
            return true;
        }
        String tenantId = context.identity().tenantId();
        return tenantId != null && tenantId.equals(row.tenantId());
    }

    private static boolean withinWindow(MedicalRuleRow row, LocalDateTime now) {
        return row.validFrom() != null && row.validUntil() != null
                && !now.isBefore(row.validFrom()) && now.isBefore(row.validUntil());
    }

    /**
     * Strictest merge per rule_code (spec §6, Review Focus #4): condition lists are unions
     * (lower scopes may only ADD categories/diseases, never remove GLOBAL entries),
     * priority is the max, the tightest SLA wins. Escalation ladder and fixed scripts come
     * from the donor row: if a GLOBAL row shares the rule_code it is UNCONDITIONALLY the
     * donor (final review I-2 — a tenant-approved narrower rule may tighten conditions and
     * SLA but never substitute the platform's pre-approved scripts or ladder); otherwise
     * the donor is min slaSeconds, then the narrowest scope (HOSPITAL > TENANT > GLOBAL),
     * then the smallest row id — fully deterministic.
     */
    private static List<MergedRule> mergeByCode(List<LoadedRule> loaded) {
        Map<String, List<LoadedRule>> byCode = new LinkedHashMap<>();
        for (LoadedRule rule : loaded) {
            byCode.computeIfAbsent(rule.row().ruleCode(), key -> new ArrayList<>()).add(rule);
        }
        List<MergedRule> merged = new ArrayList<>();
        for (var entry : byCode.entrySet()) {
            TreeSet<String> categories = new TreeSet<>();
            TreeSet<String> diseases = new TreeSet<>();
            int priority = 0;
            int slaSeconds = Integer.MAX_VALUE;
            for (LoadedRule rule : entry.getValue()) {
                rule.content().condition().triageCategories()
                        .forEach(category -> categories.add(normalize(category)));
                rule.content().condition().diseaseNames()
                        .forEach(disease -> diseases.add(normalize(disease)));
                priority = Math.max(priority, rule.content().action().priority());
                slaSeconds = Math.min(slaSeconds, rule.content().action().slaSeconds());
            }
            LoadedRule donor = entry.getValue().stream()
                    .filter(r -> r.row().scopeType() == MedicalRuleRow.ScopeType.GLOBAL)
                    .min(Comparator.comparing(r -> r.row().id())) // GLOBAL floor (final review I-2)
                    .orElseGet(() -> entry.getValue().stream()
                            .min(Comparator
                                    .comparingInt(
                                            (LoadedRule r) -> r.content().action().slaSeconds())
                                    .thenComparingInt(r -> -scopeRank(r.row()))
                                    .thenComparing(r -> r.row().id()))
                            .orElseThrow());
            merged.add(new MergedRule(entry.getKey(), List.copyOf(categories),
                    List.copyOf(diseases), priority, slaSeconds, donor));
        }
        return merged;
    }

    private Mono<ToolResult> act(List<MergedRule> merged, TriageSubmission submission,
            AgentExecutionContext context) {
        String category = normalize(submission.triageCategory().name());
        List<String> diseases = submission.symptomToDisease().stream()
                .map(pair -> normalize(pair.disease()))
                .toList();
        List<MergedRule> hits = merged.stream()
                .filter(rule -> rule.categories().contains(category)
                        || rule.diseases().stream().anyMatch(diseases::contains))
                .toList();
        if (hits.isEmpty()) {
            // Miss audit (ruling R4): structured log only — notes are free text and must
            // never become a matching signal, but the ops trail keeps them verbatim.
            log.info("Triage miss: conversation={} category={} diseases={} notes={}",
                    context.conversationId(), submission.triageCategory(), diseases,
                    submission.notes());
            return Mono.just(new ToolResult(true, TRIAGE_MISS));
        }
        MergedRule winner = hits.stream()
                .min(Comparator.comparingInt((MergedRule rule) -> rule.slaSeconds())
                        .thenComparingInt(rule -> -rule.priority())
                        .thenComparing(MergedRule::ruleCode))
                .orElseThrow();
        EmergencyAction action = winner.donor().content().action();
        EcallPayload payload = new EcallPayload(winner.ruleCode(),
                submission.triageCategory().name(), submission.notes(), winner.priority(),
                winner.slaSeconds(), action.escalationLevels(),
                action.awaitingTakeoverScript(), action.takenOverScript());
        return escalation.createEcall(context.conversationId(), context.identity().subject(),
                        payload)
                .doOnNext(task -> log.info("Triage hit: conversation={} task={} rule={} "
                                + "category={} diseases={}", context.conversationId(), task.id(),
                                winner.ruleCode(), submission.triageCategory(), diseases))
                .map(task -> new ToolResult(true, payload.awaitingTakeoverScript()))
                .switchIfEmpty(Mono.fromSupplier(() -> {
                    log.warn("E-call creation completed empty; returning safe fallback: "
                            + "conversation={}", context.conversationId());
                    return new ToolResult(false, SAFE_FALLBACK);
                }))
                .onErrorResume(e -> {
                    log.error("E-call creation failed; returning safe fallback", e);
                    return Mono.just(new ToolResult(false, SAFE_FALLBACK));
                });
    }

    /** Normalization is trim + English lowercasing only — curated lists match exactly (spec §7.3). */
    private static String normalize(String text) {
        return text == null ? "" : text.trim().toLowerCase(Locale.ROOT);
    }

    private static int scopeRank(MedicalRuleRow row) {
        return switch (row.scopeType()) {
            case GLOBAL -> 1;
            case TENANT -> 2;
            case HOSPITAL -> 3;
        };
    }

    private record LoadedRule(MedicalRuleRow row, EmergencyRuleContent content) {
    }

    private record MergedRule(String ruleCode, List<String> categories, List<String> diseases,
            int priority, int slaSeconds, LoadedRule donor) {
    }
}
