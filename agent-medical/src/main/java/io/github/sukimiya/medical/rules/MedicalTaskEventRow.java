package io.github.sukimiya.medical.rules;

import java.time.LocalDateTime;

/**
 * One row of medical_task_events (V11): the audit trail of an E-call or human task —
 * CREATED / CLAIMED / COMPLETED / ESCALATED (Plan 4 spec §4). Actor is null for
 * system-driven escalation events. Detail carries pinned markers for idempotency checks
 * (escalation ceiling, stale-claim release) and human-readable context otherwise.
 * Hostnames are allowed here; credentials and access tokens never are (spec §11).
 */
public record MedicalTaskEventRow(
        String id, String taskId, EventType eventType, int level, String actor, String detail,
        LocalDateTime createdAt) {

    public enum EventType { CREATED, CLAIMED, COMPLETED, ESCALATED }
}
