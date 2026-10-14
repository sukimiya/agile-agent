package io.github.sukimiya.medical.lifecycle;

import io.github.sukimiya.medical.hospital.HospitalPackageApprovalState;

/** Submission outcome with the persisted approval state (null when NOT_AUTHORIZED). */
public record SubmissionResult(SubmissionStatus status, HospitalPackageApprovalState state) {
}
