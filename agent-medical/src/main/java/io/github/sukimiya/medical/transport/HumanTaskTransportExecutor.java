package io.github.sukimiya.medical.transport;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.UUID;

import io.github.sukimiya.agileagent.spi.capability.ToolResult;
import io.github.sukimiya.medical.hospital.MedicalHumanTaskRepository;
import io.github.sukimiya.medical.hospital.MedicalHumanTaskRow;
import io.github.sukimiya.medical.hospital.TransportType;
import reactor.core.publisher.Mono;

/**
 * HUMAN transport: records a PENDING task and returns pending semantics — the hospital has
 * confirmed nothing yet, and the text must say so (spec §5 pending 语义红线). Retries are
 * deduplicated by a submission key derived from the request tuple (spec §14 resolution 7),
 * so a double-tapped consultation stays ONE human task and the retry reports the original
 * task id instead of inventing a second one.
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
        return tasks.insertOrFindExisting(row, submissionKey(request))
                .map(task -> new ToolResult(true,
                        "已提交至 " + request.hospitalName() + " 人工服务通道（任务编号 " + task.id()
                                + "），工作人员将跟进处理；结果以医院确认为准，当前尚未确认任何预约。"));
    }

    /**
     * SHA-256 hex (64 chars — exactly the VARCHAR(64) column) of the request tuple. Argument
     * JSON is keyed in Jackson's insertion order; full key-order canonicalization is deferred
     * to Plan 5 (C05), where the conversation-level runtime is revisited.
     */
    static String submissionKey(TransportRequest request) {
        String canonical = request.context().conversationId() + "\n"
                + request.hospitalCode() + "\n"
                + request.toolName() + "\n"
                + request.arguments().toString();
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256")
                    .digest(canonical.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                hex.append(Character.forDigit((b >> 4) & 0xF, 16))
                        .append(Character.forDigit(b & 0xF, 16));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is mandated by the JVM specification", e);
        }
    }
}
