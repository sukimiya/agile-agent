package io.github.sukimiya.medical.transport;

import java.util.UUID;

import io.github.sukimiya.agileagent.spi.capability.ToolResult;
import io.github.sukimiya.medical.hospital.MedicalHumanTaskRepository;
import io.github.sukimiya.medical.hospital.MedicalHumanTaskRow;
import io.github.sukimiya.medical.hospital.TransportType;
import reactor.core.publisher.Mono;

/**
 * HUMAN transport: records a PENDING task and returns pending semantics — the hospital
 * has confirmed nothing yet, and the text must say so (spec §5 pending 语义红线).
 */
public final class HumanTaskTransportExecutor implements TransportExecutor {

    private final MedicalHumanTaskRepository tasks;

    public HumanTaskTransportExecutor(MedicalHumanTaskRepository tasks) {
        this.tasks = tasks;
    }

    @Override
    public TransportType type() {
        return TransportType.HUMAN;
    }

    @Override
    public Mono<ToolResult> execute(TransportRequest request) {
        String taskId = UUID.randomUUID().toString();
        var row = new MedicalHumanTaskRow(
                taskId,
                request.context().conversationId(),
                request.hospitalCode(),
                request.toolName(),
                request.arguments().toString(),
                MedicalHumanTaskRow.TaskStatus.PENDING,
                request.context().identity().subject(),
                null);
        return tasks.insert(row).thenReturn(new ToolResult(true,
                "已提交至 " + request.hospitalName() + " 人工服务通道（任务编号 " + taskId
                        + "），工作人员将跟进处理；结果以医院确认为准，当前尚未确认任何预约。"));
    }
}
