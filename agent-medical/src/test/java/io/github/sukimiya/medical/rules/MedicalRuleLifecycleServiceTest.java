package io.github.sukimiya.medical.rules;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import io.github.sukimiya.agileagent.spi.identity.IdentityContext;
import io.github.sukimiya.medical.lifecycle.LifecycleOutcome;
import io.github.sukimiya.medical.lifecycle.SubmissionStatus;
import io.github.sukimiya.medical.validation.TextSafetyScreen;
import reactor.core.publisher.Mono;

class MedicalRuleLifecycleServiceTest {

    private static final Clock CLOCK =
            Clock.fixed(Instant.parse("2026-10-16T00:00:00Z"), ZoneOffset.UTC);
    private static final MedicalRuleCodec CODEC = new MedicalRuleCodec();

    private static final IdentityContext ADMIN = new IdentityContext("admin-1", "管理员一",
            null, null, null, null, Set.of("PLATFORM_ADMIN"), Set.of(), Set.of(), true, false);
    private static final IdentityContext TENANT_ADMIN = new IdentityContext("tenant-admin-1",
            "租户管理员", "tenant-1", "租户一", null, null, Set.of("TENANT_ADMIN"),
            Set.of(), Set.of(), false, true);
    private static final IdentityContext USER = new IdentityContext("user-1", "用户一",
            "tenant-1", "租户一", null, null, Set.of("USER"), Set.of(), Set.of(), false, false);

    private static final String EMERGENCY_CONTENT = "{\"schemaVersion\":1,\"kind\":\"EMERGENCY\","
            + "\"condition\":{\"triageCategories\":[\"EMERGENCY\"],\"diseaseNames\":[\"stroke\"]},"
            + "\"action\":{\"priority\":100,\"slaSeconds\":0,\"escalationLevels\":[300],"
            + "\"awaitingTakeoverScript\":\"已为您联系专员，请稍候。\","
            + "\"takenOverScript\":\"专员已接管。\"}}";
    private static final String SAFETY_CONTENT = "{\"schemaVersion\":1,\"kind\":\"SAFETY_PATTERN\","
            + "\"condition\":{\"patterns\":[\"免费义诊\"],\"fieldLabel\":\"规则数据\"},\"action\":{}}";

    private final MedicalRuleRepository rules = mock(MedicalRuleRepository.class);
    private final SafetyPatternStore safetyPatterns = mock(SafetyPatternStore.class);
    private final MedicalRuleValidator validator =
            new MedicalRuleValidator(CODEC, new TextSafetyScreen(), CLOCK);

    private MedicalRuleLifecycleService service() {
        return new MedicalRuleLifecycleService(rules, validator, CODEC, safetyPatterns);
    }

    private static MedicalRuleRow globalRow(String id, String kind, String content) {
        return new MedicalRuleRow(id, "code-" + id, kind, MedicalRuleRow.ScopeType.GLOBAL,
                null, null, null, null, null, content, "manual",
                LocalDateTime.now(CLOCK).minusDays(1), LocalDateTime.now(CLOCK).plusDays(1),
                MedicalRuleRow.Status.PENDING, "admin", null);
    }

    private static MedicalRuleRow tenantRow(String id) {
        return new MedicalRuleRow(id, "code-" + id, "EMERGENCY", MedicalRuleRow.ScopeType.TENANT,
                "tenant-1", "租户一", null, null, null, EMERGENCY_CONTENT, "manual",
                LocalDateTime.now(CLOCK).minusDays(1), LocalDateTime.now(CLOCK).plusDays(1),
                MedicalRuleRow.Status.PENDING, "admin", null);
    }

    private static MedicalRuleApprovalState state(String id, MedicalRuleRow.Status status,
            long version, String reportJson) {
        return new MedicalRuleApprovalState(id, status, version, null, null, null, reportJson);
    }

    private static MedicalRuleRepository.RuleReviewSnapshot snapshot(MedicalRuleRow row,
            MedicalRuleApprovalState state) {
        return new MedicalRuleRepository.RuleReviewSnapshot(row, state);
    }

