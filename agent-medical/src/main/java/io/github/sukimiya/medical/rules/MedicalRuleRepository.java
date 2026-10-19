package io.github.sukimiya.medical.rules;

import java.time.LocalDateTime;

import org.springframework.r2dbc.core.DatabaseClient;

import io.r2dbc.spi.Readable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * R2DBC access to medical_rules (V11). Mirrors HospitalPackageRepository: text-block SQL,
 * bindNull for nullable columns, Readable-based mapping, replacement-semantics upsert and
 * approval_version CAS fencing. Registered as a @Bean by MedicalAutoConfiguration — never
 * component-scanned.
 */
public class MedicalRuleRepository {

    private static final String COLUMNS = """
            id, rule_code, rule_kind, scope_type, tenant_id, tenant_name, org_id, org_name,
            hospital_code, content_json, source, valid_from, valid_until, status,
            created_by, created_at
            """;

    private static final String STATE_COLUMNS = """
            id, status, approval_version, reviewed_by, reviewed_at, rejection_reason,
            last_validation_report
            """;

    private final DatabaseClient db;

    public MedicalRuleRepository(DatabaseClient db) {
        this.db = db;
    }

    public Mono<MedicalRuleRow> findById(String id) {
        return db.sql("SELECT " + COLUMNS + " FROM medical_rules WHERE id = :id")
                .bind("id", id)
                .map(MedicalRuleRepository::mapRow)
                .one();
    }

    public Mono<MedicalRuleApprovalState> findApprovalState(String id) {
        return db.sql("SELECT " + STATE_COLUMNS + " FROM medical_rules WHERE id = :id")
                .bind("id", id)
                .map(MedicalRuleRepository::mapApprovalState)
                .one();
    }

    /**
     * Single-snapshot read for review: row and approval state come from ONE SELECT, so a
     * review can never pair old content with a version bumped by a concurrent replacement
     * submit in between (same TOCTOU guard as the package repository).
     */
    public Mono<RuleReviewSnapshot> findForReview(String id) {
        return db.sql("SELECT " + COLUMNS + ", approval_version, reviewed_by, reviewed_at, "
                        + "rejection_reason, last_validation_report "
                        + "FROM medical_rules WHERE id = :id")
                .bind("id", id)
                .map((row, metadata) -> new RuleReviewSnapshot(mapRow(row), mapApprovalState(row)))
                .one();
    }

    /** Row and approval state captured from the same database snapshot. */
    public record RuleReviewSnapshot(MedicalRuleRow row, MedicalRuleApprovalState state) {}

    /**
     * Replacement-semantics submission keyed on (rule_code, scope_key) — Ruling R1. A
     * conflict replaces the whole row, forces PENDING, clears every audit column, and bumps
     * the version fence so a stale approval can never land on new content. Scope-defining
     * columns (scope_type, tenant_id, hospital_code) are part of the conflict target and are
     * never rewritten. The existing row keeps its id.
     */
    public Mono<MedicalRuleApprovalState> upsertPending(MedicalRuleRow draft) {
        var query = db.sql("""
                INSERT INTO medical_rules
                    (id, rule_code, rule_kind, scope_type, tenant_id, tenant_name, org_id,
                     org_name, hospital_code, content_json, source, valid_from, valid_until,
                     status, created_by)
                VALUES
                    (:id, :ruleCode, :ruleKind, :scopeType, :tenantId, :tenantName, :orgId,
                     :orgName, :hospitalCode, :contentJson, :source, :validFrom, :validUntil,
                     'PENDING', :createdBy)
                ON CONFLICT (rule_code, scope_key) DO UPDATE SET
                    rule_kind = EXCLUDED.rule_kind,
                    org_id = EXCLUDED.org_id,
                    org_name = EXCLUDED.org_name,
                    content_json = EXCLUDED.content_json,
                    source = EXCLUDED.source,
                    valid_from = EXCLUDED.valid_from,
                    valid_until = EXCLUDED.valid_until,
                    status = 'PENDING',
                    created_by = EXCLUDED.created_by,
                    reviewed_by = NULL,
                    reviewed_at = NULL,
                    rejection_reason = NULL,
                    last_validation_report = NULL,
                    approval_version = medical_rules.approval_version + 1
                RETURNING id, status, approval_version, reviewed_by, reviewed_at,
                          rejection_reason, last_validation_report
                """)
                .bind("id", draft.id())
                .bind("ruleCode", draft.ruleCode())
                .bind("ruleKind", draft.ruleKind())
                .bind("scopeType", draft.scopeType().name())
                .bind("contentJson", draft.contentJson())
                .bind("source", draft.source())
                .bind("validFrom", draft.validFrom())
                .bind("validUntil", draft.validUntil())
                .bind("createdBy", draft.createdBy());
        query = bindNullable(query, "tenantId", draft.tenantId());
        query = bindNullable(query, "tenantName", draft.tenantName());
        query = bindNullable(query, "orgId", draft.orgId());
        query = bindNullable(query, "orgName", draft.orgName());
        query = bindNullable(query, "hospitalCode", draft.hospitalCode());
        return query.map(MedicalRuleRepository::mapApprovalState).one();
    }

