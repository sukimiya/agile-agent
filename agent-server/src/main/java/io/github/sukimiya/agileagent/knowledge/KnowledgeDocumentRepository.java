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
        return db.sql("""
                INSERT INTO knowledge_documents
                    (id, title, original_filename, temp_path, status, summary, uploader_user_id, uploaded_at)
                VALUES
                    (:id, :title, :originalFilename, :tempPath, :status, :summary, :uploaderUserId, :uploadedAt)
                """)
                .bind("id", row.id())
                .bind("title", row.title())
                .bind("originalFilename", row.originalFilename())
                .bind("tempPath", row.tempPath())
                .bind("status", row.status().name())
                .bind("summary", row.summary() == null ? "" : row.summary())
                .bind("uploaderUserId", row.uploaderUserId())
                .bind("uploadedAt", row.uploadedAt())
                .fetch().rowsUpdated().then();
    }

    public Mono<KnowledgeDocumentRow> findById(String id) {
        return db.sql("""
                SELECT id, title, original_filename, temp_path, status, summary,
                       uploader_user_id, uploaded_at, reviewed_by_user_id, reviewed_at
                FROM knowledge_documents WHERE id = :id
                """)
                .bind("id", id)
                .map(KnowledgeDocumentRepository::mapRow)
                .one();
    }

    public Flux<KnowledgeDocumentRow> findByStatus(KnowledgeDocumentRow.Status status) {
        return db.sql("""
                SELECT id, title, original_filename, temp_path, status, summary,
                       uploader_user_id, uploaded_at, reviewed_by_user_id, reviewed_at
                FROM knowledge_documents WHERE status = :status ORDER BY uploaded_at DESC
                """)
                .bind("status", status.name())
                .map(KnowledgeDocumentRepository::mapRow)
                .all();
    }

    public Mono<Long> approveIfPending(String id, String summary, String reviewedByUserId, LocalDateTime reviewedAt) {
        return db.sql("""
                UPDATE knowledge_documents
                SET status = 'APPROVED', summary = :summary, reviewed_by_user_id = :reviewedBy, reviewed_at = :reviewedAt
                WHERE id = :id AND status = 'PENDING'
                """)
                .bind("summary", summary == null ? "" : summary)
                .bind("reviewedBy", reviewedByUserId)
                .bind("reviewedAt", reviewedAt)
                .bind("id", id)
                .fetch().rowsUpdated();
    }

    public Mono<Long> rejectIfPending(String id, String reviewedByUserId, LocalDateTime reviewedAt) {
        return db.sql("""
                UPDATE knowledge_documents
                SET status = 'REJECTED', reviewed_by_user_id = :reviewedBy, reviewed_at = :reviewedAt
                WHERE id = :id AND status = 'PENDING'
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
                row.get("reviewed_at", LocalDateTime.class));
    }

    public record KnowledgeDocumentRow(
            String id, String title, String originalFilename, String tempPath,
            Status status, String summary, String uploaderUserId, LocalDateTime uploadedAt,
            String reviewedByUserId, LocalDateTime reviewedAt) {

        public enum Status { PENDING, APPROVED, REJECTED }
    }
}
