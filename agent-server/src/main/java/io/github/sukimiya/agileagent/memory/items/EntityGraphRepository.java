package io.github.sukimiya.agileagent.memory.items;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.Locale;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Repository;

import io.github.sukimiya.agileagent.memory.MemoryScope;
import io.github.sukimiya.agileagent.util.NonBlockingIds;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * One-hop entity graph for user memory (M2b Task 4). Reads only ACTIVE relations that are currently valid.
 */
@Repository
public class EntityGraphRepository {

    private final DatabaseClient db;

    public EntityGraphRepository(DatabaseClient db) {
        this.db = db;
    }

    public static String normalize(String name) {
        return name == null ? "" : name.trim().toLowerCase(Locale.ROOT);
    }

    public Mono<MemoryEntity> upsertEntity(MemoryScope scope, String name, String entityType) {
        String key = normalize(name);
        if (key.isBlank()) {
            return Mono.empty();
        }
        return db.sql("SELECT id, tenant_id, subject, name, entity_type, normalized_key, is_valid"
                        + " FROM memory_entities WHERE tenant_id = :tenantId AND subject = :subject"
                        + " AND normalized_key = :key")
                .bind("tenantId", scope.tenantId()).bind("subject", scope.subject()).bind("key", key)
                .map((row, meta) -> new Object[] {new MemoryEntity(row.get("id", String.class),
                        new MemoryScope(row.get("tenant_id", String.class), row.get("subject", String.class)),
                        row.get("name", String.class), row.get("entity_type", String.class),
                        row.get("normalized_key", String.class)), ((Number) row.get("is_valid")).intValue()})
                .one()
                // A soft-deleted entity with the same key is made valid again (the key is unique per owner).
                .flatMap(found -> {
                    var entity = (MemoryEntity) found[0];
                    if ((int) found[1] == 1) {
                        return Mono.just(entity);
                    }
                    return db.sql("UPDATE memory_entities SET is_valid = 1 WHERE id = :id")
                            .bind("id", entity.id()).fetch().rowsUpdated().thenReturn(entity);
                })
                .switchIfEmpty(Mono.defer(() -> {
                    var id = NonBlockingIds.uuid();
                    return db.sql("INSERT INTO memory_entities (id, tenant_id, subject, name, entity_type,"
                                    + " normalized_key) VALUES (:id, :tenantId, :subject, :name, :type, :key)")
                            .bind("id", id).bind("tenantId", scope.tenantId()).bind("subject", scope.subject())
                            .bind("name", name.trim()).bind("type", entityType == null ? "OTHER" : entityType)
                            .bind("key", key)
                            .fetch().rowsUpdated()
                            .thenReturn(new MemoryEntity(id, scope, name.trim(),
                                    entityType == null ? "OTHER" : entityType, key))
                            .onErrorResume(DuplicateKeyException.class, e -> upsertEntity(scope, name, entityType));
                }));
    }

    public Mono<MemoryRelation> saveRelation(MemoryScope scope, String fromEntityId, String relation,
            String toEntityId, String itemId, String status) {
        var id = NonBlockingIds.uuid();
        String st = status == null ? "ACTIVE" : status;
        var spec = db.sql("INSERT INTO memory_relations (id, tenant_id, subject, from_entity, relation, to_entity,"
                        + " item_id, valid_from, valid_until, status) VALUES (:id, :tenantId, :subject, :fromE,"
                        + " :rel, :toE, :itemId, CURRENT_TIMESTAMP(6), NULL, :status)")
                .bind("id", id).bind("tenantId", scope.tenantId()).bind("subject", scope.subject())
                .bind("fromE", fromEntityId).bind("rel", relation).bind("toE", toEntityId)
                .bind("status", st);
        spec = itemId == null ? spec.bindNull("itemId", String.class) : spec.bind("itemId", itemId);
        return spec.fetch().rowsUpdated()
                .thenReturn(new MemoryRelation(id, scope, fromEntityId, relation, toEntityId, itemId, null, null, st));
    }

    public Mono<Boolean> supersedeRelation(MemoryScope scope, String relationId) {
        return db.sql("UPDATE memory_relations SET status = 'SUPERSEDED' WHERE id = :id AND tenant_id = :tenantId"
                        + " AND subject = :subject")
                .bind("id", relationId).bind("tenantId", scope.tenantId()).bind("subject", scope.subject())
                .fetch().rowsUpdated().map(n -> n == 1);
    }