    private static String report(String ruleCode, long version, boolean passed) {
        return new RuleValidationReport(ruleCode, version, passed,
                passed ? List.of() : List.of("violation")).toJson();
    }

    @Test
    void submitByUnauthorizedIdentityNeverTouchesTheStore() {
        var result = service().submit(globalRow("r1", "EMERGENCY", EMERGENCY_CONTENT), USER)
                .block(Duration.ofSeconds(5));

        assertThat(result.status()).isEqualTo(SubmissionStatus.NOT_AUTHORIZED);
        assertThat(result.state()).isNull();
        verify(rules, never()).upsertPending(any());
    }

    @Test
    void submitNormalizesIdStatusAndCreatorThenUpserts() {
        when(rules.upsertPending(any()))
                .thenReturn(Mono.just(state("ignored", MedicalRuleRow.Status.PENDING, 1, null)));
        var draft = new MedicalRuleRow(null, "code-1", "EMERGENCY",
                MedicalRuleRow.ScopeType.GLOBAL, null, null, null, null, null, EMERGENCY_CONTENT,
                "manual", LocalDateTime.now(CLOCK).minusDays(1),
                LocalDateTime.now(CLOCK).plusDays(1), MedicalRuleRow.Status.APPROVED,
                "someone-else", null);

        var result = service().submit(draft, ADMIN).block(Duration.ofSeconds(5));

        assertThat(result.status()).isEqualTo(SubmissionStatus.SUBMITTED);
        var captor = ArgumentCaptor.forClass(MedicalRuleRow.class);
        verify(rules).upsertPending(captor.capture());
        var normalized = captor.getValue();
        assertThat(normalized.id()).isNotBlank();                                 // UUID generated
        assertThat(normalized.status()).isEqualTo(MedicalRuleRow.Status.PENDING); // forced PENDING
        assertThat(normalized.createdBy()).isEqualTo("admin-1");                  // submitter wins
        verify(safetyPatterns, never()).reload(); // EMERGENCY rules don't touch the pattern store
    }

    @Test
    void reviewPersistsTheValidatorReportWithoutBumpingVersion() {
        when(rules.findForReview("r1")).thenReturn(Mono.just(snapshot(
                globalRow("r1", "EMERGENCY", EMERGENCY_CONTENT),
                state("r1", MedicalRuleRow.Status.PENDING, 3, null))));
        when(rules.saveValidationReport(anyString(), anyString())).thenReturn(Mono.empty());

        var report = service().review("r1").block(Duration.ofSeconds(5));

        assertThat(report.passed()).isTrue();
        assertThat(report.ruleCode()).isEqualTo("code-r1");
        assertThat(report.approvalVersion()).isEqualTo(3);
        verify(rules).saveValidationReport(eq("r1"), eq(report.toJson()));
        verify(rules, never()).compareAndSetStatus(anyString(), anyLong(), any(), any(), any());
    }

    @Test
    void reviewFlagsAJunkRuleRed() {
        when(rules.findForReview("r2")).thenReturn(Mono.just(snapshot(
                globalRow("r2", "EMERGENCY", "{\"junk\":true}"),
                state("r2", MedicalRuleRow.Status.PENDING, 0, null))));
        when(rules.saveValidationReport(anyString(), anyString())).thenReturn(Mono.empty());

        var report = service().review("r2").block(Duration.ofSeconds(5));

        assertThat(report.passed()).isFalse();
        assertThat(report.violations()).isNotEmpty();
        verify(rules).saveValidationReport(eq("r2"), eq(report.toJson()));
    }

    @Test
    void approveAppliesOnGreenReportAtCurrentVersion() {
        when(rules.findForReview("r1")).thenReturn(Mono.just(snapshot(
                globalRow("r1", "EMERGENCY", EMERGENCY_CONTENT),
                state("r1", MedicalRuleRow.Status.PENDING, 2, report("code-r1", 2, true)))));
        when(rules.compareAndSetStatus("r1", 2, MedicalRuleRow.Status.APPROVED, "admin-1", null))
                .thenReturn(Mono.just(true));

        assertThat(service().approve("r1", 2, ADMIN).block(Duration.ofSeconds(5)))
                .isEqualTo(LifecycleOutcome.APPLIED);
    }