    /** Persists the validation report without touching the fence: a review is not a transition. */
    public Mono<Void> saveValidationReport(String id, String reportJson) {
        return db.sql("UPDATE medical_rules SET last_validation_report = :report WHERE id = :id")
                .bind("report", reportJson)
                .bind("id", id)
                .fetch().rowsUpdated().then();
    }

    /**
     * The only sanctioned status transition: applies only while the row is still at
     * {@code expectedVersion}, bumps the fence, and stamps the reviewer. False means the race
     * was lost — callers surface STALE_VERSION and never retry blindly.
     */
    public Mono<Boolean> compareAndSetStatus(String id, long expectedVersion,
            MedicalRuleRow.Status newStatus, String reviewedBy, String rejectionReason) {
        var query = db.sql("""
                UPDATE medical_rules
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

    /** Every APPROVED rule — engine and safety store filter by kind/window/visibility. */
    public Flux<MedicalRuleRow> findApproved() {
        return db.sql("SELECT " + COLUMNS + " FROM medical_rules "
                        + "WHERE status = 'APPROVED' ORDER BY rule_code, scope_type")
                .map(MedicalRuleRepository::mapRow)
                .all();
    }

    private static DatabaseClient.GenericExecuteSpec bindNullable(
            DatabaseClient.GenericExecuteSpec query, String name, String value) {
        return value == null ? query.bindNull(name, String.class) : query.bind(name, value);
    }

    private static MedicalRuleApprovalState mapApprovalState(Readable row) {
        return new MedicalRuleApprovalState(
                row.get("id", String.class),
                MedicalRuleRow.Status.valueOf(row.get("status", String.class)),
                row.get("approval_version", Long.class),
                row.get("reviewed_by", String.class),
                row.get("reviewed_at", LocalDateTime.class),
                row.get("rejection_reason", String.class),
                row.get("last_validation_report", String.class));
    }

    private static MedicalRuleRow mapRow(Readable row) {
        return new MedicalRuleRow(
                row.get("id", String.class),
                row.get("rule_code", String.class),
                row.get("rule_kind", String.class),
                MedicalRuleRow.ScopeType.valueOf(row.get("scope_type", String.class)),
                row.get("tenant_id", String.class),
                row.get("tenant_name", String.class),
                row.get("org_id", String.class),
                row.get("org_name", String.class),
                row.get("hospital_code", String.class),
                row.get("content_json", String.class),
                row.get("source", String.class),
                row.get("valid_from", LocalDateTime.class),
                row.get("valid_until", LocalDateTime.class),
                MedicalRuleRow.Status.valueOf(row.get("status", String.class)),
                row.get("created_by", String.class),
                row.get("created_at", LocalDateTime.class));
    }
}
