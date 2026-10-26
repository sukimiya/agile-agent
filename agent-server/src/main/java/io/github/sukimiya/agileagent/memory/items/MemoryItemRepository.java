package io.github.sukimiya.agileagent.memory.items;

import java.time.LocalDateTime;
import java.util.Optional;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Repository;

import io.github.sukimiya.agileagent.memory.MemoryScope;
import reactor.core.publisher.Mono;

/**
 * Owner-scoped access to {@code memory_items}. Every read binds {@code (tenant_id, subject)} with
 * {@code utf8mb4_bin} case-sensitive matching, so {@code Alice} and {@code alice} never see each other.
 */
@Repository
public class MemoryItemRepository {

    private final DatabaseClient db;

    public MemoryItemRepository(DatabaseClient db) {
        this.db = db;
    }

    public Mono<Boolean> save(MemoryItem item) {
        var scope = item.scope();
        var spec = db.sql("INSERT INTO memory_items (id, tenant_id, subject, conversation_id, item_type, content,"
                        + " status, assertion, importance, confidence, valid_until, last_recalled_at, superseded_by,"
                        + " rules_version, judge_model, reason, revision, created_at, updated_at) VALUES (:id,"
                        + " :tenantId, :subject, :conversationId, :itemType, :content, :status, :assertion,"
                        + " :importance, :confidence, :validUntil, :lastRecalledAt, :supersededBy, :rulesVersion,"
                        + " :judgeModel, :reason, :revision, CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6))")
                .bind("id", item.id())
                .bind("tenantId", scope.tenantId())
                .bind("subject", scope.subject())
                .bind("itemType", item.itemType())
                .bind("content", item.content())
                .bind("status", item.status().name())
                .bind("assertion", item.assertion().name())
                .bind("rulesVersion", item.rulesVersion())
                .bind("judgeModel", item.judgeModel())
                .bind("revision", item.revision());
        spec = nullable(spec, "conversationId", item.conversationId(), String.class);
        spec = nullable(spec, "importance", item.importance(), Double.class);
        spec = nullable(spec, "confidence", item.confidence(), Double.class);
        spec = nullable(spec, "validUntil", item.validUntil(), LocalDateTime.class);
        spec = nullable(spec, "lastRecalledAt", item.lastRecalledAt(), LocalDateTime.class);
        spec = nullable(spec, "supersededBy", item.supersededBy(), String.class);
        spec = nullable(spec, "reason", item.reason(), String.class);
        return spec.fetch().rowsUpdated()
                .map(n -> n == 1)
                .onErrorResume(DuplicateKeyException.class, e -> Mono.just(false));
    }

    public Mono<Optional<MemoryItem>> findById(MemoryScope scope, String id) {
        return db.sql("SELECT id, tenant_id, subject, conversation_id, item_type, content, status, assertion,"
                        + " importance, confidence, valid_until, last_recalled_at, superseded_by, rules_version,"
                        + " judge_model, reason, revision, created_at, updated_at FROM memory_items"
                        + " WHERE id = :id AND tenant_id = :tenantId AND subject = :subject")
                .bind("id", id).bind("tenantId", scope.tenantId()).bind("subject", scope.subject())
                .map((row, meta) -> new MemoryItem(row.get("id", String.class),
                        new MemoryScope(row.get("tenant_id", String.class), row.get("subject", String.class)),
                        row.get("conversation_id", String.class), row.get("item_type", String.class),
                        row.get("content", String.class), ItemStatus.valueOf(row.get("status", String.class)),
                        MemoryItem.Assertion.valueOf(row.get("assertion", String.class)),
                        row.get("importance", Double.class), row.get("confidence", Double.class),
                        row.get("valid_until", LocalDateTime.class), row.get("last_recalled_at", LocalDateTime.class),
                        row.get("superseded_by", String.class), row.get("rules_version", Integer.class),
                        row.get("judge_model", String.class), row.get("reason", String.class),
                        row.get("revision", Long.class), row.get("created_at", LocalDateTime.class),
                        row.get("updated_at", LocalDateTime.class)))
                .one()
                .map(Optional::of)
                .defaultIfEmpty(Optional.empty());
    }


    /**
     * Compare-and-set status / content / assertion / superseded_by when {@code expectedRevision} matches;
     * bumps revision on success.
     */
    public Mono<Boolean> casUpdate(MemoryScope scope, String id, long expectedRevision, ItemStatus status,
            String content, MemoryItem.Assertion assertion, String supersededBy, String reason) {
        var spec = db.sql("UPDATE memory_items SET status = :status, content = COALESCE(:content, content),"
                        + " assertion = COALESCE(:assertion, assertion), superseded_by = :supersededBy,"
                        + " reason = COALESCE(:reason, reason), revision = revision + 1,"
                        + " updated_at = CURRENT_TIMESTAMP(6)"
                        + " WHERE id = :id AND tenant_id = :tenantId AND subject = :subject"
                        + " AND revision = :rev")
                .bind("status", status.name()).bind("id", id).bind("tenantId", scope.tenantId())
                .bind("subject", scope.subject()).bind("rev", expectedRevision);
        spec = content == null ? spec.bindNull("content", String.class) : spec.bind("content", content);
        spec = assertion == null ? spec.bindNull("assertion", String.class)
                : spec.bind("assertion", assertion.name());
        spec = supersededBy == null ? spec.bindNull("supersededBy", String.class)
                : spec.bind("supersededBy", supersededBy);
        spec = reason == null ? spec.bindNull("reason", String.class) : spec.bind("reason", reason);
        return spec.fetch().rowsUpdated().map(n -> n == 1);
    }

    public Mono<Boolean> markInactive(MemoryScope scope, String id) {
        return db.sql("UPDATE memory_items SET status = 'INACTIVE', revision = revision + 1,"
                        + " updated_at = CURRENT_TIMESTAMP(6)"
                        + " WHERE id = :id AND tenant_id = :tenantId AND subject = :subject"
                        + " AND status NOT IN ('DELETED', 'SUPERSEDED')")
                .bind("id", id).bind("tenantId", scope.tenantId()).bind("subject", scope.subject())
                .fetch().rowsUpdated().map(n -> n == 1);
    }

    private static <T> DatabaseClient.GenericExecuteSpec nullable(DatabaseClient.GenericExecuteSpec spec,
            String name, T value, Class<T> type) {
        return value == null ? spec.bindNull(name, type) : spec.bind(name, value);
    }
}
