package io.github.sukimiya.medical.hospital;

import java.time.LocalDateTime;

/**
 * V10 approval-state projection of a package row: everything the lifecycle service needs to
 * fence a transition (spec §14 resolution 2) without loading the full content payload.
 */
public record HospitalPackageApprovalState(
        String id,
        HospitalPackageRow.Status status,
        long approvalVersion,
        String reviewedBy,
        LocalDateTime reviewedAt,
        String rejectionReason,
        String lastGateReport) {
}
