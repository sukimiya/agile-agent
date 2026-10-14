package io.github.sukimiya.medical.hospital;

import java.time.LocalDateTime;

import org.springframework.r2dbc.core.DatabaseClient;

import io.r2dbc.spi.Readable;
import reactor.core.publisher.Mono;

/** R2DBC access to medical_human_tasks (V9). Registered as a @Bean, never scanned. */
public class MedicalHumanTaskRepository {

    private final DatabaseClient db;

    public MedicalHumanTaskRepository(DatabaseClient db) {
        this.db = db;
    }

    public Mono<Void> insert(MedicalHumanTaskRow row) {
        return db.sql("""
                INSERT INTO medical_human_tasks
                    (id, conversation_id, hospital_code, tool_name, arguments_json, status, created_by)
                VALUES
                    (:id, :conversationId, :hospitalCode, :toolName, :argumentsJson, :status, :createdBy)
                """)
                .bind("id", row.id())
                .bind("conversationId", row.conversationId())
                .bind("hospitalCode", row.hospitalCode())
                .bind("toolName", row.toolName())
                .bind("argumentsJson", row.argumentsJson())
                .bind("status", row.status().name())
                .bind("createdBy", row.createdBy())
                .fetch().rowsUpdated().then();
    }

    public Mono<MedicalHumanTaskRow> findById(String id) {
        return db.sql("""
                SELECT id, conversation_id, hospital_code, tool_name, arguments_json, status,
                       created_by, created_at
                FROM medical_human_tasks WHERE id = :id
                """)
                .bind("id", id)
                .map(MedicalHumanTaskRepository::mapRow)
                .one();
    }

    /**
     * Idempotent insert (spec §14 resolution 7): the partial unique index on submission_key
     * makes a conflicting retry insert nothing, and the existing row is returned instead, so
     * a double-tapped consultation stays ONE human task. A null submission key never
     * conflicts — the partial index excludes it.
     */
    public Mono<MedicalHumanTaskRow> insertOrFindExisting(MedicalHumanTaskRow row, String submissionKey) {
        var query = db.sql("""
                INSERT INTO medical_human_tasks
                    (id, conversation_id, hospital_code, tool_name, arguments_json, status,
                     created_by, submission_key)
                VALUES
                    (:id, :conversationId, :hospitalCode, :toolName, :argumentsJson, :status,
                     :createdBy, :submissionKey)
                ON CONFLICT (submission_key) WHERE submission_key IS NOT NULL DO NOTHING
                """)
                .bind("id", row.id())
                .bind("conversationId", row.conversationId())
                .bind("hospitalCode", row.hospitalCode())
                .bind("toolName", row.toolName())
                .bind("argumentsJson", row.argumentsJson())
                .bind("status", row.status().name())
                .bind("createdBy", row.createdBy());
        query = submissionKey == null
                ? query.bindNull("submissionKey", String.class)
                : query.bind("submissionKey", submissionKey);
        return query.fetch().rowsUpdated()
                .flatMap(updated -> updated > 0 ? findById(row.id()) : findBySubmissionKey(submissionKey));
    }

    public Mono<MedicalHumanTaskRow> findBySubmissionKey(String submissionKey) {
        return db.sql("SELECT id, conversation_id, hospital_code, tool_name, arguments_json, "
                        + "status, created_by, created_at FROM medical_human_tasks "
                        + "WHERE submission_key = :submissionKey")
                .bind("submissionKey", submissionKey)
                .map(MedicalHumanTaskRepository::mapRow)
                .one();
    }

    /** Simulation-gate support: count and clean up the probe's own task rows (Task 9). */
    public Mono<Long> countByConversationId(String conversationId) {
        return db.sql("SELECT COUNT(*) FROM medical_human_tasks WHERE conversation_id = :conversationId")
                .bind("conversationId", conversationId)
                .map(row -> row.get(0, Long.class))
                .one();
    }

    public Mono<Long> deleteByConversationId(String conversationId) {
        return db.sql("DELETE FROM medical_human_tasks WHERE conversation_id = :conversationId")
                .bind("conversationId", conversationId)
                .fetch().rowsUpdated();
    }

    private static MedicalHumanTaskRow mapRow(Readable row) {
        return new MedicalHumanTaskRow(
                row.get("id", String.class),
                row.get("conversation_id", String.class),
                row.get("hospital_code", String.class),
                row.get("tool_name", String.class),
                row.get("arguments_json", String.class),
                MedicalHumanTaskRow.TaskStatus.valueOf(row.get("status", String.class)),
                row.get("created_by", String.class),
                row.get("created_at", LocalDateTime.class));
    }
}
