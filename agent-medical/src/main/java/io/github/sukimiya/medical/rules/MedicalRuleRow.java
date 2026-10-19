package io.github.sukimiya.medical.rules;

import java.time.LocalDateTime;

/**
 * One row of medical_rules (V11): a versioned, fenced knowledge submission mirroring
 * HospitalPackageRow. scope_key is a generated column (Ruling R1) and never part of the
 * record — it is derived from scopeType/tenantId/hospitalCode by PostgreSQL.
 */
public record MedicalRuleRow(
        String id, String ruleCode, String ruleKind, ScopeType scopeType,
        String tenantId, String tenantName, String orgId, String orgName, String hospitalCode,
        String contentJson, String source, LocalDateTime validFrom, LocalDateTime validUntil,
        Status status, String createdBy, LocalDateTime createdAt) {

    public enum Status { PENDING, APPROVED, REJECTED, REVOKED }

    public enum ScopeType { GLOBAL, TENANT, HOSPITAL }
}