    /**
     * One-hop ACTIVE relations from any of {@code entityKeys} (normalized), currently valid, limited.
     */
    public Flux<MemoryRelation> oneHop(MemoryScope scope, Collection<String> entityKeys, int limit) {
        if (entityKeys == null || entityKeys.isEmpty() || limit <= 0) {
            return Flux.empty();
        }
        var keys = entityKeys.stream().map(EntityGraphRepository::normalize).filter(k -> !k.isBlank()).distinct()
                .toList();
        if (keys.isEmpty()) {
            return Flux.empty();
        }
        return entityIdsForKeys(scope, keys).flatMapMany(ids -> {
            if (ids.isEmpty()) {
                return Flux.empty();
            }
            return db.sql("SELECT id, tenant_id, subject, from_entity, relation, to_entity, item_id,"
                            + " valid_from, valid_until, status FROM memory_relations"
                            + " WHERE tenant_id = :tenantId AND subject = :subject AND status = 'ACTIVE'"
                            + " AND is_valid = 1 AND (valid_until IS NULL OR valid_until > CURRENT_TIMESTAMP(6))"
                            + " AND NOT EXISTS (SELECT 1 FROM memory_items fi WHERE fi.id = memory_relations.item_id"
                            + " AND fi.status = 'FORGOTTEN')"
                            + " ORDER BY valid_from DESC")
                    .bind("tenantId", scope.tenantId()).bind("subject", scope.subject())
                    .map((row, meta) -> new MemoryRelation(row.get("id", String.class),
                            new MemoryScope(row.get("tenant_id", String.class), row.get("subject", String.class)),
                            row.get("from_entity", String.class), row.get("relation", String.class),
                            row.get("to_entity", String.class), row.get("item_id", String.class),
                            row.get("valid_from", LocalDateTime.class), row.get("valid_until", LocalDateTime.class),
                            row.get("status", String.class)))
                    .all()
                    .filter(r -> ids.contains(r.fromEntityId()) || ids.contains(r.toEntityId()))
                    .take(limit);
        });
    }

    private Mono<java.util.Set<String>> entityIdsForKeys(MemoryScope scope, java.util.List<String> keys) {
        return Flux.fromIterable(keys)
                .concatMap(key -> db.sql("SELECT id FROM memory_entities WHERE tenant_id = :tenantId"
                                + " AND subject = :subject AND normalized_key = :key AND is_valid = 1")
                        .bind("tenantId", scope.tenantId()).bind("subject", scope.subject()).bind("key", key)
                        .map((row, meta) -> row.get("id", String.class))
                        .one())
                .collect(java.util.stream.Collectors.toSet());
    }


    public Mono<MemoryEntity> findById(MemoryScope scope, String id) {
        return db.sql("SELECT id, tenant_id, subject, name, entity_type, normalized_key FROM memory_entities"
                        + " WHERE id = :id AND tenant_id = :tenantId AND subject = :subject AND is_valid = 1")
                .bind("id", id).bind("tenantId", scope.tenantId()).bind("subject", scope.subject())
                .map((row, meta) -> new MemoryEntity(row.get("id", String.class),
                        new MemoryScope(row.get("tenant_id", String.class), row.get("subject", String.class)),
                        row.get("name", String.class), row.get("entity_type", String.class),
                        row.get("normalized_key", String.class)))
                .one();
    }

    /** Soft-deletes ({@code is_valid = 0}) the owner's whole graph; rows stay, never read again. */
    public Mono<Void> invalidateAllForScope(MemoryScope scope) {
        return db.sql("UPDATE memory_relations SET is_valid = 0 WHERE tenant_id = :tenantId AND subject = :subject")
                .bind("tenantId", scope.tenantId()).bind("subject", scope.subject())
                .fetch().rowsUpdated()
                .then(db.sql("UPDATE memory_entities SET is_valid = 0 WHERE tenant_id = :tenantId AND subject = :subject")
                        .bind("tenantId", scope.tenantId()).bind("subject", scope.subject())
                        .fetch().rowsUpdated())
                .then();
    }
}
