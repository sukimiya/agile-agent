package io.github.sukimiya.medical.rules;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
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
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import io.github.sukimiya.agileagent.spi.identity.IdentityContext;
import io.github.sukimiya.medical.hospital.MedicalHumanTaskRepository;
import io.github.sukimiya.medical.hospital.MedicalHumanTaskRow;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

class EscalationServiceTest {

    private static final Clock CLOCK =
            Clock.fixed(Instant.parse("2026-10-16T12:00:00Z"), ZoneOffset.UTC);
    private static final LocalDateTime NOW = LocalDateTime.now(CLOCK);
    private static final long STALE_SECONDS = 1800L;

    private static final IdentityContext OPS = new IdentityContext("ops-1", "专员一",
            "tenant-1", "租户一", null, null, Set.of("MEDICAL_OPS"), Set.of(), Set.of(),
            false, false);
    private static final IdentityContext OTHER_OPS = new IdentityContext("ops-2", "专员二",
            "tenant-1", "租户一", null, null, Set.of("MEDICAL_OPS"), Set.of(), Set.of(),
            false, false);
    private static final IdentityContext ADMIN = new IdentityContext("admin-1", "管理员一",
            null, null, null, null, Set.of("PLATFORM_ADMIN"), Set.of(), Set.of(), true, false);
    private static final IdentityContext USER = new IdentityContext("user-1", "用户一",
            "tenant-1", "租户一", null, null, Set.of("USER"), Set.of(), Set.of(), false, false);

    private static final EcallPayload PAYLOAD = new EcallPayload("global-emergency-baseline",
            "EMERGENCY", null, 100, 0, List.of(300, 900, 1800), "请稍候。", "已接管。");

    private final MedicalHumanTaskRepository tasks = mock(MedicalHumanTaskRepository.class);
    private final MedicalTaskEventRepository events = mock(MedicalTaskEventRepository.class);

    private EscalationService service() {
        return service(true);
    }

    private EscalationService service(boolean staleReleaseEnabled) {
        return new EscalationService(tasks, events, CLOCK, STALE_SECONDS, staleReleaseEnabled);
    }

    /** Open E-call row whose deadline passed 60s ago; CLAIMED rows carry a 2h-old claim (stale). */
    private static MedicalHumanTaskRow ecallRow(String id, MedicalHumanTaskRow.TaskStatus status,
            int level, String payloadJson) {
        boolean claimed = status == MedicalHumanTaskRow.TaskStatus.CLAIMED;
        return new MedicalHumanTaskRow(id, "conv-" + id, MedicalHumanTaskRow.ECALL_HOSPITAL_CODE,
                MedicalHumanTaskRow.ECALL_TOOL_NAME, payloadJson, status, "user-1", NOW,
                MedicalHumanTaskRow.TaskKind.ECALL, 100, NOW.minusSeconds(60), level,
                claimed ? "ops-9" : null, claimed ? NOW.minusHours(2) : null, null, null);
    }

    private static MedicalHumanTaskRow normalRow(String id,
            MedicalHumanTaskRow.TaskStatus status) {
        return MedicalHumanTaskRow.normal(id, "conv-" + id, "demo", "requestConsultation", "{}",
                status, "user-1", NOW);
    }

    // ---- createEcall (spec §7.4) ----

    @Test
    void createEcallInsertsTaskAndWritesCreatedAudit() {
        when(tasks.insertEcallOrFindExisting(any()))
                .thenAnswer(invocation -> Mono.just(invocation.getArgument(0)));
        when(events.insert(any())).thenReturn(Mono.empty());

        var task = service().createEcall("conv-1", "user-1", PAYLOAD).block(Duration.ofSeconds(5));

        assertThat(task.conversationId()).isEqualTo("conv-1");
        assertThat(task.taskKind()).isEqualTo(MedicalHumanTaskRow.TaskKind.ECALL);
        assertThat(task.status()).isEqualTo(MedicalHumanTaskRow.TaskStatus.PENDING);
        assertThat(task.slaDeadline()).isEqualTo(NOW); // slaSeconds=0 → immediately due
        var captor = ArgumentCaptor.forClass(MedicalTaskEventRow.class);
        verify(events).insert(captor.capture());
        assertThat(captor.getValue().eventType()).isEqualTo(MedicalTaskEventRow.EventType.CREATED);
        assertThat(captor.getValue().taskId()).isEqualTo(task.id());
        assertThat(captor.getValue().actor()).isEqualTo("user-1");
        assertThat(captor.getValue().detail())
                .contains("rule_code=global-emergency-baseline")
                .contains("triage_category=EMERGENCY");
    }

