package io.github.sukimiya.medical.hospital;

import java.time.LocalDateTime;

import org.springframework.r2dbc.core.DatabaseClient;

import io.r2dbc.spi.Readable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * R2DBC access to hospital_capability_packages (V8). Mirrors the knowledge repository's
 * style: text-block SQL, bindNull for nullable scope columns, Readable-based mapping.
 * Registered as a @Bean by MedicalAutoConfiguration — not component-scanned.
 */
public class HospitalPackageRepository {

    private static final String COLUMNS = """
            id, hospital_code, hospital_name, routing_description, status, schema_version,
            content, source, valid_from, valid_until, scope_type, tenant_id, tenant_name,
            org_id, org_name, created_by, created_at
            """;

    private final DatabaseClient db;

    public HospitalPackageRepository(DatabaseClient db) {
        this.db = db;
    }

    public Mono<Void> insert(HospitalPackageRow row) {
        var query = db.sql("""
                INSERT INTO hospital_capability_packages
                    (id, hospital_code, hospital_name, routing_description, status, schema_version,
                     content, source, valid_from, valid_until, scope_type, tenant_id, tenant_name,
                     org_id, org_name, created_by)
                VALUES
                    (:id, :hospitalCode, :hospitalName, :routingDescription, :status, :schemaVersion,
                     :content, :source, :validFrom, :validUntil, :scopeType, :tenantId, :tenantName,
                     :orgId, :orgName, :createdBy)
                """)
                .bind("id", row.id())
                .bind("hospitalCode", row.hospitalCode())
                .bind("hospitalName", row.hospitalName())
                .bind("routingDescription", row.routingDescription())
                .bind("status", row.status().name())
                .bind("schemaVersion", row.schemaVersion())
                .bind("content", row.content())
                .bind("source", row.source())
                .bind("validFrom", row.validFrom())
                .bind("validUntil", row.validUntil())
                .bind("scopeType", row.scopeType().name())
                .bind("createdBy", row.createdBy());
        query = bindNullable(query, "tenantId", row.tenantId());
        query = bindNullable(query, "tenantName", row.tenantName());
        query = bindNullable(query, "orgId", row.orgId());
        query = bindNullable(query, "orgName", row.orgName());
        return query.fetch().rowsUpdated().then();
    }

    private static DatabaseClient.GenericExecuteSpec bindNullable(
            DatabaseClient.GenericExecuteSpec query, String name, String value) {
        return value == null ? query.bindNull(name, String.class) : query.bind(name, value);
    }

    public Mono<HospitalPackageRow> findById(String id) {
        return db.sql("SELECT " + COLUMNS + " FROM hospital_capability_packages WHERE id = :id")
                .bind("id", id)
                .map(HospitalPackageRepository::mapRow)
                .one();
    }

    public Mono<HospitalPackageRow> findByHospitalCode(String hospitalCode) {
        return db.sql("SELECT " + COLUMNS
                        + " FROM hospital_capability_packages WHERE hospital_code = :hospitalCode")
                .bind("hospitalCode", hospitalCode)
                .map(HospitalPackageRepository::mapRow)
                .one();
    }

    public Flux<HospitalPackageRow> findApproved() {
        return db.sql("SELECT " + COLUMNS + " FROM hospital_capability_packages "
                        + "WHERE status = 'APPROVED' ORDER BY hospital_code")
                .map(HospitalPackageRepository::mapRow)
                .all();
    }

    /**
     * Legacy unfenced status write kept for existing tests and tooling. Every lifecycle
     * transition must go through {@link #compareAndSetStatus}; do not add new callers.
     */
    public Mono<Long> updateStatus(String id, HospitalPackageRow.Status status) {
        return db.sql("UPDATE hospital_capability_packages SET status = :status WHERE id = :id")
                .bind("status", status.name())
                .bind("id", id)
                .fetch().rowsUpdated();
    }

    /** Approval-state projection over the V10 columns (spec §14 resolution 2). */
    public Mono<HospitalPackageApprovalState> findApprovalState(String id) {
        return db.sql("SELECT id, status, approval_version, reviewed_by, reviewed_at, "
                        + "rejection_reason, last_gate_report FROM hospital_capability_packages WHERE id = :id")
                .bind("id", id)
                .map(HospitalPackageRepository::mapApprovalState)
                .one();
    }

    /**
     * Single-snapshot read for review: row and approval state come from ONE SELECT on one
     * connection, so a review can never pair old content with an approval_version bumped by
     * a concurrent replacement submit in between (spec §14 resolution 2).
     */
    public Mono<ReviewSnapshot> findForReview(String id) {
        return db.sql("SELECT " + COLUMNS + ", approval_version, reviewed_by, reviewed_at, "
                        + "rejection_reason, last_gate_report "
                        + "FROM hospital_capability_packages WHERE id = :id")
                .bind("id", id)
                .map((row, metadata) -> new ReviewSnapshot(mapRow(row), mapApprovalState(row)))
                .one();
    }

    /** Row and approval state captured from the same database snapshot. */
    public record ReviewSnapshot(HospitalPackageRow row, HospitalPackageApprovalState state) {}

