package io.github.sukimiya.medical.hospital;

import java.time.LocalDateTime;

/**
 * One row of medical_human_tasks (V9, extended by V11): either a NORMAL request handed to a
 * hospital's human channel or an ECALL escalation task owned by the platform's medical ops
 * desk. PENDING means "submitted, awaiting confirmation" — never "done" (spec §5). The
 * five-state conversation machine (Plan 4 spec §8) is derived from ECALL rows: PENDING =
 * awaiting takeover, CLAIMED = taken over, COMPLETED = back to normal.
 */
public record MedicalHumanTaskRow(
        String id, String conversationId, String hospitalCode, String toolName,
        String argumentsJson, TaskStatus status, String createdBy, LocalDateTime createdAt,
        TaskKind taskKind, Integer priority, LocalDateTime slaDeadline, int escalationLevel,
        String claimedBy, LocalDateTime claimedAt, LocalDateTime completedAt,
        String resolutionNote) {

    public enum TaskStatus { PENDING, CLAIMED, COMPLETED }

    public enum TaskKind { NORMAL, ECALL }

    /** Fixed pseudo-hospital for E-call rows (Ruling R5): columns are NOT NULL. */
    public static final String ECALL_HOSPITAL_CODE = "ecall";
    public static final String ECALL_TOOL_NAME = "submitTriage";

    /** Legacy NORMAL task (C01 human channel) with V11 defaults. */
    public static MedicalHumanTaskRow normal(String id, String conversationId, String hospitalCode,
            String toolName, String argumentsJson, TaskStatus status, String createdBy,
            LocalDateTime createdAt) {
        return new MedicalHumanTaskRow(id, conversationId, hospitalCode, toolName, argumentsJson,
                status, createdBy, createdAt, TaskKind.NORMAL, null, null, 0, null, null, null,
                null);
    }

    /** New E-call task: PENDING, level 0, EcallPayload JSON as arguments (Ruling R5). */
    public static MedicalHumanTaskRow ecall(String id, String conversationId, String payloadJson,
            String createdBy, Integer priority, LocalDateTime slaDeadline) {
        return new MedicalHumanTaskRow(id, conversationId, ECALL_HOSPITAL_CODE, ECALL_TOOL_NAME,
                payloadJson, TaskStatus.PENDING, createdBy, null, TaskKind.ECALL, priority,
                slaDeadline, 0, null, null, null, null);
    }
}
