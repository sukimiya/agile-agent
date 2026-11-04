package io.github.sukimiya.agileagent.memory.items;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Repository;

import io.github.sukimiya.agileagent.memory.MemoryProperties;
import io.github.sukimiya.agileagent.memory.MemoryScope;
import io.github.sukimiya.agileagent.util.NonBlockingIds;
import io.r2dbc.spi.Readable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * One-hop entity graph for user memory (relation write path T1, user decisions 2026-11-04). Every relation belongs to
 * a memory item and is visible exactly while that item is: ACTIVE, valid, unexpired and in the recall scope (switch on:
 * user-level items; switch off: items of the current conversation), with both entities valid. All filtering is in SQL.
 * Writes are checked against {@code app.memory.relations.*} (read live) as defense in depth.
 */
@Repository
public class EntityGraphRepository {

    /** Reserved normalized key of the per-user self entity ("I" in the user's words). */
    public static final String SELF_KEY = "@self";
    public static final String SELF_TYPE = MemoryProperties.Relations.SELF_TYPE;
    /** Display name of the self entity in injected relation lines. */
    public static final String SELF_NAME = "用户";

    /** A relation with both of its entities, as injected into a prompt. */
    public record RelationHop(MemoryRelation relation, MemoryEntity from, MemoryEntity to) {
    }

    private static final String ENTITY_COLUMNS = "id, tenant_id, subject, name, entity_type, normalized_key, is_valid";

    private final DatabaseClient db;
    private final MemoryProperties props;

    public EntityGraphRepository(DatabaseClient db, MemoryProperties props) {
        props.getRelations().validate();
        this.db = db;
        this.props = props;
    }

    public static String normalize(String name) {
        return name == null ? "" : name.strip().toLowerCase(Locale.ROOT);
    }

    /** Finds or creates the entity {@code name}; a soft-deleted one with the same key is made valid again. */
    public Mono<MemoryEntity> upsertEntity(MemoryScope scope, String name, String entityType) {
        return Mono.defer(() -> {
            var rel = props.getRelations();
            String key = normalize(name);
            if (key.isEmpty()) {
                return Mono.error(new IllegalArgumentException("entity name must not be blank"));
            }
            if (SELF_KEY.equals(key)) {
                return Mono.error(new IllegalArgumentException("entity name " + SELF_KEY + " is reserved"));
            }
            String display = name.strip();
            if (display.length() > rel.getMaxEntityNameChars()) {
                return Mono.error(new IllegalArgumentException("entity name must be at most "
                        + rel.getMaxEntityNameChars() + " characters"));
            }
            if (entityType == null || !rel.getEntityTypes().contains(entityType)) {
                return Mono.error(new IllegalArgumentException("unknown entity type: " + entityType));
            }
            return findOrCreate(scope, key, display, entityType);
        });
    }

    /** The user's self entity (created on first use). */
    public Mono<MemoryEntity> self(MemoryScope scope) {
        return findOrCreate(scope, SELF_KEY, SELF_NAME, SELF_TYPE);
    }

