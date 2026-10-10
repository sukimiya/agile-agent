package io.github.sukimiya.agileagent.hint;

import java.time.LocalDateTime;

import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Repository;

import io.r2dbc.spi.Readable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Repository
public class HintRepository {

    private final DatabaseClient db;

    public HintRepository(DatabaseClient db) {
        this.db = db;
    }

    public Mono<Void> insert(HintRow row) {
        return db.sql("""
                INSERT INTO chat_hints (id, content, created_by_subject, created_at)
                VALUES (:id, :content, :createdBySubject, :createdAt)
                """)
                .bind("id", row.id())
                .bind("content", row.content())
                .bind("createdBySubject", row.createdBySubject())
                .bind("createdAt", row.createdAt())
                .fetch().rowsUpdated().then();
    }

    public Flux<HintRow> findAll() {
        return db.sql("""
                SELECT id, content, created_by_subject, created_at
                FROM chat_hints ORDER BY created_at, id
                """)
                .map(HintRepository::mapRow)
                .all();
    }

    public Mono<HintRow> findById(String id) {
        return db.sql("""
                SELECT id, content, created_by_subject, created_at
                FROM chat_hints WHERE id = :id
                """)
                .bind("id", id)
                .map(HintRepository::mapRow)
                .one();
    }

    public Mono<Long> update(String id, String content) {
        return db.sql("UPDATE chat_hints SET content = :content WHERE id = :id")
                .bind("content", content)
                .bind("id", id)
                .fetch().rowsUpdated();
    }

    public Mono<Long> deleteById(String id) {
        return db.sql("DELETE FROM chat_hints WHERE id = :id")
                .bind("id", id)
                .fetch().rowsUpdated();
    }

    private static HintRow mapRow(Readable row) {
        return new HintRow(
                row.get("id", String.class),
                row.get("content", String.class),
                row.get("created_by_subject", String.class),
                row.get("created_at", LocalDateTime.class));
    }

    public record HintRow(String id, String content, String createdBySubject, LocalDateTime createdAt) {
    }
}
