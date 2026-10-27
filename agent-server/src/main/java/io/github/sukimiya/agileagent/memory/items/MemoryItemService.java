package io.github.sukimiya.agileagent.memory.items;

import java.util.List;

import org.springframework.r2dbc.connection.R2dbcTransactionManager;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.reactive.TransactionalOperator;

import io.github.sukimiya.agileagent.memory.MemoryException;
import io.github.sukimiya.agileagent.memory.MemoryException.ErrorCode;
import io.github.sukimiya.agileagent.memory.MemoryScope;
import io.github.sukimiya.agileagent.memory.judgement.ValidatedAction.SourceRef;
import io.github.sukimiya.agileagent.memory.judgement.WriteGovernance;
import io.r2dbc.spi.ConnectionFactory;
import reactor.core.publisher.Mono;

/**
 * The user's own long-term memory (M2b Task 5, D15=A): list, detail with provenance, correction (D16=A: a new
 * {@code USER_CONFIRMED} item supersedes the old one), deletion and profile deletion. Every operation is
 * scoped to the caller's {@code (tenantId, subject)}; another owner's or a deleted item is
 * {@code MEMORY_ITEM_NOT_FOUND}.
 */
@Service
public class MemoryItemService {

    public static final int DEFAULT_LIMIT = 20;
    public static final int MAX_LIMIT = 100;
    static final String CORRECTION_MODEL = "user";
    static final String CORRECTION_REASON = "USER_CORRECTION";

    /** One page, newest first; {@code nextBefore} is the cursor for the next page (null when none). */
    public record ItemPage(List<MemoryItem> items, String nextBefore) {}

    public record ItemDetail(MemoryItem item, List<SourceRef> sources) {}

    private static final String COLUMNS = "id, tenant_id, subject, conversation_id, item_type, content, status,"
            + " assertion, importance, confidence, valid_until, last_recalled_at, superseded_by, rules_version,"
            + " judge_model, reason, revision, created_at, updated_at";

    private final DatabaseClient db;
    private final TransactionalOperator transactions;
    private final MemoryItemRepository items;
    private final MemoryItemSourceRepository sources;
    private final EntityGraphRepository graph;
    private final ProfileService profiles;

    public MemoryItemService(DatabaseClient db, ConnectionFactory connectionFactory, MemoryItemRepository items,
            MemoryItemSourceRepository sources, EntityGraphRepository graph, ProfileService profiles) {
        this.db = db;
        this.transactions = TransactionalOperator.create(new R2dbcTransactionManager(connectionFactory));
        this.items = items;
        this.sources = sources;
        this.graph = graph;
        this.profiles = profiles;
    }

    /**
     * Items newest first (keyset on {@code created_at, id}); {@code status} null means every status except
     * DELETED. {@code before} is the {@code nextBefore} of the previous page.
     */
    public Mono<ItemPage> list(MemoryScope scope, ItemStatus status, int limit, String before) {
        if (limit < 1 || limit > MAX_LIMIT) {
            return Mono.error(new MemoryException(ErrorCode.INVALID_ARGUMENT));
        }
        if (status == ItemStatus.DELETED) {
            return Mono.just(new ItemPage(List.of(), null));
        }
        Mono<java.util.Optional<MemoryItem>> cursor = before == null || before.isBlank()
                ? Mono.just(java.util.Optional.empty())
                : visible(scope, before).map(java.util.Optional::of)
                        .switchIfEmpty(Mono.error(new MemoryException(ErrorCode.INVALID_ARGUMENT)));
        return cursor.flatMap(c -> {
            var sql = new StringBuilder("SELECT " + COLUMNS + " FROM memory_items WHERE tenant_id = :tenantId"
                    + " AND subject = :subject AND status <> 'DELETED'");
            if (status != null) {
                sql.append(" AND status = :status");
            }
            if (c.isPresent()) {
                sql.append(" AND (created_at < :cAt OR (created_at = :cAt AND id < :cId))");
            }
            sql.append(" ORDER BY created_at DESC, id DESC LIMIT ").append(limit + 1);
            var spec = db.sql(sql.toString()).bind("tenantId", scope.tenantId()).bind("subject", scope.subject());
            if (status != null) {
                spec = spec.bind("status", status.name());
            }
            if (c.isPresent()) {
                spec = spec.bind("cAt", c.get().createdAt()).bind("cId", c.get().id());
            }
            return spec.map((row, meta) -> MemoryItemRepository.mapRow(row)).all().collectList().map(rows -> {
                if (rows.size() <= limit) {
                    return new ItemPage(rows, null);
                }
                var page = rows.subList(0, limit);
                return new ItemPage(List.copyOf(page), page.getLast().id());
            });
        });
    }

    public Mono<ItemDetail> detail(MemoryScope scope, String id) {
        return visibleOrNotFound(scope, id)
                .flatMap(item -> sources.findByItem(item.id()).collectList().map(s -> new ItemDetail(item, s)));
    }