    @Test
    void createEcallReusesAnOpenTaskWithoutNewAudit() {
        // Review Focus #3: repeat triage on an open E-call never double-opens (index + reuse),
        // and the reuse path stays silent — no duplicate CREATED noise for the ops desk.
        var existing = ecallRow("ecall-1", MedicalHumanTaskRow.TaskStatus.PENDING, 0,
                PAYLOAD.toJson());
        when(tasks.insertEcallOrFindExisting(any())).thenReturn(Mono.just(existing));

        var task = service().createEcall("conv-1", "user-1", PAYLOAD).block(Duration.ofSeconds(5));

        assertThat(task.id()).isEqualTo("ecall-1");
        verifyNoInteractions(events);
    }

    // ---- claim (spec §8) ----

    @Test
    void claimByOpsAppliesAndAudits() {
        when(tasks.claim("t1", "ops-1", NOW)).thenReturn(Mono.just(true));
        when(events.insert(any())).thenReturn(Mono.empty());

        assertThat(service().claim("t1", OPS).block(Duration.ofSeconds(5)))
                .isEqualTo(ClaimOutcome.APPLIED);
        var captor = ArgumentCaptor.forClass(MedicalTaskEventRow.class);
        verify(events).insert(captor.capture());
        assertThat(captor.getValue().eventType()).isEqualTo(MedicalTaskEventRow.EventType.CLAIMED);
        assertThat(captor.getValue().actor()).isEqualTo("ops-1");
    }

    @Test
    void claimDistinguishesConflictFromMissing() {
        // Review Focus #2: two concurrent claims produce exactly one winner; the loser
        // gets CONFLICT (task exists, CAS lost), not an error.
        when(tasks.claim(eq("t1"), anyString(), any())).thenReturn(Mono.just(false));
        when(tasks.findById("t1")).thenReturn(Mono.just(
                ecallRow("t1", MedicalHumanTaskRow.TaskStatus.CLAIMED, 0, PAYLOAD.toJson())));
        assertThat(service().claim("t1", OTHER_OPS).block(Duration.ofSeconds(5)))
                .isEqualTo(ClaimOutcome.CONFLICT);

        when(tasks.claim(eq("gone"), anyString(), any())).thenReturn(Mono.just(false));
        when(tasks.findById("gone")).thenReturn(Mono.empty());
        assertThat(service().claim("gone", OTHER_OPS).block(Duration.ofSeconds(5)))
                .isEqualTo(ClaimOutcome.MISSING);
        verifyNoInteractions(events);
    }

    @Test
    void claimRequiresMedicalOpsOrPlatformAdmin() {
        assertThat(service().claim("t1", USER).block(Duration.ofSeconds(5)))
                .isEqualTo(ClaimOutcome.NOT_AUTHORIZED);
        verifyNoInteractions(tasks);

        when(tasks.claim("t1", "admin-1", NOW)).thenReturn(Mono.just(true));
        when(events.insert(any())).thenReturn(Mono.empty());
        assertThat(service().claim("t1", ADMIN).block(Duration.ofSeconds(5)))
                .isEqualTo(ClaimOutcome.APPLIED);
    }

    // ---- complete (spec §8) ----

    @Test
    void completeRequiresResolutionNote() {
        assertThatThrownBy(() -> service().complete("t1", " ", OPS))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("resolution note is required");
        assertThatThrownBy(() -> service().complete("t1", null, OPS))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("resolution note is required");
    }

