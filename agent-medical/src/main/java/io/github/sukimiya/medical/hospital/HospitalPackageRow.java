package io.github.sukimiya.medical.hospital;

import java.time.LocalDateTime;

/**
 * One row of hospital_capability_packages: the knowledge-layer record of a single hospital's
 * capability package (spec §4). The row carries identity, status gate, provenance, validity
 * window, and tenant/org scope; {@code content} is the package JSON validated against the
 * code-layer schema.
 */
public record HospitalPackageRow(
        String id,
        String hospitalCode,
        String hospitalName,
        String routingDescription,
        Status status,
        int schemaVersion,
        String content,
        String source,
        LocalDateTime validFrom,
        LocalDateTime validUntil,
        ScopeType scopeType,
        String tenantId,
        String tenantName,
        String orgId,
        String orgName,
        String createdBy,
        LocalDateTime createdAt) {

    public enum Status { PENDING, APPROVED, REVOKED }

    public enum ScopeType { GLOBAL, TENANT, ORG }
}
