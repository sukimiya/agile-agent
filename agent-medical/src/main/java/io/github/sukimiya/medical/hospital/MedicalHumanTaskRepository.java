package io.github.sukimiya.medical.hospital;

import java.time.LocalDateTime;

import org.springframework.r2dbc.core.DatabaseClient;

import io.r2dbc.spi.Readable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/** R2DBC access to medical_human_tasks (V9). Registered as a @Bean, never scanned. */
public class MedicalHumanTaskRepository {

    private static final String COLUMNS = """
            id, conversation_id, hospital_code, tool_name, arguments_json, status,
            created_by, created_at, task_kind, priority, sla_deadline, escalation_level,
            claimed_by, claimed_at, completed_at, resolution_note
            """;

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
        return db.sql("SELECT " + COLUMNS + " FROM medical_human_tasks WHERE id = :id")
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
        return db.sql("SELECT " + COLUMNS + " FROM medical_human_tasks "
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

    /**
     * Idempotent E-call insert (Plan 4 spec §7.4): the partial unique index
     * ux_medical_human_tasks_open_ecall admits at most one open E-call per conversation.
     * A conflicting insert does nothing and the existing open row is returned instead, so
     * repeat triage never double-opens (Review Focus #3).
     */
    public Mono<MedicalHumanTaskRow> insertEcallOrFindExisting(MedicalHumanTaskRow row) {
        return db.sql("""
                INSERT INTO medical_human_tasks
                    (id, conversation_id, hospital_code, tool_name, arguments_json, status,
                     created_by, task_kind, priority, sla_deadline, escalation_level)
                VALUES
                    (:id, :conversationId, :hospitalCode, :toolName, :argumentsJson, :status,
                     :createdBy, :taskKind, :priority, :slaDeadline, :escalationLevel)
                ON CONFLICT (conversation_id)
                    WHERE task_kind = 'ECALL' AND status <> 'COMPLETED' DO NOTHING
                """)
                .bind("id", row.id())
                .bind("conversationId", row.conversationId())
                .bind("hospitalCode", row.hospitalCode())
                .bind("toolName", row.toolName())
                .bind("argumentsJson", row.argumentsJson())
                .bind("status", row.status().name())
                .bind("createdBy", row.createdBy())
                .bind("taskKind", row.taskKind().name())
                .bind("priority", row.priority())
                .bind("slaDeadline", row.slaDeadline())
                .bind("escalationLevel", row.escalationLevel())
                .fetch().rowsUpdated()
                .flatMap(updated -> updated > 0
                        ? findById(row.id())
                        : findOpenEcallByConversationId(row.conversationId()));
    }

    /** The conversation's open E-call, if any — the guard's single source of derived state. */
    public Mono<MedicalHumanTaskRow> findOpenEcallByConversationId(String conversationId) {
        return db.sql("SELECT " + COLUMNS + " FROM medical_human_tasks "
                        + "WHERE conversation_id = :conversationId AND task_kind = 'ECALL' "
                        + "AND status <> 'COMPLETED'")
                .bind("conversationId", conversationId)
                .map(MedicalHumanTaskRepository::mapRow)
                .one();
    }

    /** PENDING E-calls whose SLA deadline has passed — the escalation scan input. */
    public Flux<MedicalHumanTaskRow> findOverdueOpenEcalls(LocalDateTime now) {
        return db.sql("SELECT " + COLUMNS + " FROM medical_human_tasks "
                        + "WHERE task_kind = 'ECALL' AND status = 'PENDING' "
                        + "AND sla_deadline IS NOT NULL AND sla_deadline < :now "
                        + "ORDER BY sla_deadline")
                .bind("now", now)
                .map(MedicalHumanTaskRepository::mapRow)
                .all();
    }

    /** CLAIMED tasks claimed before the cutoff — the stale-claim recovery scan input. */
    public Flux<MedicalHumanTaskRow> findClaimedBefore(LocalDateTime cutoff) {
        return db.sql("SELECT " + COLUMNS + " FROM medical_human_tasks "
                        + "WHERE status = 'CLAIMED' AND claimed_at IS NOT NULL "
                        + "AND claimed_at < :cutoff ORDER BY claimed_at")
                .bind("cutoff", cutoff)
                .map(MedicalHumanTaskRepository::mapRow)
                .all();
    }

    /** Atomic takeover (Plan 4 spec §8): exactly one concurrent caller can win. */
    public Mono<Boolean> claim(String taskId, String claimedBy, LocalDateTime claimedAt) {
        return db.sql("""
                UPDATE medical_human_tasks
                SET status = 'CLAIMED', claimed_by = :claimedBy, claimed_at = :claimedAt
                WHERE id = :id AND status = 'PENDING' AND claimed_by IS NULL
                """)
                .bind("claimedBy", claimedBy)
                .bind("claimedAt", claimedAt)
                .bind("id", taskId)
                .fetch().rowsUpdated().map(updated -> updated == 1);
    }

    /** NORMAL task completion: PENDING → COMPLETED (hospital-confirmation desk entry). */
    public Mono<Boolean> completeFromPending(String taskId, String resolutionNote,
            LocalDateTime completedAt) {
        return db.sql("""
                UPDATE medical_human_tasks
                SET status = 'COMPLETED', completed_at = :completedAt,
                    resolution_note = :resolutionNote
                WHERE id = :id AND status = 'PENDING'
                """)
                .bind("completedAt", completedAt)
                .bind("resolutionNote", resolutionNote)
                .bind("id", taskId)
                .fetch().rowsUpdated().map(updated -> updated == 1);
    }

    /** Owner completion: CLAIMED → COMPLETED only for the exact claimer. */
    public Mono<Boolean> completeClaimedByOwner(String taskId, String claimedBy,
            String resolutionNote, LocalDateTime completedAt) {
        return db.sql("""
                UPDATE medical_human_tasks
                SET status = 'COMPLETED', completed_at = :completedAt,
                    resolution_note = :resolutionNote
                WHERE id = :id AND status = 'CLAIMED' AND claimed_by = :claimedBy
                """)
                .bind("completedAt", completedAt)
                .bind("resolutionNote", resolutionNote)
                .bind("claimedBy", claimedBy)
                .bind("id", taskId)
                .fetch().rowsUpdated().map(updated -> updated == 1);
    }

    /** Platform-admin override completion: any CLAIMED task regardless of claimer. */
    public Mono<Boolean> completeClaimedAny(String taskId, String resolutionNote,
            LocalDateTime completedAt) {
        return db.sql("""
                UPDATE medical_human_tasks
                SET status = 'COMPLETED', completed_at = :completedAt,
                    resolution_note = :resolutionNote
                WHERE id = :id AND status = 'CLAIMED'
                """)
                .bind("completedAt", completedAt)
                .bind("resolutionNote", resolutionNote)
                .bind("id", taskId)
                .fetch().rowsUpdated().map(updated -> updated == 1);
    }

    /** Fenced SLA escalation: applies only at the exact expected level (idempotent). */
    public Mono<Boolean> escalate(String taskId, int expectedLevel, int newLevel,
            LocalDateTime newDeadline) {
        return db.sql("""
                UPDATE medical_human_tasks
                SET escalation_level = :newLevel, sla_deadline = :newDeadline
                WHERE id = :id AND status = 'PENDING' AND escalation_level = :expectedLevel
                """)
                .bind("newLevel", newLevel)
                .bind("newDeadline", newDeadline)
                .bind("id", taskId)
                .bind("expectedLevel", expectedLevel)
                .fetch().rowsUpdated().map(updated -> updated == 1);
    }

    /** Stale-claim release fenced on the exact claimed_at so a fresh claim is never clobbered. */
    public Mono<Boolean> releaseClaim(String taskId, LocalDateTime expectedClaimedAt) {
        return db.sql("""
                UPDATE medical_human_tasks
                SET status = 'PENDING', claimed_by = NULL, claimed_at = NULL
                WHERE id = :id AND status = 'CLAIMED' AND claimed_at = :expectedClaimedAt
                """)
                .bind("expectedClaimedAt", expectedClaimedAt)
                .bind("id", taskId)
                .fetch().rowsUpdated().map(updated -> updated == 1);
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
                row.get("created_at", LocalDateTime.class),
                MedicalHumanTaskRow.TaskKind.valueOf(row.get("task_kind", String.class)),
                row.get("priority", Integer.class),
                row.get("sla_deadline", LocalDateTime.class),
                row.get("escalation_level", Integer.class),
                row.get("claimed_by", String.class),
                row.get("claimed_at", LocalDateTime.class),
                row.get("completed_at", LocalDateTime.class),
                row.get("resolution_note", String.class));
    }
}
