package io.github.sukimiya.agileagent.memory.items;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

import org.springframework.r2dbc.connection.R2dbcTransactionManager;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.reactive.TransactionalOperator;

import io.github.sukimiya.agileagent.memory.MemoryException;
import io.github.sukimiya.agileagent.memory.MemoryException.ErrorCode;
import io.github.sukimiya.agileagent.memory.MemoryProperties;
import io.github.sukimiya.agileagent.memory.MemoryScope;
import io.github.sukimiya.agileagent.memory.judgement.ValidatedAction.SourceRef;
import io.github.sukimiya.agileagent.memory.judgement.WriteGovernance;
import io.github.sukimiya.agileagent.memory.retention.MemoryAudit;
import io.github.sukimiya.agileagent.memory.retention.RecallReferences;
import io.github.sukimiya.agileagent.memory.retention.RetentionTiers;
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

    /**
     * An item's effective recall reference count, the retention its tier gives, and when it becomes purgeable
     * (null unless its source was deleted and it is no longer ACTIVE).
     */
    public record RecallCount(String itemId, long count, Duration retention, LocalDateTime purgeEligibleAt) {}

    private static final String COLUMNS = "id, tenant_id, subject, conversation_id, item_type, content, status,"
            + " assertion, importance, confidence, valid_until, last_recalled_at, superseded_by, rules_version,"
            + " judge_model, reason, revision, created_at, updated_at";

    private final DatabaseClient db;
    private final TransactionalOperator transactions;
    private final MemoryItemRepository items;
    private final MemoryItemSourceRepository sources;
    private final EntityGraphRepository graph;
    private final ProfileService profiles;
    private final RetentionTiers tiers;
    private final RecallReferences references;
    private final MemoryAudit audit;

    public MemoryItemService(DatabaseClient db, ConnectionFactory connectionFactory, MemoryItemRepository items,
            MemoryItemSourceRepository sources, EntityGraphRepository graph, ProfileService profiles,
            MemoryProperties props) {
        this.tiers = RetentionTiers.of(props.getPurge().getTiers());
        this.references = new RecallReferences(db);
        this.audit = new MemoryAudit(db);
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
     * Deletes an item (soft): status DELETED and {@code is_valid = 0} with its text kept in the database, its
     * provenance and derived relations invalidated, its recall explanations hidden (they still count as
     * references), and a kept profile snapshot re-rendered without it.
     */
    public Mono<Void> delete(MemoryScope scope, String id) {
        return visibleOrNotFound(scope, id).flatMap(item -> db.sql("UPDATE memory_items SET status = 'DELETED',"
                                + " is_valid = 0, revision = revision + 1, updated_at = CURRENT_TIMESTAMP(6)"
                                + " WHERE id = :id AND tenant_id = :tenantId AND subject = :subject")
                        .bind("id", id).bind("tenantId", scope.tenantId()).bind("subject", scope.subject())
                        .fetch().rowsUpdated()
                        .then(db.sql("UPDATE memory_item_sources SET is_valid = 0 WHERE item_id = :id").bind("id", id)
                                .fetch().rowsUpdated())
                        .then(db.sql("UPDATE memory_relations SET status = 'INACTIVE', is_valid = 0"
                                        + " WHERE item_id = :id AND tenant_id = :tenantId AND subject = :subject")
                                .bind("id", id).bind("tenantId", scope.tenantId()).bind("subject", scope.subject())
                                .fetch().rowsUpdated())
                        .then(db.sql("UPDATE memory_recall_events SET is_valid = 0 WHERE kind = 'ITEM'"
                                        + " AND ref_id = :id AND tenant_id = :tenantId AND subject = :subject")
                                .bind("id", id).bind("tenantId", scope.tenantId()).bind("subject", scope.subject())
                                .fetch().rowsUpdated())
                        .as(transactions::transactional)
                        .then(profiles.refreshSnapshot(scope)));
    }

    /**
     * Deletes the profile snapshot and the whole entity graph of the caller (soft: rows kept with
     * {@code is_valid = 0}; their PROFILE / RELATION recall explanations hidden). Items stay; a later rebuild
     * (switch on) derives a new snapshot from them.
     */
    public Mono<Void> deleteProfile(MemoryScope scope) {
        return graph.invalidateAllForScope(scope)
                .then(db.sql("UPDATE memory_recall_events SET is_valid = 0 WHERE kind IN ('PROFILE', 'RELATION')"
                                + " AND tenant_id = :tenantId AND subject = :subject")
                        .bind("tenantId", scope.tenantId()).bind("subject", scope.subject())
                        .fetch().rowsUpdated())
                .as(transactions::transactional)
                .then(profiles.softDelete(scope));
    }

    /** The item's effective recall reference count and what it means for retention. */
    public Mono<RecallCount> recallCount(MemoryScope scope, String id) {
        return visibleOrNotFound(scope, id).flatMap(item -> state(scope, item.id()));
    }

    /**
     * Manually lowers an item's recall reference count by {@code amount} (at least 1), floored at 0: the
     * effective count is {@code max(0, references - recall_ref_offset)} and the offset grows by what was
     * actually removed, so later recalls count again from the floor. Audited in {@code memory_audit_log}
     * ({@code actor}, before/after, database time). The retention tier and purge eligibility follow at once.
     */
    public Mono<RecallCount> decrementRecallCount(MemoryScope scope, String id, long amount, String actor) {
        if (amount < 1) {
            return Mono.error(new MemoryException(ErrorCode.INVALID_ARGUMENT));
        }
        return visibleOrNotFound(scope, id).flatMap(item -> Mono.defer(() -> loadOffset(scope, item.id())
                        .zipWith(references.itemReferences(scope, item.id()))
                        .flatMap(t -> {
                            long offset = t.getT1();
                            long before = Math.max(0, t.getT2() - offset);
                            long applied = Math.min(amount, before);
                            return db.sql("UPDATE memory_items SET recall_ref_offset = :next,"
                                            + " updated_at = CURRENT_TIMESTAMP(6) WHERE id = :id"
                                            + " AND tenant_id = :tenantId AND subject = :subject"
                                            + " AND recall_ref_offset = :offset")
                                    .bind("next", offset + applied).bind("id", item.id())
                                    .bind("tenantId", scope.tenantId()).bind("subject", scope.subject())
                                    .bind("offset", offset)
                                    .fetch().rowsUpdated()
                                    .flatMap(n -> n == 1
                                            ? audit.record(scope, actor, "RECALL_COUNT_DECREMENT", "ITEM", item.id(),
                                                    amount, before, before - applied)
                                            : Mono.error(new MemoryException(ErrorCode.MEMORY_ITEM_CONFLICT)));
                        })
                        .as(transactions::transactional))
                .then(state(scope, item.id())));
    }

    /** Statuses never purged: live, user-deleted and forgotten items. */
    private static final java.util.Set<String> KEPT = java.util.Set.of("ACTIVE", "DELETED", "FORGOTTEN");

    /**
     * Forgets the given items (in-chat forget): each still ACTIVE and valid item becomes FORGOTTEN (not a delete:
     * it stays listed and restorable, leaves recall and the profile and is never purged), audited as
     * {@code action} by {@code actor}. Items that changed meanwhile are skipped. Returns the forgotten items, in
     * the given order; a kept profile snapshot is re-rendered without them.
     */
    public Mono<List<MemoryItem>> forget(MemoryScope scope, List<String> ids, String actor, String action) {
        return transition(scope, ids, actor, action, "'FORGOTTEN'", "ACTIVE");
    }

    /**
     * Restores forgotten items (in chat or {@link #restore}): FORGOTTEN becomes ACTIVE again, or INACTIVE when
     * its source conversation was deleted meanwhile (then the usual purge tiers apply). Audited like
     * {@link #forget}.
     */
    public Mono<List<MemoryItem>> restoreAll(MemoryScope scope, List<String> ids, String actor, String action) {
        return transition(scope, ids, actor, action,
                "CASE WHEN source_deleted_at IS NULL THEN 'ACTIVE' ELSE 'INACTIVE' END", "FORGOTTEN");
    }

    /**
     * {@code POST /api/memory/items/{id}/restore}: a missing, deleted or other owner's item is
     * {@code MEMORY_ITEM_NOT_FOUND}; an item that is not FORGOTTEN is {@code MEMORY_ITEM_CONFLICT}.
     */
    public Mono<MemoryItem> restore(MemoryScope scope, String id, String actor) {
        return visibleOrNotFound(scope, id).flatMap(item -> item.status() != ItemStatus.FORGOTTEN
                ? Mono.<MemoryItem>error(new MemoryException(ErrorCode.MEMORY_ITEM_CONFLICT))
                : restoreAll(scope, List.of(item.id()), actor, "RESTORE").flatMap(list -> list.isEmpty()
                        ? Mono.<MemoryItem>error(new MemoryException(ErrorCode.MEMORY_ITEM_CONFLICT))
                        : Mono.just(list.getFirst())));
    }

    private Mono<List<MemoryItem>> transition(MemoryScope scope, List<String> ids, String actor, String action,
            String newStatus, String fromStatus) {
        return reactor.core.publisher.Flux.fromIterable(ids)
                .concatMap(id -> db.sql("UPDATE memory_items SET status = " + newStatus + ","
                                + " revision = revision + 1, updated_at = CURRENT_TIMESTAMP(6)"
                                + " WHERE id = :id AND tenant_id = :tenantId AND subject = :subject"
                                + " AND status = :from AND is_valid = 1")
                        .bind("id", id).bind("tenantId", scope.tenantId()).bind("subject", scope.subject())
                        .bind("from", fromStatus)
                        .fetch().rowsUpdated()
                        .flatMap(n -> n == 1
                                ? audit.record(scope, actor, action, "ITEM", id)
                                        .then(items.findById(scope, id)).flatMap(Mono::justOrEmpty)
                                : Mono.<MemoryItem>empty()))
                .collectList()
                .as(transactions::transactional)
                .flatMap(changed -> changed.isEmpty() ? Mono.just(changed)
                        : profiles.refreshSnapshot(scope).thenReturn(changed));
    }

    private Mono<Long> loadOffset(MemoryScope scope, String id) {
        return db.sql("SELECT recall_ref_offset FROM memory_items WHERE id = :id AND tenant_id = :tenantId"
                        + " AND subject = :subject")
                .bind("id", id).bind("tenantId", scope.tenantId()).bind("subject", scope.subject())
                .map((row, meta) -> ((Number) row.get("recall_ref_offset")).longValue())
                .one();
    }

    private Mono<RecallCount> state(MemoryScope scope, String id) {
        return references.itemCount(scope, id).flatMap(count -> {
            var retention = tiers.retentionFor(count);
            return db.sql("SELECT status, source_deleted_at FROM memory_items WHERE id = :id"
                            + " AND tenant_id = :tenantId AND subject = :subject")
                    .bind("id", id).bind("tenantId", scope.tenantId()).bind("subject", scope.subject())
                    .map((row, meta) -> {
                        var deletedAt = row.get("source_deleted_at", LocalDateTime.class);
                        boolean candidate = deletedAt != null
                                && !KEPT.contains(row.get("status", String.class));
                        return new RecallCount(id, count, retention, candidate ? deletedAt.plus(retention) : null);
                    })
                    .one();
        });
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