    @Test
    void approveFencesStaleVersionInvalidTransitionAndMissingOrStaleReports() {
        // version moved after the reviewer read it
        when(rules.findForReview("r1")).thenReturn(Mono.just(snapshot(
                globalRow("r1", "EMERGENCY", EMERGENCY_CONTENT),
                state("r1", MedicalRuleRow.Status.PENDING, 3, report("code-r1", 3, true)))));
        assertThat(service().approve("r1", 2, ADMIN).block(Duration.ofSeconds(5)))
                .isEqualTo(LifecycleOutcome.STALE_VERSION);

        // already APPROVED
        when(rules.findForReview("r1")).thenReturn(Mono.just(snapshot(
                globalRow("r1", "EMERGENCY", EMERGENCY_CONTENT),
                state("r1", MedicalRuleRow.Status.APPROVED, 2, report("code-r1", 2, true)))));
        assertThat(service().approve("r1", 2, ADMIN).block(Duration.ofSeconds(5)))
                .isEqualTo(LifecycleOutcome.INVALID_TRANSITION);

        // red report at the current version
        when(rules.findForReview("r1")).thenReturn(Mono.just(snapshot(
                globalRow("r1", "EMERGENCY", EMERGENCY_CONTENT),
                state("r1", MedicalRuleRow.Status.PENDING, 2, report("code-r1", 2, false)))));
        assertThat(service().approve("r1", 2, ADMIN).block(Duration.ofSeconds(5)))
                .isEqualTo(LifecycleOutcome.GATES_NOT_PASSED);

        // green report from an older version
        when(rules.findForReview("r1")).thenReturn(Mono.just(snapshot(
                globalRow("r1", "EMERGENCY", EMERGENCY_CONTENT),
                state("r1", MedicalRuleRow.Status.PENDING, 2, report("code-r1", 1, true)))));
        assertThat(service().approve("r1", 2, ADMIN).block(Duration.ofSeconds(5)))
                .isEqualTo(LifecycleOutcome.GATES_NOT_PASSED);

        // no such rule
        when(rules.findForReview("missing")).thenReturn(Mono.empty());
        assertThat(service().approve("missing", 0, ADMIN).block(Duration.ofSeconds(5)))
                .isEqualTo(LifecycleOutcome.MISSING);

        verify(rules, never()).compareAndSetStatus(anyString(), anyLong(), any(), any(), any());
    }

    @Test
    void approvalAuthorityIsScopeAware() {
        when(rules.findForReview("r9")).thenReturn(Mono.just(snapshot(tenantRow("r9"),
                state("r9", MedicalRuleRow.Status.PENDING, 0, report("code-r9", 0, true)))));
        when(rules.compareAndSetStatus("r9", 0, MedicalRuleRow.Status.APPROVED,
                "tenant-admin-1", null)).thenReturn(Mono.just(true));

        // own-tenant admin may approve a TENANT-scoped rule (spec §6 — unlike packages)
        assertThat(service().approve("r9", 0, TENANT_ADMIN).block(Duration.ofSeconds(5)))
                .isEqualTo(LifecycleOutcome.APPLIED);
        // plain users may not
        assertThat(service().approve("r9", 0, USER).block(Duration.ofSeconds(5)))
                .isEqualTo(LifecycleOutcome.NOT_AUTHORIZED);

        // GLOBAL rules stay platform-admin only
        when(rules.findForReview("r1")).thenReturn(Mono.just(snapshot(
                globalRow("r1", "EMERGENCY", EMERGENCY_CONTENT),
                state("r1", MedicalRuleRow.Status.PENDING, 0, report("code-r1", 0, true)))));
        assertThat(service().approve("r1", 0, TENANT_ADMIN).block(Duration.ofSeconds(5)))
                .isEqualTo(LifecycleOutcome.NOT_AUTHORIZED);
    }

