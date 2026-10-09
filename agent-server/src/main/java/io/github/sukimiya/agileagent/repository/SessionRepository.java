package io.github.sukimiya.agileagent.repository;

import java.time.LocalDateTime;

import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Repository;

import io.r2dbc.spi.Readable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Repository
public class SessionRepository {

    private final DatabaseClient db;

    public SessionRepository(DatabaseClient db) {
        this.db = db;
    }

    public Mono<SessionRow> findById(String id) {
        return db.sql("SELECT id, user_id, title, created_at, updated_at FROM sessions WHERE id = :id")
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
        return db.sql("SELECT id, user_id, title, created_at, updated_at FROM sessions WHERE user_id = :userId ORDER BY updated_at DESC")
                .bind("userId", userId)
                .map(SessionRepository::mapRow)
                .all();
    }

    private static SessionRow mapRow(Readable row) {
        return new SessionRow(
                row.get("id", String.class),
                row.get("user_id", String.class),
                row.get("title", String.class),
                row.get("created_at", LocalDateTime.class),
                row.get("updated_at", LocalDateTime.class));
    }

    public record SessionRow(String id, String userId, String title, LocalDateTime createdAt, LocalDateTime updatedAt) {}
}
