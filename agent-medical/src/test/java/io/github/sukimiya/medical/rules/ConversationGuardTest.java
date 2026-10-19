package io.github.sukimiya.medical.rules;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.Test;

import io.github.sukimiya.medical.hospital.MedicalHumanTaskRepository;
import io.github.sukimiya.medical.hospital.MedicalHumanTaskRow;
import reactor.core.publisher.Mono;

class ConversationGuardTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 16, 12, 0);
    private static final EcallPayload PAYLOAD = new EcallPayload("global-emergency-baseline",
            "EMERGENCY", null, 100, 0, List.of(300), "请稍候。", "已接管。");

    private final MedicalHumanTaskRepository tasks = mock(MedicalHumanTaskRepository.class);
    private final EscalationService escalation = mock(EscalationService.class);

    private ConversationGuard guard() {
        return new ConversationGuard(tasks, escalation);
    }

    private static MedicalHumanTaskRow ecall(MedicalHumanTaskRow.TaskStatus status,
            String payloadJson) {
        return new MedicalHumanTaskRow("t1", "conv-1", MedicalHumanTaskRow.ECALL_HOSPITAL_CODE,
                MedicalHumanTaskRow.ECALL_TOOL_NAME, payloadJson, status, "user-1", NOW,
                MedicalHumanTaskRow.TaskKind.ECALL, 100, NOW, 0, null, null, null, null);
    }

    @Test
    void noOpenEcallIsNormal() {
        when(escalation.escalateOverdue("conv-1")).thenReturn(Mono.empty());
        when(tasks.findOpenEcallByConversationId("conv-1")).thenReturn(Mono.empty());

        var state = guard().state("conv-1").block(Duration.ofSeconds(5));

        assertThat(state.state()).isEqualTo(ConversationState.State.NORMAL);
        assertThat(state.restricted()).isFalse();
        // the lazy SLA double-check always runs before the derivation (spec §2 ruling 5)
        verify(escalation).escalateOverdue("conv-1");
    }

    @Test
    void pendingEcallIsAwaitingTakeoverWithPayload() {
        when(escalation.escalateOverdue("conv-1")).thenReturn(Mono.empty());
        when(tasks.findOpenEcallByConversationId("conv-1"))
                .thenReturn(Mono.just(ecall(MedicalHumanTaskRow.TaskStatus.PENDING,
                        PAYLOAD.toJson())));

        var state = guard().state("conv-1").block(Duration.ofSeconds(5));

        assertThat(state.state()).isEqualTo(ConversationState.State.AWAITING_TAKEOVER);
        assertThat(state.restricted()).isTrue();
        assertThat(state.payload().awaitingTakeoverScript()).isEqualTo("请稍候。");
        assertThat(state.ecallTask().id()).isEqualTo("t1");
    }

    @Test
    void claimedEcallIsTakenOver() {
        when(escalation.escalateOverdue("conv-1")).thenReturn(Mono.empty());
        when(tasks.findOpenEcallByConversationId("conv-1"))
                .thenReturn(Mono.just(ecall(MedicalHumanTaskRow.TaskStatus.CLAIMED,
                        PAYLOAD.toJson())));

        var state = guard().state("conv-1").block(Duration.ofSeconds(5));

        assertThat(state.state()).isEqualTo(ConversationState.State.TAKEN_OVER);
        assertThat(state.restricted()).isTrue();
        assertThat(state.payload().takenOverScript()).isEqualTo("已接管。");
    }

    @Test
    void queryFailureFailsClosedToRestrictedState() {
        // Review Focus #1: the guard NEVER errors through to the provider — a broken
        // lookup must land in the restricted state (zero tools), not the normal one.
        when(escalation.escalateOverdue("conv-1")).thenReturn(Mono.empty());
        when(tasks.findOpenEcallByConversationId("conv-1"))
                .thenReturn(Mono.error(new IllegalStateException("db down")));

        var state = guard().state("conv-1").block(Duration.ofSeconds(5));

        assertThat(state.state()).isEqualTo(ConversationState.State.FAIL_CLOSED);
        assertThat(state.restricted()).isTrue();
    }

    @Test
    void corruptPayloadFailsClosed() {
        // Review Focus #1: an open E-call whose payload cannot be parsed is still an open
        // E-call — restricted, never normal (fail closed, 宁闸勿放).
        when(escalation.escalateOverdue("conv-1")).thenReturn(Mono.empty());
        when(tasks.findOpenEcallByConversationId("conv-1"))
                .thenReturn(Mono.just(ecall(MedicalHumanTaskRow.TaskStatus.PENDING, "{oops")));

        var state = guard().state("conv-1").block(Duration.ofSeconds(5));

        assertThat(state.state()).isEqualTo(ConversationState.State.FAIL_CLOSED);
        assertThat(state.restricted()).isTrue();
    }
}