    @Test
    void completeEcallByOwnerAppliesAndAudits() {
        var task = new MedicalHumanTaskRow("t1", "conv-1",
                MedicalHumanTaskRow.ECALL_HOSPITAL_CODE, MedicalHumanTaskRow.ECALL_TOOL_NAME,
                PAYLOAD.toJson(), MedicalHumanTaskRow.TaskStatus.CLAIMED, "user-1", NOW,
                MedicalHumanTaskRow.TaskKind.ECALL, 100, NOW, 0, "ops-1", NOW.minusMinutes(5),
                null, null);
        when(tasks.findById("t1")).thenReturn(Mono.just(task));
        when(tasks.completeClaimedByOwner("t1", "ops-1", "专员已联系患者", NOW))
                .thenReturn(Mono.just(true));
        when(events.insert(any())).thenReturn(Mono.empty());

        assertThat(service().complete("t1", "专员已联系患者", OPS).block(Duration.ofSeconds(5)))
                .isEqualTo(CompleteOutcome.APPLIED);
        var captor = ArgumentCaptor.forClass(MedicalTaskEventRow.class);
        verify(events).insert(captor.capture());
        assertThat(captor.getValue().eventType())
                .isEqualTo(MedicalTaskEventRow.EventType.COMPLETED);
        assertThat(captor.getValue().detail()).isEqualTo("专员已联系患者");
    }

    @Test
    void completeEcallMatrix() {
        // platform admin may complete another operator's claim (override path)
        var claimedByOps9 = new MedicalHumanTaskRow("t2", "conv-2",
                MedicalHumanTaskRow.ECALL_HOSPITAL_CODE, MedicalHumanTaskRow.ECALL_TOOL_NAME,
                PAYLOAD.toJson(), MedicalHumanTaskRow.TaskStatus.CLAIMED, "user-1", NOW,
                MedicalHumanTaskRow.TaskKind.ECALL, 100, NOW, 0, "ops-9", NOW, null, null);
        when(tasks.findById("t2")).thenReturn(Mono.just(claimedByOps9));
        when(tasks.completeClaimedAny("t2", "代完成", NOW)).thenReturn(Mono.just(true));
        when(events.insert(any())).thenReturn(Mono.empty());
        assertThat(service().complete("t2", "代完成", ADMIN).block(Duration.ofSeconds(5)))
                .isEqualTo(CompleteOutcome.APPLIED);

        // a different operator (not owner, not admin) may not
        assertThat(service().complete("t2", "x", OTHER_OPS).block(Duration.ofSeconds(5)))
                .isEqualTo(CompleteOutcome.INVALID_STATE);

        // an E-call still awaiting takeover must be claimed first
        var pending = ecallRow("t3", MedicalHumanTaskRow.TaskStatus.PENDING, 0, PAYLOAD.toJson());
        when(tasks.findById("t3")).thenReturn(Mono.just(pending));
        assertThat(service().complete("t3", "x", ADMIN).block(Duration.ofSeconds(5)))
                .isEqualTo(CompleteOutcome.INVALID_STATE);

        // unknown task
        when(tasks.findById("gone")).thenReturn(Mono.empty());
        assertThat(service().complete("gone", "x", OPS).block(Duration.ofSeconds(5)))
                .isEqualTo(CompleteOutcome.MISSING);

        // plain users never complete
        assertThat(service().complete("t2", "x", USER).block(Duration.ofSeconds(5)))
                .isEqualTo(CompleteOutcome.NOT_AUTHORIZED);
        verify(tasks, never()).completeFromPending(anyString(), anyString(), any());
    }

    @Test
    void completeNormalTaskFromPendingIsTheHospitalConfirmationEntry() {
        // C04 closure: NORMAL consults complete directly from PENDING (spec §8) — the desk
        // entry that records hospital confirmation. The agent-side red line is untouched:
        // nothing here lets the agent claim a booking before this human transition.
        when(tasks.findById("n1")).thenReturn(Mono.just(
                normalRow("n1", MedicalHumanTaskRow.TaskStatus.PENDING)));
        when(tasks.completeFromPending("n1", "医院已确认预约", NOW)).thenReturn(Mono.just(true));
        when(events.insert(any())).thenReturn(Mono.empty());

        assertThat(service().complete("n1", "医院已确认预约", OPS).block(Duration.ofSeconds(5)))
                .isEqualTo(CompleteOutcome.APPLIED);
    }

