package io.github.sukimiya.medical.transport;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import io.github.sukimiya.agileagent.spi.capability.AgentExecutionContext;
import io.github.sukimiya.agileagent.spi.identity.IdentityContext;
import io.github.sukimiya.medical.hospital.MedicalHumanTaskRepository;
import io.github.sukimiya.medical.hospital.MedicalHumanTaskRow;
import io.github.sukimiya.medical.hospital.TransportBinding;
import reactor.core.publisher.Mono;

class HumanTaskTransportExecutorTest {

    private final MedicalHumanTaskRepository tasks = mock(MedicalHumanTaskRepository.class);
    private final HumanTaskTransportExecutor executor = new HumanTaskTransportExecutor(tasks);

    private static TransportRequest request() {
        return request(new ObjectMapper().createObjectNode().put("keyword", "cardiology"));
    }

    private static TransportRequest request(ObjectNode arguments) {
        var identity = new IdentityContext("user-1", "用户一", "tenant-1", "租户一", "org-1", "机构一",
                Set.of("USER"), Set.of(), Set.of(), false, false);
        var context = new AgentExecutionContext(identity, "conv-1", () -> "token");
        return new TransportRequest("demo", "演示医院", "requestConsultation",
                TransportBinding.human("wechat-desk"), arguments, context);
    }

    @Test
    void persistsPendingTaskAndReturnsPendingSemanticsWithoutClaimingConfirmation() {
        when(tasks.insertOrFindExisting(any(), anyString()))
                .thenAnswer(invocation -> Mono.just(invocation.getArgument(0)));

        var result = executor.execute(request()).block(Duration.ofSeconds(10));

        var rowCaptor = ArgumentCaptor.forClass(MedicalHumanTaskRow.class);
        var keyCaptor = ArgumentCaptor.forClass(String.class);
        verify(tasks).insertOrFindExisting(rowCaptor.capture(), keyCaptor.capture());
        var row = rowCaptor.getValue();
        assertThat(row.id()).isNotBlank();
        assertThat(row.conversationId()).isEqualTo("conv-1");
        assertThat(row.hospitalCode()).isEqualTo("demo");
        assertThat(row.toolName()).isEqualTo("requestConsultation");
        assertThat(row.argumentsJson()).contains("cardiology");
        assertThat(row.status()).isEqualTo(MedicalHumanTaskRow.TaskStatus.PENDING);
        assertThat(row.createdBy()).isEqualTo("user-1");
        assertThat(keyCaptor.getValue()).hasSize(64).matches("[0-9a-f]{64}");
        assertThat(result.success()).isTrue();
        assertThat(result.displayResult())
                .contains("演示医院")
                .contains(row.id())
                .contains("尚未确认任何预约")
                .doesNotContain("已预约")
                .doesNotContain("提交成功");
    }

    @Test
    void repositoryFailurePropagatesInsteadOfFakingSuccess() {
        when(tasks.insertOrFindExisting(any(), anyString()))
                .thenReturn(Mono.error(new IllegalStateException("db down")));

        assertThatThrownBy(() -> executor.execute(request()).block(Duration.ofSeconds(10)))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void sameRequestTupleComputesSameSubmissionKey() {
        assertThat(HumanTaskTransportExecutor.submissionKey(request()))
                .isEqualTo(HumanTaskTransportExecutor.submissionKey(request()));
    }

    @Test
    void differentArgumentsComputeDifferentSubmissionKeys() {
        var other = new ObjectMapper().createObjectNode().put("keyword", "neurology");
        assertThat(HumanTaskTransportExecutor.submissionKey(request()))
                .isNotEqualTo(HumanTaskTransportExecutor.submissionKey(request(other)));
    }

    @Test
    void deduplicatedRetryReportsTheExistingTaskId() {
        var existing = new MedicalHumanTaskRow("task-existing", "conv-1", "demo", "requestConsultation",
                "{\"keyword\":\"cardiology\"}", MedicalHumanTaskRow.TaskStatus.PENDING, "user-1", null);
        when(tasks.insertOrFindExisting(any(), anyString())).thenReturn(Mono.just(existing));

        var result = executor.execute(request()).block(Duration.ofSeconds(10));

        assertThat(result.success()).isTrue();
        assertThat(result.displayResult()).contains("task-existing").contains("尚未确认任何预约");
    }
}
