package io.github.sukimiya.agileagent.knowledge;

import java.time.LocalDateTime;

import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Repository;

import io.r2dbc.spi.Readable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Repository
public class KnowledgeDocumentRepository {

    private final DatabaseClient db;

    public KnowledgeDocumentRepository(DatabaseClient db) {
        this.db = db;
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

    public Mono<Long> claimPendingApproval(String id, String claimToken) {
        return db.sql("""
                UPDATE knowledge_documents
                SET approval_claim_token = :claimToken
                WHERE id = :id AND status = 'PENDING' AND approval_claim_token IS NULL
                """)
                .bind("claimToken", claimToken)
                .bind("id", id)
                .fetch().rowsUpdated();
    }

    public Mono<Long> approveClaimedPending(
            String id, String claimToken, String summary, String reviewedByUserId, LocalDateTime reviewedAt) {
        return db.sql("""
                UPDATE knowledge_documents
                SET status = 'APPROVED', summary = :summary,
                    reviewed_by_user_id = :reviewedBy, reviewed_at = :reviewedAt
                WHERE id = :id AND status = 'PENDING' AND approval_claim_token = :claimToken
                """)
                .bind("summary", summary == null ? "" : summary)
                .bind("reviewedBy", reviewedByUserId)
                .bind("reviewedAt", reviewedAt)
                .bind("claimToken", claimToken)
                .bind("id", id)
                .fetch().rowsUpdated();
    }

    /** Return an exact attempt's status without exposing the internal claim token on document rows. */
    public Mono<KnowledgeDocumentRow.Status> statusForApprovalClaim(String id, String claimToken) {
        return db.sql("""
                SELECT status FROM knowledge_documents
                WHERE id = :id AND approval_claim_token = :claimToken
                """)
                .bind("id", id)
                .bind("claimToken", claimToken)
                .map((row, metadata) -> KnowledgeDocumentRow.Status.valueOf(row.get("status", String.class)))
                .one();
    }

    /** Restore PENDING only while this exact attempt still owns the claim; keep the claim through file cleanup. */
    public Mono<Long> recoverPendingApprovalClaim(String id, String claimToken, String originalSummary) {
        return db.sql("""
                UPDATE knowledge_documents
                SET status = 'PENDING',
                    summary = :summary,
                    reviewed_by_user_id = NULL, reviewed_at = NULL
                WHERE id = :id AND approval_claim_token = :claimToken
                  AND status IN ('PENDING', 'APPROVED')
                """)
                .bind("summary", originalSummary == null ? "" : originalSummary)
                .bind("id", id)
                .bind("claimToken", claimToken)
                .fetch().rowsUpdated();
    }

    /** Release a compensated attempt after its library artifacts have been removed. */
    public Mono<Long> releasePendingApprovalClaim(String id, String claimToken) {
        return db.sql("""
                UPDATE knowledge_documents
                SET approval_claim_token = NULL
                WHERE id = :id AND status = 'PENDING' AND approval_claim_token = :claimToken
                """)
                .bind("id", id)
                .bind("claimToken", claimToken)
                .fetch().rowsUpdated();
    }

    public Mono<Long> rejectIfPending(String id, String reviewedByUserId, LocalDateTime reviewedAt) {
        return db.sql("""
                UPDATE knowledge_documents
                SET status = 'REJECTED', reviewed_by_user_id = :reviewedBy, reviewed_at = :reviewedAt
                WHERE id = :id AND status = 'PENDING' AND approval_claim_token IS NULL
                """)
                .bind("reviewedBy", reviewedByUserId)
                .bind("reviewedAt", reviewedAt)
                .bind("id", id)
                .fetch().rowsUpdated();
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
}