    // ---- SLA escalation (spec §8 冒泡升级, §9) ----

    @Test
    void escalateOverdueAdvancesLevelRecomputesDeadlineAndAudits() {
        var task = ecallRow("t1", MedicalHumanTaskRow.TaskStatus.PENDING, 0, PAYLOAD.toJson());
        when(tasks.findOverdueOpenEcalls(NOW)).thenReturn(Flux.just(task));
        when(tasks.escalate("t1", 0, 1, NOW.plusSeconds(300))).thenReturn(Mono.just(true));
        when(events.insert(any())).thenReturn(Mono.empty());

        assertThat(service().escalateOverdue().block(Duration.ofSeconds(5))).isEqualTo(1);

        var captor = ArgumentCaptor.forClass(MedicalTaskEventRow.class);
        verify(events).insert(captor.capture());
        assertThat(captor.getValue().eventType())
                .isEqualTo(MedicalTaskEventRow.EventType.ESCALATED);
        assertThat(captor.getValue().level()).isEqualTo(1);
    }

    @Test
    void escalateOverdueIsIdempotentUnderCasLoss() {
        var task = ecallRow("t1", MedicalHumanTaskRow.TaskStatus.PENDING, 0, PAYLOAD.toJson());
        when(tasks.findOverdueOpenEcalls(NOW)).thenReturn(Flux.just(task));
        when(tasks.escalate(anyString(), eq(0), eq(1), any())).thenReturn(Mono.just(false));

        assertThat(service().escalateOverdue().block(Duration.ofSeconds(5))).isZero();
        verifyNoInteractions(events);
    }

    @Test
    void oneBadRowDoesNotStopTheBatch() {
        var corrupt = ecallRow("bad", MedicalHumanTaskRow.TaskStatus.PENDING, 0, "{oops");
        var good = ecallRow("good", MedicalHumanTaskRow.TaskStatus.PENDING, 0, PAYLOAD.toJson());
        when(tasks.findOverdueOpenEcalls(NOW)).thenReturn(Flux.just(corrupt, good));
        when(tasks.escalate(eq("good"), eq(0), eq(1), any())).thenReturn(Mono.just(true));
        when(events.insert(any())).thenReturn(Mono.empty());

        assertThat(service().escalateOverdue().block(Duration.ofSeconds(5))).isEqualTo(1);
        verify(tasks).escalate(eq("good"), eq(0), eq(1), any());
        verify(tasks, never()).escalate(eq("bad"), anyInt(), anyInt(), any());
    }

    @Test
    void escalationCeilingWritesOneTerminalEventThenStops() {
        // Review Focus #5: level stops at escalationLevels.size(); one terminal audit
        // event, then silence forever (no repeated upgrades, no CAS churn).
        var maxed = ecallRow("t1", MedicalHumanTaskRow.TaskStatus.PENDING, 3, PAYLOAD.toJson());
        when(tasks.findOverdueOpenEcalls(NOW)).thenReturn(Flux.just(maxed));
        when(events.hasEvent("t1", MedicalTaskEventRow.EventType.ESCALATED,
                EscalationService.CEILING_DETAIL)).thenReturn(Mono.just(false));
        when(events.insert(any())).thenReturn(Mono.empty());

        assertThat(service().escalateOverdue().block(Duration.ofSeconds(5))).isZero();
        var captor = ArgumentCaptor.forClass(MedicalTaskEventRow.class);
        verify(events).insert(captor.capture());
        assertThat(captor.getValue().level()).isEqualTo(3);
        assertThat(captor.getValue().detail()).isEqualTo(EscalationService.CEILING_DETAIL);
        verify(tasks, never()).escalate(anyString(), anyInt(), anyInt(), any());

        // second pass: the terminal event exists → nothing at all
        when(events.hasEvent("t1", MedicalTaskEventRow.EventType.ESCALATED,
                EscalationService.CEILING_DETAIL)).thenReturn(Mono.just(true));
        assertThat(service().escalateOverdue().block(Duration.ofSeconds(5))).isZero();
        verify(events, times(1)).insert(any());
    }

