package io.github.sukimiya.agileagent.memory.retention;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.SmartLifecycle;
import org.springframework.core.NestedExceptionUtils;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Component;

import io.github.sukimiya.agileagent.memory.MemoryMetrics;
import io.github.sukimiya.agileagent.memory.MemoryProperties;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Periodic purge of derived memory index rows whose source was soft-deleted (M2b Q4 redesign C).
 * <ul>
 * <li>Memory items that are no longer ACTIVE and carry {@code source_deleted_at} (their conversation, or their
 * last valid source, was deleted): the item with its sources and relations.</li>
 * <li>Recall chunks and turn records ({@code is_valid = 0}) of deleted conversations.</li>
 * </ul>
 * A candidate is purged once {@link RetentionTiers#retentionFor} of its recall reference count has passed since
 * the source was deleted (database time). "Purge" is a physical delete of these derived rows only: original
 * conversation data (sessions, turns, messages, tool records, item sources' evidence of live items, recall
 * records) is never purged, only soft-deleted. Runs every {@code app.memory.purge.interval} when
 * {@code app.memory.purge.enabled} and {@code app.memory.recovery-enabled} are set.
 */
@Component
public class MemoryIndexPurger implements SmartLifecycle {

    private static final Logger log = LoggerFactory.getLogger(MemoryIndexPurger.class);

    public record PurgeResult(int items, int chunks, int summaries, int expiredRecallRecords) {}

    private record Candidate(String id, long references, long ageMicros, LocalDateTime cursorTime) {}

    private final DatabaseClient db;
    private final MemoryProperties props;
    private final MemoryMetrics metrics;
    private final RetentionTiers tiers;
    private volatile Disposable schedule;

    public MemoryIndexPurger(DatabaseClient db, MemoryProperties props, MemoryMetrics metrics) {
        this.db = db;
        this.props = props;
        this.metrics = metrics;
        this.tiers = RetentionTiers.of(props.getPurge().getTiers());
    }

    public RetentionTiers tiers() {
        return tiers;
    }

    /** One full pass over all candidates. */
    public Mono<PurgeResult> purgeExpired() {
        return expireRecallRecords().flatMap(expired -> purgeItems().flatMap(items -> purgeChunks()
                        .flatMap(chunks -> purgeSummaries()
                                .map(summaries -> new PurgeResult(items, chunks, summaries, expired)))))
                .doOnNext(r -> {
                    if (metrics != null) {
                        metrics.recallRecordsExpired(r.expiredRecallRecords());
                        metrics.purged("ITEM", r.items());
                        metrics.purged("CHUNK", r.chunks());
                        metrics.purged("SUMMARY", r.summaries());
                    }
                    if (r.items() + r.chunks() + r.summaries() + r.expiredRecallRecords() > 0) {
                        log.info("memory.purge items={} chunks={} summaries={} expiredRecallRecords={}", r.items(),
                                r.chunks(), r.summaries(), r.expiredRecallRecords());
                    }
                });
    }

    /**
     * Marks recall records older than {@code recall-record-ttl} expired (never deleted), then caps each item's
     * manual offset at its remaining live references so later recalls count again from 0.
     */
    private Mono<Integer> expireRecallRecords() {
        long ttlMicros = Math.max(1, props.getPurge().getRecallRecordTtl().toNanos() / 1_000);
        return db.sql("UPDATE memory_recall_events SET expired_at = CURRENT_TIMESTAMP(6) WHERE expired_at IS NULL"
                        + " AND created_at <= TIMESTAMPADD(MICROSECOND, -" + ttlMicros + ", CURRENT_TIMESTAMP(6))")
                .fetch().rowsUpdated()
                .flatMap(n -> n == 0 ? Mono.just(0) : db.sql("UPDATE memory_items SET recall_ref_offset = "
                                + RecallReferences.itemReferencesSql("memory_items")
                                + " WHERE recall_ref_offset > 0 AND recall_ref_offset > "
                                + RecallReferences.itemReferencesSql("memory_items"))
                        .fetch().rowsUpdated().thenReturn(n.intValue()));
    }

    private long minAgeMicros() {
        return Math.max(0, tiers.shortest().toNanos() / 1_000);
    }

    private boolean due(Candidate c) {
        return c.ageMicros() >= tiers.retentionFor(c.references()).toNanos() / 1_000;
    }

    /** Never purged: live items, and items the user deleted or forgot (kept permanently). */
    static final String KEPT = "('ACTIVE', 'DELETED', 'FORGOTTEN')";

    private Mono<Integer> purgeItems() {
        String select = "SELECT i.id, i.source_deleted_at AS t, i.recall_ref_offset AS o, "
                + RecallReferences.itemReferencesSql("i") + " AS n,"
                + " TIMESTAMPDIFF(MICROSECOND, i.source_deleted_at, CURRENT_TIMESTAMP(6)) AS age_us"
                + " FROM memory_items i WHERE i.source_deleted_at IS NOT NULL AND i.status NOT IN " + KEPT
                + " AND i.source_deleted_at <= TIMESTAMPADD(MICROSECOND, -:minAge, CURRENT_TIMESTAMP(6))";
        return pages(select, "i.source_deleted_at", "i.id", true)
                .filter(this::due)
                .concatMap(c -> db.sql("DELETE FROM memory_relations WHERE item_id = :id").bind("id", c.id())
                        .fetch().rowsUpdated()
                        .then(db.sql("DELETE FROM memory_item_sources WHERE item_id = :id").bind("id", c.id())
                                .fetch().rowsUpdated())
                        .then(db.sql("DELETE FROM memory_items WHERE id = :id AND source_deleted_at IS NOT NULL"
                                        + " AND status NOT IN " + KEPT)
                                .bind("id", c.id()).fetch().rowsUpdated()))
                .reduce(0, (sum, n) -> sum + n.intValue());
    }

    private Mono<Integer> purgeChunks() {
        return purgeIndex("memory_chunks", "c", "CHUNK", "id");
    }

    private Mono<Integer> purgeSummaries() {
        return purgeIndex("memory_summaries", "c", "SUMMARY", "turn_id");
    }

    private Mono<Integer> purgeIndex(String table, String alias, String kind, String refColumn) {
        String select = "SELECT " + alias + ".id, se.deleted_at AS t, 0 AS o, "
                + RecallReferences.countSql(kind, alias, refColumn) + " AS n,"
                + " TIMESTAMPDIFF(MICROSECOND, se.deleted_at, CURRENT_TIMESTAMP(6)) AS age_us"
                + " FROM " + table + " " + alias + " JOIN sessions se ON se.id = " + alias + ".conversation_id"
                + " AND se.tenant_id = " + alias + ".tenant_id AND se.user_id = " + alias + ".subject"
                + " WHERE " + alias + ".is_valid = 0 AND se.is_valid = 0 AND se.deleted_at IS NOT NULL"
                + " AND se.deleted_at <= TIMESTAMPADD(MICROSECOND, -:minAge, CURRENT_TIMESTAMP(6))";
        return pages(select, null, alias + ".id", false)
                .filter(this::due)
                .concatMap(c -> db.sql("DELETE FROM " + table + " WHERE id = :id AND is_valid = 0").bind("id", c.id())
                        .fetch().rowsUpdated())
                .reduce(0, (sum, n) -> sum + n.intValue());
    }

    /** All candidates in keyset pages of {@code app.memory.purge.batch} (ordered by time then id, or by id). */
    private Flux<Candidate> pages(String select, String timeColumn, String idColumn, boolean byTime) {
        int batch = Math.max(1, props.getPurge().getBatch());
        return page(select, timeColumn, idColumn, byTime, batch, null)
                .expand(list -> list.size() < batch ? Mono.empty()
                        : page(select, timeColumn, idColumn, byTime, batch, list.getLast()))
                .concatMapIterable(list -> list);
    }

    private Mono<List<Candidate>> page(String select, String timeColumn, String idColumn, boolean byTime, int batch,
            Candidate after) {
        var sql = new StringBuilder(select);
        if (after != null) {
            sql.append(byTime
                    ? " AND (" + timeColumn + " > :afterT OR (" + timeColumn + " = :afterT AND " + idColumn
                            + " > :afterId))"
                    : " AND " + idColumn + " > :afterId");
        }
        sql.append(" ORDER BY ").append(byTime ? timeColumn + ", " : "").append(idColumn).append(" LIMIT ")
                .append(batch);
        var spec = db.sql(sql.toString()).bind("minAge", minAgeMicros());
        if (after != null) {
            spec = spec.bind("afterId", after.id());
            if (byTime) {
                spec = spec.bind("afterT", after.cursorTime());
            }
        }
        return spec.map((row, meta) -> new Candidate(row.get("id", String.class),
                        Math.max(0, ((Number) row.get("n")).longValue() - ((Number) row.get("o")).longValue()),
                        ((Number) row.get("age_us")).longValue(), row.get("t", LocalDateTime.class)))
                .all()
                .collectList();
    }

    @Override
    public void start() {
        Duration every = props.getPurge().getInterval();
        schedule = Flux.interval(every, every)
                .onBackpressureDrop()
                .concatMap(tick -> purgeExpired().onErrorResume(e -> {
                    log.warn("memory.purge failed: {}", NestedExceptionUtils.getMostSpecificCause(e).toString());
                    return Mono.empty();
                }))
                .subscribe();
    }

    @Override
    public void stop() {
        var current = schedule;
        if (current != null) {
            current.dispose();
        }
        schedule = null;
    }

    @Override
    public boolean isRunning() {
        var current = schedule;
        return current != null && !current.isDisposed();
    }

    @Override
    public boolean isAutoStartup() {
        return props.isRecoveryEnabled() && props.getPurge().isEnabled();
    }
}
