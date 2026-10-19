package io.github.sukimiya.medical.rules;

import java.time.LocalDateTime;

/**
 * Approval-state projection of a rule row: everything the lifecycle service needs to fence
 * a transition, mirroring HospitalPackageApprovalState (main spec §14 resolution 2).
 */
public record MedicalRuleApprovalState(
        String id,
        MedicalRuleRow.Status status,
        long approvalVersion,
        String reviewedBy,
        LocalDateTime reviewedAt,
        String rejectionReason,
        String lastValidationReport) {
}