    @Test
    void lazyConversationCheckEscalatesOverduePendingOnly() {
        var overdue = ecallRow("t1", MedicalHumanTaskRow.TaskStatus.PENDING, 0, PAYLOAD.toJson());
        when(tasks.findOpenEcallByConversationId("conv-t1")).thenReturn(Mono.just(overdue));
        when(tasks.escalate("t1", 0, 1, NOW.plusSeconds(300))).thenReturn(Mono.just(true));
        when(events.insert(any())).thenReturn(Mono.empty());

        service().escalateOverdue("conv-t1").block(Duration.ofSeconds(5));
        verify(tasks).escalate("t1", 0, 1, NOW.plusSeconds(300));

        // a CLAIMED (taken-over) task is never escalated — the SLA only chases PENDING
        var claimed = ecallRow("t2", MedicalHumanTaskRow.TaskStatus.CLAIMED, 0, PAYLOAD.toJson());
        when(tasks.findOpenEcallByConversationId("conv-t2")).thenReturn(Mono.just(claimed));
        service().escalateOverdue("conv-t2").block(Duration.ofSeconds(5));
        verify(tasks, never()).escalate(eq("t2"), anyInt(), anyInt(), any());
    }

    @Test
    void lazyConversationCheckSwallowsErrors() {
        when(tasks.findOpenEcallByConversationId("conv-broken"))
                .thenReturn(Mono.error(new IllegalStateException("db down")));

        // never throws: the guard's lazy double-check must not fail a conversation
        service().escalateOverdue("conv-broken").block(Duration.ofSeconds(5));
    }

    // ---- stale-claim recovery (spec §8 轻量恢复) ----

    @Test
    void staleClaimsAreReleasedBackToPendingWithAudit() {
        var stale = ecallRow("t1", MedicalHumanTaskRow.TaskStatus.CLAIMED, 0, PAYLOAD.toJson());
        when(tasks.findClaimedBefore(NOW.minusSeconds(STALE_SECONDS)))
                .thenReturn(Flux.just(stale));
        when(events.hasEvent("t1", MedicalTaskEventRow.EventType.ESCALATED,
                EscalationService.STALE_RELEASED_DETAIL + "@" + stale.claimedAt()))
                .thenReturn(Mono.just(false));
        when(tasks.releaseClaim("t1", stale.claimedAt())).thenReturn(Mono.just(true));
        when(events.insert(any())).thenReturn(Mono.empty());

        assertThat(service().recoverStaleClaims().block(Duration.ofSeconds(5))).isEqualTo(1);
        var captor = ArgumentCaptor.forClass(MedicalTaskEventRow.class);
        verify(events).insert(captor.capture());
        assertThat(captor.getValue().detail())
                .isEqualTo(EscalationService.STALE_RELEASED_DETAIL + "@" + stale.claimedAt());
    }

    @Test
    void staleClaimRecoveryCanBeDetectOnly() {
        var stale = ecallRow("t1", MedicalHumanTaskRow.TaskStatus.CLAIMED, 0, PAYLOAD.toJson());
        when(tasks.findClaimedBefore(NOW.minusSeconds(STALE_SECONDS)))
                .thenReturn(Flux.just(stale));
        when(events.hasEvent("t1", MedicalTaskEventRow.EventType.ESCALATED,
                EscalationService.STALE_DETECTED_DETAIL + "@" + stale.claimedAt()))
                .thenReturn(Mono.just(false));
        when(events.insert(any())).thenReturn(Mono.empty());

        assertThat(service(false).recoverStaleClaims().block(Duration.ofSeconds(5))).isZero();
        verify(tasks, never()).releaseClaim(anyString(), any());
        var captor = ArgumentCaptor.forClass(MedicalTaskEventRow.class);
        verify(events).insert(captor.capture());
        assertThat(captor.getValue().detail())
                .isEqualTo(EscalationService.STALE_DETECTED_DETAIL + "@" + stale.claimedAt());
    }