    @Test
    void safetyPatternLifecycleActionsReloadTheRuntimeStore() {
        when(safetyPatterns.reload()).thenReturn(Mono.empty());
        when(rules.upsertPending(any()))
                .thenReturn(Mono.just(state("r5", MedicalRuleRow.Status.PENDING, 1, null)));
        service().submit(globalRow("r5", "SAFETY_PATTERN", SAFETY_CONTENT), ADMIN)
                .block(Duration.ofSeconds(5));
        verify(safetyPatterns, times(1)).reload();

        when(rules.findForReview("r5")).thenReturn(Mono.just(snapshot(
                globalRow("r5", "SAFETY_PATTERN", SAFETY_CONTENT),
                state("r5", MedicalRuleRow.Status.PENDING, 1, report("code-r5", 1, true)))));
        when(rules.compareAndSetStatus("r5", 1, MedicalRuleRow.Status.APPROVED, "admin-1", null))
                .thenReturn(Mono.just(true));
        assertThat(service().approve("r5", 1, ADMIN).block(Duration.ofSeconds(5)))
                .isEqualTo(LifecycleOutcome.APPLIED);
        verify(safetyPatterns, times(2)).reload();

        when(rules.findForReview("r5")).thenReturn(Mono.just(snapshot(
                globalRow("r5", "SAFETY_PATTERN", SAFETY_CONTENT),
                state("r5", MedicalRuleRow.Status.APPROVED, 1, report("code-r5", 1, true)))));
        when(rules.compareAndSetStatus("r5", 1, MedicalRuleRow.Status.REVOKED, "admin-1",
                "撤回")).thenReturn(Mono.just(true));
        assertThat(service().revoke("r5", 1, "撤回", ADMIN).block(Duration.ofSeconds(5)))
                .isEqualTo(LifecycleOutcome.APPLIED);
        verify(safetyPatterns, times(3)).reload();
    }

    @Test
    void rejectAndRevokeRequireReasonAndFenceTransitions() {
        assertThatThrownBy(() -> service().reject("r1", 0, " ", ADMIN))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("rejection reason is required");
        assertThatThrownBy(() -> service().revoke("r1", 0, null, ADMIN))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("rejection reason is required");

        // revoking a PENDING rule is not a valid transition
        when(rules.findForReview("r1")).thenReturn(Mono.just(snapshot(
                globalRow("r1", "EMERGENCY", EMERGENCY_CONTENT),
                state("r1", MedicalRuleRow.Status.PENDING, 0, null))));
        assertThat(service().revoke("r1", 0, "原因", ADMIN).block(Duration.ofSeconds(5)))
                .isEqualTo(LifecycleOutcome.INVALID_TRANSITION);

        // rejecting a PENDING rule applies and persists the reason
        when(rules.compareAndSetStatus("r1", 0, MedicalRuleRow.Status.REJECTED, "admin-1",
                "依据不足")).thenReturn(Mono.just(true));
        assertThat(service().reject("r1", 0, "依据不足", ADMIN).block(Duration.ofSeconds(5)))
                .isEqualTo(LifecycleOutcome.APPLIED);
    }

    @Test
    void casLossOnTransitionReportsStaleVersion() {
        when(rules.findForReview("r1")).thenReturn(Mono.just(snapshot(
                globalRow("r1", "EMERGENCY", EMERGENCY_CONTENT),
                state("r1", MedicalRuleRow.Status.PENDING, 2, report("code-r1", 2, true)))));
        when(rules.compareAndSetStatus("r1", 2, MedicalRuleRow.Status.APPROVED, "admin-1", null))
                .thenReturn(Mono.just(false));

        assertThat(service().approve("r1", 2, ADMIN).block(Duration.ofSeconds(5)))
                .isEqualTo(LifecycleOutcome.STALE_VERSION);
    }
}
