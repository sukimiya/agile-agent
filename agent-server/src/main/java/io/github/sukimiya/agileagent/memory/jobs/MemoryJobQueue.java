package io.github.sukimiya.agileagent.memory.jobs;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Component;

import io.github.sukimiya.agileagent.memory.MemoryScope;
import io.github.sukimiya.agileagent.util.NonBlockingIds;
import reactor.core.publisher.Mono;

/**
 * Registers durable background jobs in {@code memory_jobs}. An enqueue joins the caller's R2DBC transaction when
 * there is one (it uses the same {@link DatabaseClient}), so a job is committed or rolled back together with the
 * state change that caused it.
 */
@Component
public class MemoryJobQueue {

    private final DatabaseClient db;

    public MemoryJobQueue(DatabaseClient db) {
        this.db = db;
    }

    /**
     * Adds a PENDING job, due now. Emits true when it was added and false when a job of the same {@code type} and
     * {@code dedupeKey} already exists in any status (a null key never deduplicates). A duplicate is a
     * statement-level failure on both H2 and InnoDB, so the caller's transaction stays usable.
     */
    public Mono<Boolean> enqueue(MemoryScope scope, String conversationId, JobType type, String dedupeKey,
            String payloadJson) {
        return Mono.defer(() -> {
            var insert = db.sql("INSERT INTO memory_jobs (id, tenant_id, subject, conversation_id, job_type, status,"
                            + " attempts, dedupe_key, payload, available_at, created_at, updated_at) VALUES (:id,"
                            + " :tenantId, :subject, :c, :type, 'PENDING', 0, :dedupeKey, :payload,"
                            + " CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6))")
                    .bind("id", NonBlockingIds.uuid())
                    .bind("tenantId", scope.tenantId())
                    .bind("subject", scope.subject())
                    .bind("type", type.name());
            insert = conversationId == null ? insert.bindNull("c", String.class) : insert.bind("c", conversationId);
            insert = dedupeKey == null ? insert.bindNull("dedupeKey", String.class)
                    : insert.bind("dedupeKey", dedupeKey);
            insert = payloadJson == null ? insert.bindNull("payload", String.class)
                    : insert.bind("payload", payloadJson);
            return insert.fetch().rowsUpdated().map(n -> n == 1);
        }).onErrorResume(MemoryJobQueue::isDuplicateKey, e -> Mono.just(false));
    }

    private static boolean isDuplicateKey(Throwable error) {
        for (var cause = error; cause != null; cause = cause.getCause()) {
            if (cause instanceof DuplicateKeyException) {
                return true;
            }
        }
        return false;
    }
}
