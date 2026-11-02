package io.github.sukimiya.agileagent.memory.retention;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.r2dbc.core.DatabaseClient;

import io.github.sukimiya.agileagent.memory.MemoryScope;
import io.github.sukimiya.agileagent.util.NonBlockingIds;
import reactor.core.publisher.Mono;

/**
 * Durable audit of manual memory changes ({@code memory_audit_log}: actor, action, target, amount, counts,
 * database time; never content), mirrored as one JSON INFO line on the {@code memory.audit} logger.
 */
public final class MemoryAudit {

    static final String LOGGER = "memory.audit";
    private static final Logger log = LoggerFactory.getLogger(LOGGER);
    private static final com.fasterxml.jackson.databind.ObjectMapper JSON =
            new com.fasterxml.jackson.databind.ObjectMapper();

    private final DatabaseClient db;

    public MemoryAudit(DatabaseClient db) {
        this.db = db;
    }

    public Mono<Void> record(MemoryScope scope, String actor, String action, String targetKind, String targetId,
            long amount, long before, long after) {
        return write(scope, actor, action, targetKind, targetId, amount, before, after);
    }

    /** A change without amounts (for example a status change such as forget / restore). */
    public Mono<Void> record(MemoryScope scope, String actor, String action, String targetKind, String targetId) {
        return write(scope, actor, action, targetKind, targetId, null, null, null);
    }

    private Mono<Void> write(MemoryScope scope, String actor, String action, String targetKind, String targetId,
            Long amount, Long before, Long after) {
        return nullable(nullable(nullable(db.sql("INSERT INTO memory_audit_log (id, tenant_id, subject, actor, action, target_kind, target_id,"
                        + " amount, count_before, count_after, created_at) VALUES (:id, :tenantId, :subject, :actor,"
                        + " :action, :kind, :target, :amount, :before, :after, CURRENT_TIMESTAMP(6))")
                .bind("id", NonBlockingIds.uuid()).bind("tenantId", scope.tenantId()).bind("subject", scope.subject())
                .bind("actor", actor == null ? scope.subject() : actor).bind("action", action)
                .bind("kind", targetKind).bind("target", targetId), "amount", amount), "before", before),
                "after", after)
                .fetch().rowsUpdated()
                .doOnSuccess(n -> {
                    var line = new java.util.LinkedHashMap<String, Object>();
                    line.put("event", action);
                    line.put("tenantId", scope.tenantId());
                    line.put("subject", scope.subject());
                    line.put("actor", actor);
                    line.put("targetKind", targetKind);
                    line.put("targetId", targetId);
                    line.put("amount", amount);
                    line.put("countBefore", before);
                    line.put("countAfter", after);
                    try {
                        log.info(JSON.writeValueAsString(line));
                    } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
                        log.error("memory.audit line could not be written action={}", action);
                    }
                })
                .then();
    }

    private static DatabaseClient.GenericExecuteSpec nullable(DatabaseClient.GenericExecuteSpec spec, String name,
            Long value) {
        return value == null ? spec.bindNull(name, Long.class) : spec.bind(name, value);
    }
}
