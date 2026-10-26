package io.github.sukimiya.agileagent.memory.recall;

import java.util.List;

import org.springframework.r2dbc.core.DatabaseClient;

import io.github.sukimiya.agileagent.memory.MemoryScope;
import reactor.core.publisher.Flux;

/**
 * MySQL 8.4: {@code MATCH ... AGAINST (... IN NATURAL LANGUAGE MODE)} on the ngram FULLTEXT indexes
 * {@code ft_memory_chunks_content} and {@code ft_memory_summaries_search_text} (D7, {@code ngram_token_size=2}).
 * The query is the term list, so the ranking uses the same bigrams the retriever scores on.
 */
public class MySqlFullTextSearch implements FullTextSearch {

    private final DatabaseClient db;

    public MySqlFullTextSearch(DatabaseClient db) {
        this.db = db;
    }

    @Override
    public Flux<Hit> chunks(MemoryScope scope, List<String> conversationIds, List<String> terms, int limit) {
        if (conversationIds.isEmpty() || terms.isEmpty()) {
            return Flux.empty();
        }
        var match = "MATCH (c.content) AGAINST (:query IN NATURAL LANGUAGE MODE)";
        return db.sql("SELECT c.id, c.turn_id, c.conversation_id, c.from_sequence, c.to_sequence, c.content, "
                        + match + " AS score FROM memory_chunks c" + ChunkRepository.LIVE_SESSION.formatted("c")
                        + " WHERE c.tenant_id = :tenantId AND c.subject = :subject AND c.conversation_id IN (:ids)"
                        + " AND " + match + " > 0 ORDER BY score DESC, c.to_sequence DESC LIMIT :limit")
                .bind("tenantId", scope.tenantId()).bind("subject", scope.subject())
                .bind("ids", List.copyOf(conversationIds)).bind("query", String.join(" ", terms))
                .bind("limit", limit)
                .map(row -> new Hit(row.get("id", String.class), row.get("turn_id", String.class),
                        row.get("conversation_id", String.class), ChunkRepository.number(row.get("from_sequence")),
                        ChunkRepository.number(row.get("to_sequence")), row.get("content", String.class)))
                .all();
    }

    @Override
    public Flux<Hit> records(MemoryScope scope, List<String> conversationIds, List<String> terms, int limit) {
        if (conversationIds.isEmpty() || terms.isEmpty()) {
            return Flux.empty();
        }
        var match = "MATCH (s.search_text) AGAINST (:query IN NATURAL LANGUAGE MODE)";
        return db.sql("SELECT s.turn_id, s.conversation_id, s.from_sequence, s.to_sequence, s.search_text, "
                        + match + " AS score FROM memory_summaries s" + ChunkRepository.LIVE_SESSION.formatted("s")
                        + " WHERE s.tenant_id = :tenantId AND s.subject = :subject AND s.conversation_id IN (:ids)"
                        + " AND s.superseded_at IS NULL AND " + match + " > 0"
                        + " ORDER BY score DESC, s.to_sequence DESC LIMIT :limit")
                .bind("tenantId", scope.tenantId()).bind("subject", scope.subject())
                .bind("ids", List.copyOf(conversationIds)).bind("query", String.join(" ", terms))
                .bind("limit", limit)
                .map(row -> new Hit(null, row.get("turn_id", String.class), row.get("conversation_id", String.class),
                        ChunkRepository.number(row.get("from_sequence")),
                        ChunkRepository.number(row.get("to_sequence")), row.get("search_text", String.class)))
                .all();
    }
}