    @Test
    void staleRecoveryIsOneTimePerStuckClaim() {
        var stale = ecallRow("t1", MedicalHumanTaskRow.TaskStatus.CLAIMED, 0, PAYLOAD.toJson());
        when(tasks.findClaimedBefore(NOW.minusSeconds(STALE_SECONDS)))
                .thenReturn(Flux.just(stale));
        when(events.hasEvent("t1", MedicalTaskEventRow.EventType.ESCALATED,
                EscalationService.STALE_RELEASED_DETAIL + "@" + stale.claimedAt()))
                .thenReturn(Mono.just(true));

        assertThat(service().recoverStaleClaims().block(Duration.ofSeconds(5))).isZero();
        verify(tasks, never()).releaseClaim(anyString(), any());
        verify(events, never()).insert(any());
    }

    @Test
    void secondStuckClaimInstanceIsRecoveredAgain() {
        // Spec §8: a task released from one crashed operator, re-claimed, and stuck again
        // must be recoverable — the guard is scoped per claim instance (claimed_at), so
        // the first instance's audit never silences the second.
        LocalDateTime firstClaimedAt = NOW.minusHours(3);
        LocalDateTime secondClaimedAt = NOW.minusHours(1);
        var reclaimed = new MedicalHumanTaskRow("t1", "conv-t1",
                MedicalHumanTaskRow.ECALL_HOSPITAL_CODE, MedicalHumanTaskRow.ECALL_TOOL_NAME,
                PAYLOAD.toJson(), MedicalHumanTaskRow.TaskStatus.CLAIMED, "user-1", NOW,
                MedicalHumanTaskRow.TaskKind.ECALL, 100, NOW.minusSeconds(60), 0, "ops-8",
                secondClaimedAt, null, null);
        when(tasks.findClaimedBefore(NOW.minusSeconds(STALE_SECONDS)))
                .thenReturn(Flux.just(reclaimed));
        String marker1 = EscalationService.STALE_RELEASED_DETAIL + "@" + firstClaimedAt;
        String marker2 = EscalationService.STALE_RELEASED_DETAIL + "@" + secondClaimedAt;
        when(events.hasEvent("t1", MedicalTaskEventRow.EventType.ESCALATED, marker1))
                .thenReturn(Mono.just(true));
        when(events.hasEvent("t1", MedicalTaskEventRow.EventType.ESCALATED, marker2))
                .thenReturn(Mono.just(false));
        when(tasks.releaseClaim("t1", secondClaimedAt)).thenReturn(Mono.just(true));
        when(events.insert(any())).thenReturn(Mono.empty());

        assertThat(service().recoverStaleClaims().block(Duration.ofSeconds(5))).isEqualTo(1);
        verify(tasks).releaseClaim("t1", secondClaimedAt);
        var captor = ArgumentCaptor.forClass(MedicalTaskEventRow.class);
        verify(events).insert(captor.capture());
        assertThat(captor.getValue().eventType())
                .isEqualTo(MedicalTaskEventRow.EventType.ESCALATED);
        assertThat(captor.getValue().detail()).isEqualTo(marker2);
    }

    @Test
    void staleReleaseLostToCasWritesNoEvent() {
        var stale = ecallRow("t1", MedicalHumanTaskRow.TaskStatus.CLAIMED, 0, PAYLOAD.toJson());
        when(tasks.findClaimedBefore(NOW.minusSeconds(STALE_SECONDS)))
                .thenReturn(Flux.just(stale));
        when(events.hasEvent("t1", MedicalTaskEventRow.EventType.ESCALATED,
                EscalationService.STALE_RELEASED_DETAIL + "@" + stale.claimedAt()))
                .thenReturn(Mono.just(false));
        when(tasks.releaseClaim("t1", stale.claimedAt())).thenReturn(Mono.just(false));

        assertThat(service().recoverStaleClaims().block(Duration.ofSeconds(5))).isZero();
        verify(events, never()).insert(any());
    }

    @Test
    void tickRunsEscalationThenStaleRecovery() {
        when(tasks.findOverdueOpenEcalls(NOW)).thenReturn(Flux.empty());
        when(tasks.findClaimedBefore(NOW.minusSeconds(STALE_SECONDS))).thenReturn(Flux.empty());

        service().tick().block(Duration.ofSeconds(5));

        verify(tasks).findOverdueOpenEcalls(NOW);
        verify(tasks).findClaimedBefore(NOW.minusSeconds(STALE_SECONDS));
    }
}
