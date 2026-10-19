package io.github.sukimiya.medical.rules;

import io.github.sukimiya.medical.lifecycle.SubmissionStatus;

/** Submission outcome with the persisted approval state (null when NOT_AUTHORIZED). */
public record RuleSubmissionResult(SubmissionStatus status, MedicalRuleApprovalState state) {
}
