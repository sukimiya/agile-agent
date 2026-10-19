package io.github.sukimiya.medical.rules;

import java.time.LocalDateTime;

import org.springframework.r2dbc.core.DatabaseClient;

import io.r2dbc.spi.Readable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/** R2DBC access to medical_task_events (V11). Registered as a @Bean, never scanned. */
public class MedicalTaskEventRepository {

    private final DatabaseClient db;

    public MedicalTaskEventRepository(DatabaseClient db) {
        this.db = db;
    }

    public Mono<Void> insert(MedicalTaskEventRow row) {
        var query = db.sql("""
                INSERT INTO medical_task_events (id, task_id, event_type, level, actor, detail)
                VALUES (:id, :taskId, :eventType, :level, :actor, :detail)
                """)
                .bind("id", row.id())
                .bind("taskId", row.taskId())
                .bind("eventType", row.eventType().name())
                .bind("level", row.level());
        query = row.actor() == null
                ? query.bindNull("actor", String.class) : query.bind("actor", row.actor());
        query = row.detail() == null
                ? query.bindNull("detail", String.class) : query.bind("detail", row.detail());
        return query.fetch().rowsUpdated().then();
    }

    public Flux<MedicalTaskEventRow> findByTaskId(String taskId) {
        return db.sql("SELECT id, task_id, event_type, level, actor, detail, created_at "
                        + "FROM medical_task_events WHERE task_id = :taskId "
                        + "ORDER BY created_at, id")
                .bind("taskId", taskId)
                .map(MedicalTaskEventRepository::mapRow)
                .all();
    }

    /**
     * Exact (task, type, detail) existence check — the ceiling/stale idempotency guard.
     * {@code detail} must be non-null: SQL equality never matches a NULL detail, so a null
     * argument is rejected at bind time rather than silently returning false.
     */
    public Mono<Boolean> hasEvent(String taskId, MedicalTaskEventRow.EventType eventType,
            String detail) {
        return db.sql("SELECT EXISTS(SELECT 1 FROM medical_task_events WHERE task_id = :taskId "
                        + "AND event_type = :eventType AND detail = :detail)")
                .bind("taskId", taskId)
                .bind("eventType", eventType.name())
                .bind("detail", detail)
                .map(row -> row.get(0, Boolean.class))
                .one();
    }

    private static MedicalTaskEventRow mapRow(Readable row) {
        return new MedicalTaskEventRow(
                row.get("id", String.class),
                row.get("task_id", String.class),
                MedicalTaskEventRow.EventType.valueOf(row.get("event_type", String.class)),
                row.get("level", Integer.class),
                row.get("actor", String.class),
                row.get("detail", String.class),
                row.get("created_at", LocalDateTime.class));
    }
}
