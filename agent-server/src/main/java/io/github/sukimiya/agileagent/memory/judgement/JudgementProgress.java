package io.github.sukimiya.agileagent.memory.judgement;

import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Repository;

import io.github.sukimiya.agileagent.memory.MemoryScope;
import reactor.core.publisher.Mono;

/** {@code memory_index_progress.judged_through_sequence} CAS helpers. */
@Repository
public class JudgementProgress {

    private final DatabaseClient db;

    public JudgementProgress(DatabaseClient db) {
        this.db = db;
    }

    public Mono<Long> judgedThrough(MemoryScope scope, String conversationId) {
        return db.sql("SELECT judged_through_sequence FROM memory_index_progress WHERE conversation_id = :c"
                        + " AND tenant_id = :tenantId AND subject = :subject")
                .bind("c", conversationId).bind("tenantId", scope.tenantId()).bind("subject", scope.subject())
                .map((row, meta) -> ((Number) row.get("judged_through_sequence")).longValue())
                .one()
                .defaultIfEmpty(0L);
    }

    /** Advances the watermark when still at {@code expected}; creates the row if missing. */
    public Mono<Boolean> advance(MemoryScope scope, String conversationId, long expected, long through) {
        return db.sql("UPDATE memory_index_progress SET judged_through_sequence = :through,"
                        + " updated_at = CURRENT_TIMESTAMP(6)"
                        + " WHERE conversation_id = :c AND tenant_id = :tenantId AND subject = :subject"
                        + " AND judged_through_sequence = :expected")
                .bind("through", through).bind("c", conversationId).bind("tenantId", scope.tenantId())
                .bind("subject", scope.subject()).bind("expected", expected)
                .fetch().rowsUpdated()
                .flatMap(n -> {
                    if (n == 1) {
                        return Mono.just(true);
                    }
                    if (expected != 0) {
                        return Mono.just(false);
                    }
                    return db.sql("INSERT INTO memory_index_progress (conversation_id, tenant_id, subject,"
                                    + " indexed_through_sequence, summarized_through_sequence,"
                                    + " judged_through_sequence, summary_invalid_attempts, updated_at)"
                                    + " VALUES (:c, :tenantId, :subject, 0, 0, :through, 0, CURRENT_TIMESTAMP(6))")
                            .bind("c", conversationId).bind("tenantId", scope.tenantId())
                            .bind("subject", scope.subject()).bind("through", through)
                            .fetch().rowsUpdated()
                            .map(ins -> ins == 1)
                            .onErrorReturn(false);
                });
    }
}
