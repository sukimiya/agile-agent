package io.github.sukimiya.agileagent.knowledge;

import java.time.Duration;
import java.time.LocalDateTime;

import io.r2dbc.spi.ConnectionFactory;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.r2dbc.connection.R2dbcTransactionManager;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.reactive.TransactionalOperator;

import io.r2dbc.spi.Readable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Repository
public class KnowledgeDocumentRepository {

    private final DatabaseClient db;
    private final TransactionalOperator transactions;

    public KnowledgeDocumentRepository(DatabaseClient db, ConnectionFactory connectionFactory) {
        this.db = db;
        this.transactions = TransactionalOperator.create(new R2dbcTransactionManager(connectionFactory));
    }

    public Mono<Void> insert(KnowledgeDocumentRow row) {
        var query = db.sql("""
                INSERT INTO knowledge_documents
                    (id, title, original_filename, temp_path, status, summary, uploader_user_id, uploaded_at,
                     scope_type, tenant_id, tenant_name, org_id, org_name)
                VALUES
                    (:id, :title, :originalFilename, :tempPath, :status, :summary, :uploaderUserId, :uploadedAt,
                     :scopeType, :tenantId, :tenantName, :orgId, :orgName)
                """)
                .bind("id", row.id())
                .bind("title", row.title())
                .bind("originalFilename", row.originalFilename())
                .bind("tempPath", row.tempPath())
                .bind("status", row.status().name())
                .bind("summary", row.summary() == null ? "" : row.summary())
                .bind("uploaderUserId", row.uploaderUserId())
                .bind("uploadedAt", row.uploadedAt())
                .bind("scopeType", row.scopeType().name());
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

    public Mono<KnowledgeDocumentRow> findById(String id) {
        return db.sql("""
                SELECT id, title, original_filename, temp_path, status, summary,
                       uploader_user_id, uploaded_at, reviewed_by_user_id, reviewed_at,
                       scope_type, tenant_id, tenant_name, org_id, org_name
                FROM knowledge_documents WHERE id = :id
                """)
                .bind("id", id)
                .map(KnowledgeDocumentRepository::mapRow)
                .one();
    }

    public Flux<KnowledgeDocumentRow> findByStatus(KnowledgeDocumentRow.Status status) {
        return db.sql("""
                SELECT id, title, original_filename, temp_path, status, summary,
                       uploader_user_id, uploaded_at, reviewed_by_user_id, reviewed_at,
                       scope_type, tenant_id, tenant_name, org_id, org_name
                FROM knowledge_documents WHERE status = :status ORDER BY uploaded_at DESC
                """)
                .bind("status", status.name())
                .map(KnowledgeDocumentRepository::mapRow)
                .all();
    }

    /** Current, durable filesystem pointer for catalog publication. */
    public Flux<ApprovedArtifactPointer> findApprovedArtifactPointers() {
        return db.sql("""
                SELECT id, approval_owner, approval_version, scope_type, tenant_id, org_id
                FROM knowledge_documents
                WHERE status = 'APPROVED' AND approval_owner IS NOT NULL
                ORDER BY uploaded_at DESC
                """)
                .map((row, metadata) -> new ApprovedArtifactPointer(
                        row.get("id", String.class), row.get("approval_owner", String.class),
                        row.get("approval_version", Long.class),
                        KnowledgeDocumentRow.ScopeType.valueOf(row.get("scope_type", String.class)),
                        row.get("tenant_id", String.class), row.get("org_id", String.class)))
                .all();
    }

    /**
     * Atomically claims a PENDING row and returns its new fencing generation, or zero if a
     * non-expired owner already holds it. The row lock and transaction keep the returned
     * generation paired with this owner's fresh database-time lease.
     */
    public Mono<Long> claimPendingApproval(String id, String owner, Duration claimLease) {
        var seconds = leaseSeconds(claimLease);
        var claim = db.sql("""
                UPDATE knowledge_documents
                SET approval_owner = :owner,
                    approval_claimed_at = CURRENT_TIMESTAMP,
                    approval_version = approval_version + 1
                WHERE id = :id AND status = 'PENDING'
                  AND (approval_owner IS NULL OR approval_claimed_at IS NULL OR
                       approval_claimed_at <= TIMESTAMPADD(SECOND, -:leaseSeconds, CURRENT_TIMESTAMP))
                """)
                .bind("owner", owner)
                .bind("id", id)
                .bind("leaseSeconds", seconds)
                .fetch().rowsUpdated()
                .flatMap(updated -> updated == 0
                        ? Mono.just(0L)
                        : db.sql("""
                                SELECT approval_version
                                FROM knowledge_documents
                                WHERE id = :id AND status = 'PENDING' AND approval_owner = :owner
                                """)
                                .bind("id", id)
                                .bind("owner", owner)
                                .map((row, metadata) -> row.get("approval_version", Long.class))
                                .one()
                                .defaultIfEmpty(0L));
        return transactions.transactional(claim);
    }

    /** Renew an exact live owner/generation. An expired claim cannot be revived. */
    public Mono<Long> renewPendingApprovalClaim(String id, String owner, long version, Duration claimLease) {
        return db.sql("""
                UPDATE knowledge_documents
                SET approval_claimed_at = CURRENT_TIMESTAMP
                WHERE id = :id AND status = 'PENDING'
                  AND approval_owner = :owner AND approval_version = :version
                  AND approval_claimed_at IS NOT NULL
                  AND approval_claimed_at > TIMESTAMPADD(SECOND, -:leaseSeconds, CURRENT_TIMESTAMP)
                """)
                .bind("id", id)
                .bind("owner", owner)
                .bind("version", version)
                .bind("leaseSeconds", leaseSeconds(claimLease))
                .fetch().rowsUpdated();
    }

    public Mono<Long> approveClaimedPending(
            String id, String owner, long version, Duration claimLease,
            String summary, String reviewedByUserId, LocalDateTime reviewedAt) {
        return db.sql("""
                UPDATE knowledge_documents
                SET status = 'APPROVED', summary = :summary,
                    reviewed_by_user_id = :reviewedBy, reviewed_at = :reviewedAt
                WHERE id = :id AND status = 'PENDING'
                  AND approval_owner = :owner AND approval_version = :version
                  AND approval_claimed_at IS NOT NULL
                  AND approval_claimed_at > TIMESTAMPADD(SECOND, -:leaseSeconds, CURRENT_TIMESTAMP)
                """)
                .bind("summary", summary == null ? "" : summary)
                .bind("reviewedBy", reviewedByUserId)
                .bind("reviewedAt", reviewedAt)
                .bind("owner", owner)
                .bind("version", version)
                .bind("id", id)
                .bind("leaseSeconds", leaseSeconds(claimLease))
                .fetch().rowsUpdated();
    }

    public Mono<KnowledgeDocumentRow.Status> statusForApprovalClaim(String id, String owner, long version) {
        return db.sql("""
                SELECT status FROM knowledge_documents
                WHERE id = :id AND approval_owner = :owner AND approval_version = :version
                """)
                .bind("id", id)
                .bind("owner", owner)
                .bind("version", version)
                .map((row, metadata) -> KnowledgeDocumentRow.Status.valueOf(row.get("status", String.class)))
                .one();
    }

    /** Restore PENDING only while this exact owner/generation still controls the row. */
    public Mono<Long> recoverPendingApprovalClaim(
            String id, String owner, long version, String originalSummary) {
        return db.sql("""
                UPDATE knowledge_documents
                SET status = 'PENDING',
                    summary = :summary,
                    reviewed_by_user_id = NULL, reviewed_at = NULL
                WHERE id = :id AND approval_owner = :owner AND approval_version = :version
                  AND status = 'PENDING'
                """)
                .bind("summary", originalSummary == null ? "" : originalSummary)
                .bind("id", id)
                .bind("owner", owner)
                .bind("version", version)
                .fetch().rowsUpdated();
    }

    public Mono<Long> releasePendingApprovalClaim(String id, String owner, long version) {
        return db.sql("""
                UPDATE knowledge_documents
                SET approval_owner = NULL, approval_claimed_at = NULL
                WHERE id = :id AND status = 'PENDING'
                  AND approval_owner = :owner AND approval_version = :version
                """)
                .bind("id", id)
                .bind("owner", owner)
                .bind("version", version)
                .fetch().rowsUpdated();
    }

    public Mono<Long> rejectClaimedPending(
            String id, String owner, long version, Duration claimLease,
            String reviewedByUserId, LocalDateTime reviewedAt) {
        return db.sql("""
                UPDATE knowledge_documents
                SET status = 'REJECTED', reviewed_by_user_id = :reviewedBy, reviewed_at = :reviewedAt
                WHERE id = :id AND status = 'PENDING'
                  AND approval_owner = :owner AND approval_version = :version
                  AND approval_claimed_at IS NOT NULL
                  AND approval_claimed_at > TIMESTAMPADD(SECOND, -:leaseSeconds, CURRENT_TIMESTAMP)
                """)
                .bind("reviewedBy", reviewedByUserId)
                .bind("reviewedAt", reviewedAt)
                .bind("owner", owner)
                .bind("version", version)
                .bind("id", id)
                .bind("leaseSeconds", leaseSeconds(claimLease))
                .fetch().rowsUpdated();
    }

    public Mono<Long> rejectIfPending(String id, String reviewedByUserId, LocalDateTime reviewedAt) {
        return db.sql("""
                UPDATE knowledge_documents
                SET status = 'REJECTED', reviewed_by_user_id = :reviewedBy, reviewed_at = :reviewedAt
                WHERE id = :id AND status = 'PENDING' AND approval_owner IS NULL
                """)
                .bind("reviewedBy", reviewedByUserId)
                .bind("reviewedAt", reviewedAt)
                .bind("id", id)
                .fetch().rowsUpdated();
    }

    private static int leaseSeconds(Duration lease) {
        return Math.toIntExact(lease.getSeconds() + (lease.getNano() > 0 ? 1 : 0));
    }

    private static KnowledgeDocumentRow mapRow(Readable row) {
        return new KnowledgeDocumentRow(
                row.get("id", String.class),
                row.get("title", String.class),
                row.get("original_filename", String.class),
                row.get("temp_path", String.class),
                KnowledgeDocumentRow.Status.valueOf(row.get("status", String.class)),
                row.get("summary", String.class),
                row.get("uploader_user_id", String.class),
                row.get("uploaded_at", LocalDateTime.class),
                row.get("reviewed_by_user_id", String.class),
                row.get("reviewed_at", LocalDateTime.class),
                KnowledgeDocumentRow.ScopeType.valueOf(row.get("scope_type", String.class)),
                row.get("tenant_id", String.class),
                row.get("tenant_name", String.class),
                row.get("org_id", String.class),
                row.get("org_name", String.class));
    }

    public record KnowledgeDocumentRow(
            String id, String title, String originalFilename, String tempPath,
            Status status, String summary, String uploaderUserId, LocalDateTime uploadedAt,
            String reviewedByUserId, LocalDateTime reviewedAt, ScopeType scopeType,
            String tenantId, String tenantName, String orgId, String orgName) {

        public KnowledgeDocumentRow(String id, String title, String originalFilename, String tempPath,
                Status status, String summary, String uploaderUserId, LocalDateTime uploadedAt,
                String reviewedByUserId, LocalDateTime reviewedAt) {
            this(id, title, originalFilename, tempPath, status, summary, uploaderUserId, uploadedAt,
                    reviewedByUserId, reviewedAt, ScopeType.GLOBAL, null, null, null, null);
        }

        public enum Status { PENDING, APPROVED, REJECTED }

        public enum ScopeType { GLOBAL, TENANT, ORG }
    }

    public record ApprovedArtifactPointer(String id, String owner, long version,
                                          KnowledgeDocumentRow.ScopeType scopeType,
                                          String tenantId, String orgId) {
        KnowledgeDocumentRow accessRow() {
            return new KnowledgeDocumentRow(id, null, null, null, KnowledgeDocumentRow.Status.APPROVED,
                    null, null, null, null, null, scopeType, tenantId, null, orgId, null);
        }
    }
}
