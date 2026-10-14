package io.github.sukimiya.medical.lifecycle;

/** Outcome of a fenced lifecycle transition (spec §14 resolution 2). */
public enum LifecycleOutcome {
    /** CAS succeeded; the transition is applied. */
    APPLIED,
    /** No package with the given id exists. */
    MISSING,
    /** approval_version moved after the reviewer read it — re-review required. */
    STALE_VERSION,
    /** The current status does not allow this transition. */
    INVALID_TRANSITION,
    /** The stored gate report is missing, red, or stale relative to the current version. */
    GATES_NOT_PASSED,
    /** The actor lacks platform-admin authority. */
    NOT_AUTHORIZED
}
