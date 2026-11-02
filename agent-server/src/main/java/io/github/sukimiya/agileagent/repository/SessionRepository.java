package io.github.sukimiya.agileagent.repository;

import java.time.LocalDateTime;

import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Repository;

import io.github.sukimiya.agileagent.memory.MemoryScope;
import io.r2dbc.spi.Readable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Repository
public class SessionRepository {

    private static final String COLUMNS = "id, tenant_id, user_id, title, revision, created_at, updated_at, deleted_at";

    private final DatabaseClient db;

    public SessionRepository(DatabaseClient db) {
        this.db = db;
    }

    public Mono<SessionRow> findById(String id) {
        return db.sql("SELECT " + COLUMNS + " FROM sessions WHERE id = :id")
                .bind("id", id)
                .map(SessionRepository::mapRow)
                .one();
    }

    /** Like {@link #findById} but locks the row for the current transaction (serializes accept vs delete). */
    public Mono<SessionRow> findByIdForUpdate(String id) {
        return db.sql("SELECT " + COLUMNS + " FROM sessions WHERE id = :id FOR UPDATE")
                .bind("id", id)
                .map(SessionRepository::mapRow)
                .one();
    }

    public Mono<Void> insert(String id, String userId, String title) {
        var now = LocalDateTime.now();
        return db.sql("INSERT INTO sessions (id, user_id, title, created_at, updated_at) VALUES (:id, :userId, :title, :createdAt, :updatedAt)")
                .bind("id", id).bind("userId", userId).bind("title", title)
                .bind("createdAt", now).bind("updatedAt", now)
                .fetch().rowsUpdated().then();
    }

    public Mono<Void> touch(String id) {
        return db.sql("UPDATE sessions SET updated_at = :now WHERE id = :id")
                .bind("now", LocalDateTime.now()).bind("id", id)
                .fetch().rowsUpdated().then();
    }

    public Flux<SessionRow> findByUserId(String userId) {
        return db.sql("SELECT " + COLUMNS + " FROM sessions WHERE user_id = :userId ORDER BY updated_at DESC")
                .bind("userId", userId)
                .map(SessionRepository::mapRow)
                .all();
    }

    /** Live (not deleted) session owned by exactly this tenant and subject; legacy rows without tenant never match. */
    public Mono<SessionRow> findScoped(String id, MemoryScope scope) {
        return db.sql("SELECT " + COLUMNS + " FROM sessions WHERE id = :id AND tenant_id = :tenantId"
                        + " AND user_id = :subject AND is_valid = 1")
                .bind("id", id).bind("tenantId", scope.tenantId()).bind("subject", scope.subject())
                .map(SessionRepository::mapRow)
                .one();
    }

    /** Live sessions of exactly this tenant and subject, most recently updated first. */
    public Flux<SessionRow> findByScope(MemoryScope scope) {
        return db.sql("SELECT " + COLUMNS + " FROM sessions WHERE tenant_id = :tenantId AND user_id = :subject"
                        + " AND is_valid = 1 ORDER BY updated_at DESC")
                .bind("tenantId", scope.tenantId()).bind("subject", scope.subject())
                .map(SessionRepository::mapRow)
                .all();
    }

    /** Inserts a tenant-owned session at revision 0; fails with a duplicate-key error if the id exists. */
    public Mono<Void> insertScoped(String id, MemoryScope scope, String title) {
        var now = LocalDateTime.now();
        return db.sql("INSERT INTO sessions (id, tenant_id, user_id, title, revision, created_at, updated_at)"
                        + " VALUES (:id, :tenantId, :subject, :title, 0, :createdAt, :updatedAt)")
                .bind("id", id).bind("tenantId", scope.tenantId()).bind("subject", scope.subject())
                .bind("title", title).bind("createdAt", now).bind("updatedAt", now)
                .fetch().rowsUpdated().then();
    }

    private static SessionRow mapRow(Readable row) {
        var revision = row.get("revision", Long.class);
        return new SessionRow(
                row.get("id", String.class),
                row.get("tenant_id", String.class),
                row.get("user_id", String.class),
                row.get("title", String.class),
                revision == null ? 0 : revision,
                row.get("created_at", LocalDateTime.class),
                row.get("updated_at", LocalDateTime.class),
                row.get("deleted_at", LocalDateTime.class));
    }

    /**
     * Session row. {@code tenantId} is {@code null} only for legacy rows, which scoped queries never return;
     * {@code revision} versions cached history; {@code deletedAt} is the deletion tombstone.
     */
    public record SessionRow(String id, String tenantId, String userId, String title, long revision,
            LocalDateTime createdAt, LocalDateTime updatedAt, LocalDateTime deletedAt) {

        /** Live and owned by exactly this tenant and subject (legacy rows without a tenant never match). */
        public boolean ownedBy(io.github.sukimiya.agileagent.memory.MemoryScope scope) {
            return deletedAt == null && scope.tenantId().equals(tenantId) && scope.subject().equals(userId);
        }
    }
}
