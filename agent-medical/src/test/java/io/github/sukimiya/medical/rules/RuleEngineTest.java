package io.github.sukimiya.medical.rules;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import io.github.sukimiya.agileagent.spi.capability.AgentExecutionContext;
import io.github.sukimiya.agileagent.spi.identity.IdentityContext;
import io.github.sukimiya.medical.hospital.MedicalHumanTaskRow;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

class RuleEngineTest {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final Clock CLOCK =
            Clock.fixed(Instant.parse("2026-10-16T12:00:00Z"), ZoneOffset.UTC);
    private static final MedicalRuleCodec CODEC = new MedicalRuleCodec();

    private static final IdentityContext USER = new IdentityContext("user-1", "用户一",
            "tenant-1", "租户一", null, null, Set.of("USER"), Set.of(), Set.of(), false, false);

    private final MedicalRuleRepository rules = mock(MedicalRuleRepository.class);
    private final EscalationService escalation = mock(EscalationService.class);

    private RuleEngine engine() {
        return new RuleEngine(rules, CODEC, escalation, CLOCK);
    }

    private static AgentExecutionContext context() {
        return new AgentExecutionContext(USER, "conv-1", () -> "token");
    }

    private static MedicalRuleRow emergencyRule(String id, String ruleCode,
            MedicalRuleRow.ScopeType scope, String tenantId, String content) {
        return new MedicalRuleRow(id, ruleCode, "EMERGENCY", scope, tenantId,
                tenantId == null ? null : "租户", null, null,
                scope == MedicalRuleRow.ScopeType.HOSPITAL ? "h1" : null,
                content, "manual", LocalDateTime.now(CLOCK).minusDays(1),
                LocalDateTime.now(CLOCK).plusDays(1), MedicalRuleRow.Status.APPROVED,
                "admin", null);
    }

    /** Rule content whose scripts encode priority/slaSeconds so donor assertions are exact. */
    private static String emergency(int priority, int slaSeconds, String levelsJson,
            String categoriesJson, String diseasesJson) {
        return "{\"schemaVersion\":1,\"kind\":\"EMERGENCY\",\"condition\":{\"triageCategories\":"
                + categoriesJson + ",\"diseaseNames\":" + diseasesJson + "},\"action\":{"
                + "\"priority\":" + priority + ",\"slaSeconds\":" + slaSeconds
                + ",\"escalationLevels\":" + levelsJson
                + ",\"awaitingTakeoverScript\":\"等待接管话术-" + priority + "-" + slaSeconds + "\","
                + "\"takenOverScript\":\"接管话术\"}}";
    }

