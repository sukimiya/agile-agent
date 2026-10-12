package io.github.sukimiya.medical.transport;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.fasterxml.jackson.databind.ObjectMapper;

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
        var identity = new IdentityContext("user-1", "用户一", "tenant-1", "租户一", "org-1", "机构一",
                Set.of("USER"), Set.of(), Set.of(), false, false);
        var context = new AgentExecutionContext(identity, "conv-1", () -> "token");
        var arguments = new ObjectMapper().createObjectNode().put("keyword", "cardiology");
        return new TransportRequest("demo", "演示医院", "requestConsultation",
                TransportBinding.human("wechat-desk"), arguments, context);
    }

    @Test
    void persistsPendingTaskAndReturnsPendingSemanticsWithoutClaimingConfirmation() {
        when(tasks.insert(any())).thenReturn(Mono.empty());

        var result = executor.execute(request()).block(Duration.ofSeconds(10));

        var captor = ArgumentCaptor.forClass(MedicalHumanTaskRow.class);
        verify(tasks).insert(captor.capture());
        var row = captor.getValue();
        assertThat(row.id()).isNotBlank();
        assertThat(row.conversationId()).isEqualTo("conv-1");
        assertThat(row.hospitalCode()).isEqualTo("demo");
        assertThat(row.toolName()).isEqualTo("requestConsultation");
        assertThat(row.argumentsJson()).contains("cardiology");
        assertThat(row.status()).isEqualTo(MedicalHumanTaskRow.TaskStatus.PENDING);
        assertThat(row.createdBy()).isEqualTo("user-1");
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
        when(tasks.insert(any())).thenReturn(Mono.error(new IllegalStateException("db down")));

        assertThatThrownBy(() -> executor.execute(request()).block(Duration.ofSeconds(10)))
                .isInstanceOf(IllegalStateException.class);
    }
}
