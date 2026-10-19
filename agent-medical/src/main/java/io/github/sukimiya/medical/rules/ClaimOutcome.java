package io.github.sukimiya.medical.rules;

/** Outcome of an atomic takeover attempt (Plan 4 spec §8). */
public enum ClaimOutcome {
    /** CAS won: the task is now CLAIMED by this actor. */
    APPLIED,
    /** No task with that id exists. */
    MISSING,
    /** Not claimable right now — somebody else won the CAS (exactly one winner per task). */
    CONFLICT,
    /** The actor is neither MEDICAL_OPS nor a platform admin. */
    NOT_AUTHORIZED
}