    /**
     * D16=A: a new ACTIVE {@code USER_CONFIRMED} item with {@code content} (same type and conversation)
     * supersedes the old one in one transaction. F3 applies (blocked PII or blank is {@code INVALID_ARGUMENT}).
     * A superseded item, or {@code expectedRevision} not matching, is {@code MEMORY_ITEM_CONFLICT}.
     */
    public Mono<MemoryItem> correct(MemoryScope scope, String id, String content, Long expectedRevision) {
        if (content == null || content.isBlank() || WriteGovernance.containsBlockedPii(content)) {
            return Mono.error(new MemoryException(ErrorCode.INVALID_ARGUMENT));
        }
        var text = content.strip();
        return visibleOrNotFound(scope, id).flatMap(old -> {
            if (old.status() == ItemStatus.SUPERSEDED
                    || (expectedRevision != null && expectedRevision != old.revision())) {
                return Mono.error(new MemoryException(ErrorCode.MEMORY_ITEM_CONFLICT));
            }
            var created = MemoryItem.newItem(scope, old.conversationId(), old.itemType(), text, ItemStatus.ACTIVE,
                    MemoryItem.Assertion.USER_CONFIRMED, old.importance(), 1.0, old.rulesVersion(),
                    CORRECTION_MODEL, CORRECTION_REASON);
            return items.save(created)
                    .then(items.casUpdate(scope, old.id(), old.revision(), ItemStatus.SUPERSEDED, null, null,
                            created.id(), null))
                    .flatMap(ok -> ok ? Mono.just(created)
                            : Mono.<MemoryItem>error(new MemoryException(ErrorCode.MEMORY_ITEM_CONFLICT)))
                    .as(transactions::transactional)
                    .flatMap(c -> profiles.refreshSnapshot(scope).thenReturn(c))
                    .flatMap(c -> items.findById(scope, c.id()).map(o -> o.orElse(c)));
        });
    }

    /**
     * Deletes an item: status DELETED and content erased, provenance removed, relations derived from it
     * deactivated, its recall explanations removed, and a kept profile snapshot re-rendered without it.
     */
    public Mono<Void> delete(MemoryScope scope, String id) {
        return visibleOrNotFound(scope, id).flatMap(item -> db.sql("UPDATE memory_items SET status = 'DELETED',"
                                + " content = '', revision = revision + 1, updated_at = CURRENT_TIMESTAMP(6)"
                                + " WHERE id = :id AND tenant_id = :tenantId AND subject = :subject")
                        .bind("id", id).bind("tenantId", scope.tenantId()).bind("subject", scope.subject())
                        .fetch().rowsUpdated()
                        .then(db.sql("DELETE FROM memory_item_sources WHERE item_id = :id").bind("id", id)
                                .fetch().rowsUpdated())
                        .then(db.sql("UPDATE memory_relations SET status = 'INACTIVE' WHERE item_id = :id"
                                        + " AND tenant_id = :tenantId AND subject = :subject")
                                .bind("id", id).bind("tenantId", scope.tenantId()).bind("subject", scope.subject())
                                .fetch().rowsUpdated())
                        .then(db.sql("DELETE FROM memory_recall_events WHERE kind = 'ITEM' AND ref_id = :id"
                                        + " AND tenant_id = :tenantId AND subject = :subject")
                                .bind("id", id).bind("tenantId", scope.tenantId()).bind("subject", scope.subject())
                                .fetch().rowsUpdated())
                        .as(transactions::transactional)
                        .then(profiles.refreshSnapshot(scope)));
    }

    /**
     * Deletes the profile snapshot and the whole entity graph of the caller (and their PROFILE / RELATION recall
     * explanations). Items stay; a later rebuild (switch on) derives a new snapshot from them.
     */
    public Mono<Void> deleteProfile(MemoryScope scope) {
        return graph.deleteAllForScope(scope)
                .then(db.sql("DELETE FROM memory_recall_events WHERE kind IN ('PROFILE', 'RELATION')"
                                + " AND tenant_id = :tenantId AND subject = :subject")
                        .bind("tenantId", scope.tenantId()).bind("subject", scope.subject())
                        .fetch().rowsUpdated())
                .as(transactions::transactional)
                .then(profiles.purge(scope));
    }

    private Mono<MemoryItem> visible(MemoryScope scope, String id) {
        if (id == null || id.isBlank() || id.length() > 64) {
            return Mono.empty();
        }
        return items.findById(scope, id).flatMap(opt -> Mono.justOrEmpty(opt))
                .filter(item -> item.status() != ItemStatus.DELETED);
    }

    private Mono<MemoryItem> visibleOrNotFound(MemoryScope scope, String id) {
        return visible(scope, id).switchIfEmpty(Mono.error(new MemoryException(ErrorCode.MEMORY_ITEM_NOT_FOUND)));
    }
}
