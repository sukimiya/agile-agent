package io.github.sukimiya.medical.hospital;

import java.time.LocalDateTime;

/**
 * One row of medical_human_tasks (V9): a request handed to a hospital's human channel.
 * PENDING means "submitted, awaiting hospital confirmation" — never "done" (spec §5).
 */
public record MedicalHumanTaskRow(
        String id, String conversationId, String hospitalCode, String toolName,
        String argumentsJson, TaskStatus status, String createdBy, LocalDateTime createdAt) {

    public enum TaskStatus { PENDING }
}
