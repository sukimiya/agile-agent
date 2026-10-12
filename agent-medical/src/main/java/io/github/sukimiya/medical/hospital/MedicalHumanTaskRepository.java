package io.github.sukimiya.medical.hospital;

import java.time.LocalDateTime;

import org.springframework.r2dbc.core.DatabaseClient;

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
                .map((row, metadata) -> new MedicalHumanTaskRow(
                        row.get("id", String.class),
                        row.get("conversation_id", String.class),
                        row.get("hospital_code", String.class),
                        row.get("tool_name", String.class),
                        row.get("arguments_json", String.class),
                        MedicalHumanTaskRow.TaskStatus.valueOf(row.get("status", String.class)),
                        row.get("created_by", String.class),
                        row.get("created_at", LocalDateTime.class)))
                .one();
    }
}
