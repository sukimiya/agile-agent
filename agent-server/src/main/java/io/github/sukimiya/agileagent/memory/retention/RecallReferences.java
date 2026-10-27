package io.github.sukimiya.agileagent.memory.retention;

import org.springframework.r2dbc.core.DatabaseClient;

import io.github.sukimiya.agileagent.memory.MemoryScope;
import reactor.core.publisher.Mono;

/**
 * Recall reference counts (M2b Q4 redesign B), computed from {@code memory_recall_events}: one reference per
 * turn whose answer prompt contained the target (so a replaced explanation of the same turn never counts
 * twice). Events stay counted after they are soft-deleted (their conversation or target was deleted): they are
 * the record of past use. An item's effective count subtracts its manual {@code recall_ref_offset}, floored at 0.
 */
public final class RecallReferences {

    /** Correlated subquery: references of kind {@code kind} to the id in column {@code refColumn} of alias. */
    static String countSql(String kind, String alias, String refColumn) {
        return "(SELECT COUNT(DISTINCT e.turn_id) FROM memory_recall_events e WHERE e.tenant_id = " + alias
                + ".tenant_id AND e.subject = " + alias + ".subject AND e.kind = '" + kind + "' AND e.ref_id = "
                + alias + "." + refColumn + ")";
    }

    private final DatabaseClient db;

    public RecallReferences(DatabaseClient db) {
        this.db = db;
    }

    /** Raw references to an item (before the manual offset). */
    public Mono<Long> itemReferences(MemoryScope scope, String itemId) {
        return db.sql("SELECT COUNT(DISTINCT turn_id) AS n FROM memory_recall_events WHERE tenant_id = :tenantId"
                        + " AND subject = :subject AND kind = 'ITEM' AND ref_id = :id")
                .bind("tenantId", scope.tenantId()).bind("subject", scope.subject()).bind("id", itemId)
                .map((row, meta) -> ((Number) row.get("n")).longValue())
                .one()
                .defaultIfEmpty(0L);
    }

    /** Effective count: {@code max(0, references - recall_ref_offset)}. */
    public Mono<Long> itemCount(MemoryScope scope, String itemId) {
        return db.sql("SELECT i.recall_ref_offset AS o, " + countSql("ITEM", "i", "id") + " AS n FROM memory_items i"
                        + " WHERE i.id = :id AND i.tenant_id = :tenantId AND i.subject = :subject")
                .bind("tenantId", scope.tenantId()).bind("subject", scope.subject()).bind("id", itemId)
                .map((row, meta) -> Math.max(0, ((Number) row.get("n")).longValue()
                        - ((Number) row.get("o")).longValue()))
                .one()
                .defaultIfEmpty(0L);
    }
}