    /**
     * Replacement-semantics submission: a conflict on hospital_code replaces the whole row,
     * forces PENDING, clears every audit column, and bumps the version fence so a stale
     * approval can never land on the new content (TOCTOU guard). The existing row keeps its id.
     */
    public Mono<HospitalPackageApprovalState> upsertPending(HospitalPackageRow draft) {
        var query = db.sql("""
                INSERT INTO hospital_capability_packages
                    (id, hospital_code, hospital_name, routing_description, status, schema_version,
                     content, source, valid_from, valid_until, scope_type, tenant_id, tenant_name,
                     org_id, org_name, created_by)
                VALUES
                    (:id, :hospitalCode, :hospitalName, :routingDescription, 'PENDING', :schemaVersion,
                     :content, :source, :validFrom, :validUntil, :scopeType, :tenantId, :tenantName,
                     :orgId, :orgName, :createdBy)
                ON CONFLICT (hospital_code) DO UPDATE SET
                    hospital_name = EXCLUDED.hospital_name,
                    routing_description = EXCLUDED.routing_description,
                    status = 'PENDING',
                    schema_version = EXCLUDED.schema_version,
                    content = EXCLUDED.content,
                    source = EXCLUDED.source,
                    valid_from = EXCLUDED.valid_from,
                    valid_until = EXCLUDED.valid_until,
                    scope_type = EXCLUDED.scope_type,
                    tenant_id = EXCLUDED.tenant_id,
                    tenant_name = EXCLUDED.tenant_name,
                    org_id = EXCLUDED.org_id,
                    org_name = EXCLUDED.org_name,
                    created_by = EXCLUDED.created_by,
                    reviewed_by = NULL,
                    reviewed_at = NULL,
                    rejection_reason = NULL,
                    last_gate_report = NULL,
                    approval_version = hospital_capability_packages.approval_version + 1
                RETURNING id, status, approval_version, reviewed_by, reviewed_at,
                          rejection_reason, last_gate_report
                """)
                .bind("id", draft.id())
                .bind("hospitalCode", draft.hospitalCode())
                .bind("hospitalName", draft.hospitalName())
                .bind("routingDescription", draft.routingDescription())
                .bind("schemaVersion", draft.schemaVersion())
                .bind("content", draft.content())
                .bind("source", draft.source())
                .bind("validFrom", draft.validFrom())
                .bind("validUntil", draft.validUntil())
                .bind("scopeType", draft.scopeType().name())
                .bind("createdBy", draft.createdBy());
        query = bindNullable(query, "tenantId", draft.tenantId());
        query = bindNullable(query, "tenantName", draft.tenantName());
        query = bindNullable(query, "orgId", draft.orgId());
        query = bindNullable(query, "orgName", draft.orgName());
        return query.map(HospitalPackageRepository::mapApprovalState).one();
    }

    /** Persists the six-gate report without touching the fence: a review is not a transition. */
    public Mono<Void> saveGateReport(String id, String reportJson) {
        return db.sql("UPDATE hospital_capability_packages SET last_gate_report = :report WHERE id = :id")
                .bind("report", reportJson)
                .bind("id", id)
                .fetch().rowsUpdated().then();
    }

    /**
     * The only sanctioned status transition (spec §14 resolution 2): applies only while the row
     * is still at {@code expectedVersion}, bumps the fence, and stamps the reviewer. False means
     * the race was lost — callers surface STALE_VERSION and never retry blindly.
     */
    public Mono<Boolean> compareAndSetStatus(String id, long expectedVersion,
            HospitalPackageRow.Status newStatus, String reviewedBy, String rejectionReason) {
        var query = db.sql("""
                UPDATE hospital_capability_packages
                SET status = :status,
                    approval_version = approval_version + 1,
                    reviewed_by = :reviewedBy,
                    reviewed_at = CURRENT_TIMESTAMP,
                    rejection_reason = :rejectionReason
                WHERE id = :id AND approval_version = :expectedVersion
                """)
                .bind("status", newStatus.name())
                .bind("reviewedBy", reviewedBy)
                .bind("id", id)
                .bind("expectedVersion", expectedVersion);
        query = bindNullable(query, "rejectionReason", rejectionReason);
        return query.fetch().rowsUpdated().map(updated -> updated == 1);
    }

    private static HospitalPackageApprovalState mapApprovalState(Readable row) {
        return new HospitalPackageApprovalState(
                row.get("id", String.class),
                HospitalPackageRow.Status.valueOf(row.get("status", String.class)),
                row.get("approval_version", Long.class),
                row.get("reviewed_by", String.class),
                row.get("reviewed_at", LocalDateTime.class),
                row.get("rejection_reason", String.class),
                row.get("last_gate_report", String.class));
    }

    private static HospitalPackageRow mapRow(Readable row) {
        return new HospitalPackageRow(
                row.get("id", String.class),
                row.get("hospital_code", String.class),
                row.get("hospital_name", String.class),
                row.get("routing_description", String.class),
                HospitalPackageRow.Status.valueOf(row.get("status", String.class)),
                row.get("schema_version", Integer.class),
                row.get("content", String.class),
                row.get("source", String.class),
                row.get("valid_from", LocalDateTime.class),
                row.get("valid_until", LocalDateTime.class),
                HospitalPackageRow.ScopeType.valueOf(row.get("scope_type", String.class)),
                row.get("tenant_id", String.class),
                row.get("tenant_name", String.class),
                row.get("org_id", String.class),
                row.get("org_name", String.class),
                row.get("created_by", String.class),
                row.get("created_at", LocalDateTime.class));
    }
}