    private Mono<MemoryEntity> findOrCreate(MemoryScope scope, String key, String name, String type) {
        return db.sql("SELECT " + ENTITY_COLUMNS + " FROM memory_entities WHERE tenant_id = :tenantId"
                        + " AND subject = :subject AND normalized_key = :key")
                .bind("tenantId", scope.tenantId()).bind("subject", scope.subject()).bind("key", key)
                .map((row, meta) -> new Object[] {entity(row, ""), ((Number) row.get("is_valid")).intValue()})
                .one()
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
                            .bind("name", name).bind("type", type).bind("key", key)
                            .fetch().rowsUpdated()
                            .thenReturn(new MemoryEntity(id, scope, name, type, key))
                            .onErrorResume(DuplicateKeyException.class, e -> findOrCreate(scope, key, name, type));
                }));
    }

    /**
     * Saves the edge {@code from —relation→ to} of item {@code itemId}. The same edge of the same item is stored once:
     * saving it again returns the existing row, made ACTIVE and valid again.
     */
    public Mono<MemoryRelation> saveRelation(MemoryScope scope, String itemId, String fromEntityId, String relation,
            String toEntityId) {
        return Mono.defer(() -> {
            if (itemId == null || itemId.isBlank()) {
                return Mono.error(new IllegalArgumentException("a relation needs its memory item"));
            }
            if (fromEntityId == null || toEntityId == null) {
                return Mono.error(new IllegalArgumentException("a relation needs both entities"));
            }
            if (fromEntityId.equals(toEntityId)) {
                return Mono.error(new IllegalArgumentException("an entity cannot relate to itself"));
            }
            if (relation == null || !props.getRelations().getTypes().contains(relation)) {
                return Mono.error(new IllegalArgumentException("unknown relation type: " + relation));
            }
            return existing(scope, itemId, fromEntityId, relation, toEntityId)
                    .flatMap(id -> db.sql("UPDATE memory_relations SET status = 'ACTIVE', is_valid = 1,"
                                    + " valid_until = NULL WHERE id = :id").bind("id", id).fetch().rowsUpdated()
                            .thenReturn(id))
                    .switchIfEmpty(Mono.defer(() -> insert(scope, itemId, fromEntityId, relation, toEntityId)))
                    .map(id -> new MemoryRelation(id, scope, fromEntityId, relation, toEntityId, itemId, null, null,
                            "ACTIVE"));
        });
    }

    private Mono<String> existing(MemoryScope scope, String itemId, String from, String relation, String to) {
        return db.sql("SELECT id FROM memory_relations WHERE tenant_id = :tenantId AND subject = :subject"
                        + " AND item_id = :itemId AND from_entity = :fromE AND relation = :rel AND to_entity = :toE")
                .bind("tenantId", scope.tenantId()).bind("subject", scope.subject()).bind("itemId", itemId)
                .bind("fromE", from).bind("rel", relation).bind("toE", to)
                .map((row, meta) -> row.get("id", String.class)).one();
    }

    private Mono<String> insert(MemoryScope scope, String itemId, String from, String relation, String to) {
        var id = NonBlockingIds.uuid();
        return db.sql("INSERT INTO memory_relations (id, tenant_id, subject, from_entity, relation, to_entity,"
                        + " item_id, valid_from, valid_until, status) VALUES (:id, :tenantId, :subject, :fromE,"
                        + " :rel, :toE, :itemId, CURRENT_TIMESTAMP(6), NULL, 'ACTIVE')")
                .bind("id", id).bind("tenantId", scope.tenantId()).bind("subject", scope.subject())
                .bind("fromE", from).bind("rel", relation).bind("toE", to).bind("itemId", itemId)
                .fetch().rowsUpdated().thenReturn(id)
                .onErrorResume(DuplicateKeyException.class, e -> existing(scope, itemId, from, relation, to));
    }

    public Mono<Boolean> supersedeRelation(MemoryScope scope, String relationId) {
        return db.sql("UPDATE memory_relations SET status = 'SUPERSEDED' WHERE id = :id AND tenant_id = :tenantId"
                        + " AND subject = :subject")
                .bind("id", relationId).bind("tenantId", scope.tenantId()).bind("subject", scope.subject())
                .fetch().rowsUpdated().map(n -> n == 1);
    }

    /**
     * Current one-hop relations touching any entity whose normalized key is in {@code entityKeys}, newest first, one
     * per (from, relation, to), at most {@code limit}. Visibility follows the owning item: with {@code crossConversation}
     * only user-level items, otherwise only items of {@code conversationId} (none when it is null).
     */
    public Flux<RelationHop> oneHop(MemoryScope scope, String conversationId, boolean crossConversation,
            Collection<String> entityKeys, int limit) {
        if (entityKeys == null || entityKeys.isEmpty() || limit <= 0 || (!crossConversation && conversationId == null)) {
            return Flux.empty();
        }
        List<String> keys = entityKeys.stream().map(EntityGraphRepository::normalize).filter(k -> !k.isEmpty())
                .distinct().toList();
        if (keys.isEmpty()) {
            return Flux.empty();
        }
        var sql = "SELECT r.id, r.tenant_id, r.subject, r.from_entity, r.relation, r.to_entity, r.item_id,"
                + " r.valid_from, r.valid_until, r.status,"
                + " fe.name AS f_name, fe.entity_type AS f_type, fe.normalized_key AS f_key,"
                + " te.name AS t_name, te.entity_type AS t_type, te.normalized_key AS t_key"
                + " FROM memory_relations r"
                + " JOIN memory_items i ON i.id = r.item_id AND i.tenant_id = r.tenant_id AND i.subject = r.subject"
                + " JOIN memory_entities fe ON fe.id = r.from_entity AND fe.tenant_id = r.tenant_id"
                + " AND fe.subject = r.subject AND fe.is_valid = 1"
                + " JOIN memory_entities te ON te.id = r.to_entity AND te.tenant_id = r.tenant_id"
                + " AND te.subject = r.subject AND te.is_valid = 1"
                + " WHERE r.tenant_id = :tenantId AND r.subject = :subject AND r.status = 'ACTIVE' AND r.is_valid = 1"
                + " AND (r.valid_until IS NULL OR r.valid_until > CURRENT_TIMESTAMP(6))"
                + " AND i.status = 'ACTIVE' AND i.is_valid = 1"
                + " AND (i.valid_until IS NULL OR i.valid_until > CURRENT_TIMESTAMP(6))"
                + (crossConversation ? " AND i.conversation_id IS NULL" : " AND i.conversation_id = :conversationId")
                + " AND (fe.normalized_key IN (:keys) OR te.normalized_key IN (:keys))"
                + " ORDER BY r.valid_from DESC, r.id DESC";
        var spec = db.sql(sql).bind("tenantId", scope.tenantId()).bind("subject", scope.subject()).bind("keys", keys);
        if (!crossConversation) {
            spec = spec.bind("conversationId", conversationId);
        }
        var seen = new HashSet<String>();
        return spec.map((row, meta) -> {
                    var owner = new MemoryScope(row.get("tenant_id", String.class), row.get("subject", String.class));
                    var rel = new MemoryRelation(row.get("id", String.class), owner,
                            row.get("from_entity", String.class), row.get("relation", String.class),
                            row.get("to_entity", String.class), row.get("item_id", String.class),
                            row.get("valid_from", LocalDateTime.class), row.get("valid_until", LocalDateTime.class),
                            row.get("status", String.class));
                    return new RelationHop(rel,
                            new MemoryEntity(rel.fromEntityId(), owner, row.get("f_name", String.class),
                                    row.get("f_type", String.class), row.get("f_key", String.class)),
                            new MemoryEntity(rel.toEntityId(), owner, row.get("t_name", String.class),
                                    row.get("t_type", String.class), row.get("t_key", String.class)));
                })
                .all()
                .filter(h -> seen.add(h.relation().fromEntityId() + '\u0000' + h.relation().relation() + '\u0000'
                        + h.relation().toEntityId()))
                .take(limit);
    }

    private static MemoryEntity entity(Readable row, String prefix) {
        return new MemoryEntity(row.get(prefix + "id", String.class),
                new MemoryScope(row.get("tenant_id", String.class), row.get("subject", String.class)),
                row.get(prefix + "name", String.class), row.get(prefix + "entity_type", String.class),
                row.get(prefix + "normalized_key", String.class));
    }

    /** A valid entity of the owner by id. */
    public Mono<MemoryEntity> findById(MemoryScope scope, String id) {
        return db.sql("SELECT " + ENTITY_COLUMNS + " FROM memory_entities"
                        + " WHERE id = :id AND tenant_id = :tenantId AND subject = :subject AND is_valid = 1")
                .bind("id", id).bind("tenantId", scope.tenantId()).bind("subject", scope.subject())
                .map((row, meta) -> entity(row, "")).one();
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