    private static JsonNode args(String category, String pairsJson, String notes) {
        ObjectNode args = JSON.createObjectNode();
        if (category != null) {
            args.put("triageCategory", category);
        }
        try {
            args.set("symptomToDisease", JSON.readTree(pairsJson));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
        if (notes != null) {
            args.put("notes", notes);
        }
        return args;
    }

    private void stubCreateEcallEcho() {
        when(escalation.createEcall(any(), any(), any()))
                .thenAnswer(invocation -> Mono.just(MedicalHumanTaskRow.ecall("t1",
                        invocation.getArgument(0),
                        ((EcallPayload) invocation.getArgument(2)).toJson(),
                        invocation.getArgument(1), 100, LocalDateTime.now(CLOCK))));
    }

    @Test
    void categoryHitCreatesEcallAndReturnsTheAwaitingScript() {
        when(rules.findApproved()).thenReturn(Flux.just(emergencyRule("r1",
                "global-emergency-baseline", MedicalRuleRow.ScopeType.GLOBAL, null,
                emergency(100, 0, "[300,900]", "[\"EMERGENCY\",\"CRITICAL\"]",
                        "[\"急性心肌梗死\"]"))));
        stubCreateEcallEcho();

        var result = engine().triage(args("EMERGENCY", "[]", null), context())
                .block(Duration.ofSeconds(5));

        assertThat(result.success()).isTrue();
        assertThat(result.displayResult()).isEqualTo("等待接管话术-100-0");
        var captor = ArgumentCaptor.forClass(EcallPayload.class);
        verify(escalation).createEcall(eq("conv-1"), eq("user-1"), captor.capture());
        assertThat(captor.getValue().ruleCode()).isEqualTo("global-emergency-baseline");
        assertThat(captor.getValue().triageCategory()).isEqualTo("EMERGENCY");
        assertThat(captor.getValue().slaSeconds()).isZero();
        assertThat(captor.getValue().escalationLevels()).containsExactly(300, 900);
    }

    @Test
    void diseaseHitIsExactAfterTrimAndLowercaseNormalization() {
        when(rules.findApproved()).thenReturn(Flux.just(emergencyRule("r1", "code-en",
                MedicalRuleRow.ScopeType.GLOBAL, null,
                emergency(90, 30, "[600]", "[\"EMERGENCY\"]",
                        "[\"acute myocardial infarction\"]"))));
        stubCreateEcallEcho();

        // " Acute MYOCARDIAL Infarction " normalizes onto the curated entry; even a
        // ROUTINE category cannot talk the engine out of a disease hit (category OR disease)
        var result = engine().triage(args("ROUTINE",
                "[{\"symptom\":\"chest pain\",\"disease\":\" Acute MYOCARDIAL Infarction \"}]",
                null), context()).block(Duration.ofSeconds(5));

        assertThat(result.success()).isTrue();
        assertThat(result.displayResult()).isEqualTo("等待接管话术-90-30");
    }

    @Test
    void fuzzyNearMissesNeverHit() {
        when(rules.findApproved()).thenReturn(Flux.just(emergencyRule("r1", "code-1",
                MedicalRuleRow.ScopeType.GLOBAL, null,
                emergency(100, 0, "[300]", "[\"EMERGENCY\"]", "[\"stroke\",\"心肌梗死\"]"))));

        // exact match only — no substring, no fuzzy, no LLM discretion (spec §7.3)
        var result = engine().triage(args("ROUTINE",
                "[{\"symptom\":\"头晕\",\"disease\":\"stroke-like episode\"},"
                        + "{\"symptom\":\"胸痛\",\"disease\":\"心肌梗死前期\"}]", null), context())
                .block(Duration.ofSeconds(5));

        assertThat(result.success()).isTrue();
        assertThat(result.displayResult()).isEqualTo(RuleEngine.TRIAGE_MISS);
        verifyNoInteractions(escalation);
    }

    @Test
    void missReturnsThePinnedSafeResult() {
        when(rules.findApproved()).thenReturn(Flux.just(emergencyRule("r1", "code-1",
                MedicalRuleRow.ScopeType.GLOBAL, null,
                emergency(100, 0, "[300]", "[\"EMERGENCY\"]", "[\"stroke\"]"))));

        var result = engine().triage(args("ROUTINE",
                "[{\"symptom\":\"头痛\",\"disease\":\"感冒\"}]", "自由文本备注"), context())
                .block(Duration.ofSeconds(5));

        assertThat(result.success()).isTrue();
        assertThat(result.displayResult()).isEqualTo(RuleEngine.TRIAGE_MISS);
        verifyNoInteractions(escalation);
    }

    @Test
    void invalidArgumentsAreSafeFailures() {
        // spec §7.1: invalid parameters = safe failure — no hit, no pass-through, no E-call
        ObjectNode unknownField = JSON.createObjectNode().put("triageCategory", "EMERGENCY");
        unknownField.set("symptomToDisease", JSON.createArrayNode());
        unknownField.put("surprise", 1);

        assertThat(engine().triage(unknownField, context()).block(Duration.ofSeconds(5))
                .displayResult()).isEqualTo(RuleEngine.TRIAGE_INVALID);
        assertThat(engine().triage(args(null, "[]", null), context())
                .block(Duration.ofSeconds(5)).success()).isFalse();
        assertThat(engine().triage(args("SUPER-URGENT", "[]", null), context())
                .block(Duration.ofSeconds(5)).displayResult())
                .isEqualTo(RuleEngine.TRIAGE_INVALID);
        verifyNoInteractions(escalation);
        verifyNoInteractions(rules); // validation short-circuits before any load
    }

    @Test
    void multipleHitsTakeTheStrictestRuleForOneEcall() {
        when(rules.findApproved()).thenReturn(Flux.just(
                emergencyRule("r-a", "code-A", MedicalRuleRow.ScopeType.GLOBAL, null,
                        emergency(50, 600, "[1200]", "[\"EMERGENCY\"]", "[]")),
                emergencyRule("r-b", "code-B", MedicalRuleRow.ScopeType.GLOBAL, null,
                        emergency(100, 0, "[300]", "[\"EMERGENCY\",\"CRITICAL\"]", "[]"))));
        stubCreateEcallEcho();

        var result = engine().triage(args("EMERGENCY", "[]", null), context())
                .block(Duration.ofSeconds(5));

        assertThat(result.displayResult()).isEqualTo("等待接管话术-100-0");
        var captor = ArgumentCaptor.forClass(EcallPayload.class);
        verify(escalation, times(1)).createEcall(eq("conv-1"), eq("user-1"), captor.capture());
        assertThat(captor.getValue().ruleCode()).isEqualTo("code-B"); // min sla → max priority
    }

    @Test
    void globalBaselineCannotBeRelaxedByLowerScopes() {
        // Review Focus #4: same rule_code across scopes merges strictest — a HOSPITAL rule
        // can only tighten (union of lists, min SLA, max priority); its longer window and
        // its reduced disease list never dilute the GLOBAL baseline.
        when(rules.findApproved()).thenReturn(Flux.just(
                emergencyRule("r-global", "baseline", MedicalRuleRow.ScopeType.GLOBAL, null,
                        emergency(100, 0, "[300]", "[\"EMERGENCY\"]", "[\"心肌梗死\",\"stroke\"]")),
                emergencyRule("r-hosp", "baseline", MedicalRuleRow.ScopeType.HOSPITAL, "tenant-1",
                        emergency(10, 3600, "[9999]", "[\"ROUTINE\"]", "[\"感冒\"]"))));
        stubCreateEcallEcho();

        var result = engine().triage(args("ROUTINE",
                "[{\"symptom\":\"胸痛\",\"disease\":\"心肌梗死\"}]", null), context())
                .block(Duration.ofSeconds(5));

        assertThat(result.success()).isTrue();
        assertThat(result.displayResult()).isEqualTo("等待接管话术-100-0"); // donor = GLOBAL row
        var captor = ArgumentCaptor.forClass(EcallPayload.class);
        verify(escalation).createEcall(eq("conv-1"), eq("user-1"), captor.capture());
        assertThat(captor.getValue().slaSeconds()).isZero();     // never relaxed to 3600
        assertThat(captor.getValue().priority()).isEqualTo(100); // max, never min
        assertThat(captor.getValue().escalationLevels()).containsExactly(300);
    }

    @Test
    void globalDonorFloorKeepsPlatformScriptsAtEqualSla() {
        // Final review I-2: a HOSPITAL rule sharing the GLOBAL rule_code with an EQUAL
        // slaSeconds (and its own scripts/ladder) must never substitute the platform's
        // pre-approved fixed scripts — the GLOBAL row is the unconditional donor. The
        // hospital's tightened additions (extra disease name) and the strictest
        // priority/SLA merge still take effect.
        when(rules.findApproved()).thenReturn(Flux.just(
                emergencyRule("r-global", "baseline", MedicalRuleRow.ScopeType.GLOBAL, null,
                        "{\"schemaVersion\":1,\"kind\":\"EMERGENCY\",\"condition\":{"
                                + "\"triageCategories\":[\"EMERGENCY\"],"
                                + "\"diseaseNames\":[\"stroke\"]},\"action\":{\"priority\":100,"
                                + "\"slaSeconds\":0,\"escalationLevels\":[300,900],"
                                + "\"awaitingTakeoverScript\":\"平台等待话术\","
                                + "\"takenOverScript\":\"平台接管话术\"}}"),
                emergencyRule("r-hosp", "baseline", MedicalRuleRow.ScopeType.HOSPITAL, "tenant-1",
                        "{\"schemaVersion\":1,\"kind\":\"EMERGENCY\",\"condition\":{"
                                + "\"triageCategories\":[],"
                                + "\"diseaseNames\":[\"院级新增病种\"]},\"action\":{\"priority\":50,"
                                + "\"slaSeconds\":0,\"escalationLevels\":[120],"
                                + "\"awaitingTakeoverScript\":\"院级等待话术\","
                                + "\"takenOverScript\":\"院级接管话术\"}}")));
        stubCreateEcallEcho();

        // GLOBAL category hit: donor = GLOBAL row despite the equal-SLA hospital rule
        var result = engine().triage(args("EMERGENCY", "[]", null), context())
                .block(Duration.ofSeconds(5));
        assertThat(result.success()).isTrue();
        assertThat(result.displayResult()).isEqualTo("平台等待话术");
        var captor = ArgumentCaptor.forClass(EcallPayload.class);
        verify(escalation).createEcall(eq("conv-1"), eq("user-1"), captor.capture());
        assertThat(captor.getValue().awaitingTakeoverScript()).isEqualTo("平台等待话术");
        assertThat(captor.getValue().takenOverScript()).isEqualTo("平台接管话术");
        assertThat(captor.getValue().escalationLevels()).containsExactly(300, 900);
        assertThat(captor.getValue().priority()).isEqualTo(100); // max merge
        assertThat(captor.getValue().slaSeconds()).isZero();     // min merge

        // The hospital's extra disease name still hits (union merge) — and still carries
        // the GLOBAL scripts.
        var hospitalAddition = engine().triage(args("ROUTINE",
                "[{\"symptom\":\"院级症状\",\"disease\":\"院级新增病种\"}]", null), context())
                .block(Duration.ofSeconds(5));
        assertThat(hospitalAddition.success()).isTrue();
        assertThat(hospitalAddition.displayResult()).isEqualTo("平台等待话术");
        verify(escalation, times(2)).createEcall(eq("conv-1"), eq("user-1"), captor.capture());
        assertThat(captor.getValue().ruleCode()).isEqualTo("baseline");
        assertThat(captor.getValue().awaitingTakeoverScript()).isEqualTo("平台等待话术");
    }

    @Test
    void onlyGlobalAndOwnTenantRulesAreVisible() {
        // Ruling R3: engine visibility = GLOBAL always + TENANT/HOSPITAL by tenantId match
        when(rules.findApproved()).thenReturn(Flux.just(emergencyRule("r-other", "other",
                MedicalRuleRow.ScopeType.TENANT, "tenant-2",
                emergency(100, 0, "[300]", "[\"EMERGENCY\"]", "[\"其他租户病种\"]"))));

        var result = engine().triage(args("EMERGENCY",
                "[{\"symptom\":\"x\",\"disease\":\"其他租户病种\"}]", null), context())
                .block(Duration.ofSeconds(5));

        assertThat(result.displayResult()).isEqualTo(RuleEngine.TRIAGE_MISS);
        verifyNoInteractions(escalation);
    }

    @Test
    void expiredRulesNeverLoad() {
        var expired = new MedicalRuleRow("r-exp", "code-exp", "EMERGENCY",
                MedicalRuleRow.ScopeType.GLOBAL, null, null, null, null, null,
                emergency(100, 0, "[300]", "[\"EMERGENCY\"]", "[]"), "manual",
                LocalDateTime.now(CLOCK).minusDays(10), LocalDateTime.now(CLOCK).minusDays(1),
                MedicalRuleRow.Status.APPROVED, "admin", null);
        when(rules.findApproved()).thenReturn(Flux.just(expired));

        var result = engine().triage(args("EMERGENCY", "[]", null), context())
                .block(Duration.ofSeconds(5));

        assertThat(result.displayResult()).isEqualTo(RuleEngine.TRIAGE_MISS);
        verifyNoInteractions(escalation);
    }

    @Test
    void junkAndSafetyPatternRowsAreSkippedNotFatal() {
        var junk = emergencyRule("r-junk", "code-junk", MedicalRuleRow.ScopeType.GLOBAL, null,
                "{\"junk\":1}");
        var safety = new MedicalRuleRow("r-safety", "code-safety", "SAFETY_PATTERN",
                MedicalRuleRow.ScopeType.GLOBAL, null, null, null, null, null,
                "{\"schemaVersion\":1,\"kind\":\"SAFETY_PATTERN\","
                        + "\"condition\":{\"patterns\":[\"x\"],\"fieldLabel\":\"规则数据\"},"
                        + "\"action\":{}}",
                "manual", LocalDateTime.now(CLOCK).minusDays(1),
                LocalDateTime.now(CLOCK).plusDays(1), MedicalRuleRow.Status.APPROVED,
                "admin", null);
        var good = emergencyRule("r-good", "code-good", MedicalRuleRow.ScopeType.GLOBAL, null,
                emergency(100, 0, "[300]", "[\"CRITICAL\"]", "[]"));
        when(rules.findApproved()).thenReturn(Flux.just(junk, safety, good));
        stubCreateEcallEcho();

        var result = engine().triage(args("CRITICAL", "[]", null), context())
                .block(Duration.ofSeconds(5));

        assertThat(result.success()).isTrue();
        assertThat(result.displayResult()).isEqualTo("等待接管话术-100-0");
    }

    @Test
    void engineErrorsFallBackSafelyWithoutFabricatingATriage() {
        // spec §7.6 + medical red line: any engine failure → the safe-failure script,
        // never an invented conclusion, never a silent pass.
        when(rules.findApproved()).thenReturn(Flux.error(new IllegalStateException("db down")));

        var result = engine().triage(args("EMERGENCY", "[]", null), context())
                .block(Duration.ofSeconds(5));

        assertThat(result.success()).isFalse();
        assertThat(result.displayResult()).isEqualTo(RuleEngine.SAFE_FALLBACK);
        verify(escalation, never()).createEcall(any(), any(), any());
    }

    @Test
    void ecallCreationFailureFallsBackSafely() {
        when(rules.findApproved()).thenReturn(Flux.just(emergencyRule("r1", "code-1",
                MedicalRuleRow.ScopeType.GLOBAL, null,
                emergency(100, 0, "[300]", "[\"EMERGENCY\"]", "[]"))));
        when(escalation.createEcall(any(), any(), any()))
                .thenReturn(Mono.error(new IllegalStateException("db down")));

        var result = engine().triage(args("EMERGENCY", "[]", null), context())
                .block(Duration.ofSeconds(5));

        assertThat(result.success()).isFalse();
        assertThat(result.displayResult()).isEqualTo(RuleEngine.SAFE_FALLBACK);
    }

    @Test
    void ecallCreationEmptyFallsBackSafely() {
        when(rules.findApproved()).thenReturn(Flux.just(emergencyRule("r1", "code-1",
                MedicalRuleRow.ScopeType.GLOBAL, null,
                emergency(100, 0, "[300]", "[\"EMERGENCY\"]", "[]"))));
        when(escalation.createEcall(any(), any(), any())).thenReturn(Mono.empty());

        var result = engine().triage(args("EMERGENCY", "[]", null), context())
                .block(Duration.ofSeconds(5));

        assertThat(result.success()).isFalse();
        assertThat(result.displayResult()).isEqualTo(RuleEngine.SAFE_FALLBACK);
    }
}
