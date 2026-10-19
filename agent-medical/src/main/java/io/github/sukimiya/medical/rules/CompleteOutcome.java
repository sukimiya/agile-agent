package io.github.sukimiya.medical.rules;

/**
 * Outcome of a completion attempt (Plan 4 spec §8): ECALL requires CLAIMED-by-owner (or a
 * platform-admin override); NORMAL completes directly from PENDING (the hospital-confirmation
 * desk entry) or from CLAIMED by owner/admin.
 */
public enum CompleteOutcome {
    /** CAS won: the task is COMPLETED and its conversation returns to the normal state. */
    APPLIED,
    /** No task with that id exists. */
    MISSING,
    /** The actor lacks MEDICAL_OPS/platform-admin authority. */
    NOT_AUTHORIZED,
    /** The task's kind/status does not allow this actor to complete it, or the CAS lost. */
    INVALID_STATE
}
