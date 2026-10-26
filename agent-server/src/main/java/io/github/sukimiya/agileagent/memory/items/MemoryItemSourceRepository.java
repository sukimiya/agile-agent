package io.github.sukimiya.agileagent.memory.items;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Repository;

import io.github.sukimiya.agileagent.memory.MemoryScope;
import io.github.sukimiya.agileagent.memory.SourceType;
import io.github.sukimiya.agileagent.memory.judgement.ValidatedAction.SourceRef;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/** Provenance rows in {@code memory_item_sources}. */
@Repository
public class MemoryItemSourceRepository {

    private final DatabaseClient db;

    public MemoryItemSourceRepository(DatabaseClient db) {
        this.db = db;
    }

    public Mono<Void> saveAll(String itemId, Iterable<SourceRef> sources) {
        return Flux.fromIterable(sources)
                .concatMap(s -> db.sql("INSERT INTO memory_item_sources (item_id, message_id, conversation_id,"
                                + " source_type, evidence_excerpt) VALUES (:itemId, :messageId, :conversationId,"
                                + " :sourceType, :excerpt)")
                        .bind("itemId", itemId).bind("messageId", s.messageId())
                        .bind("conversationId", s.conversationId()).bind("sourceType", s.sourceType().name())
                        .bind("excerpt", s.evidenceExcerpt() == null ? "" : s.evidenceExcerpt())
                        .fetch().rowsUpdated()
                        .onErrorResume(DuplicateKeyException.class, e -> Mono.just(0L)))
                .then();
    }

    public Flux<SourceRef> findByItem(String itemId) {
        return db.sql("SELECT message_id, conversation_id, source_type, evidence_excerpt FROM memory_item_sources"
                        + " WHERE item_id = :itemId")
                .bind("itemId", itemId)
                .map((row, meta) -> new SourceRef(row.get("message_id", String.class),
                        row.get("conversation_id", String.class),
                        SourceType.valueOf(row.get("source_type", String.class)),
                        row.get("evidence_excerpt", String.class)))
                .all();
    }

    /** Removes sources whose messages belong to {@code conversationId}; returns affected item ids. */
    public Flux<String> deleteByConversation(MemoryScope scope, String conversationId) {
        return db.sql("SELECT DISTINCT s.item_id FROM memory_item_sources s"
                        + " JOIN memory_items i ON i.id = s.item_id"
                        + " WHERE s.conversation_id = :c AND i.tenant_id = :tenantId AND i.subject = :subject")
                .bind("c", conversationId).bind("tenantId", scope.tenantId()).bind("subject", scope.subject())
                .map((row, meta) -> row.get("item_id", String.class))
                .all()
                .collectList()
                .flatMapMany(ids -> Flux.fromIterable(ids)
                        .concatMap(itemId -> db.sql("DELETE FROM memory_item_sources WHERE conversation_id = :c"
                                        + " AND item_id = :itemId")
                                .bind("c", conversationId).bind("itemId", itemId)
                                .fetch().rowsUpdated()
                                .thenReturn(itemId)));
    }

    public Mono<Long> countForItem(String itemId) {
        return db.sql("SELECT COUNT(*) AS n FROM memory_item_sources WHERE item_id = :itemId")
                .bind("itemId", itemId)
                .map((row, meta) -> ((Number) row.get("n")).longValue())
                .one()
                .defaultIfEmpty(0L);
    }
}
