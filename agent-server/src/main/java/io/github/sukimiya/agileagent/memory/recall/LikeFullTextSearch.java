package io.github.sukimiya.agileagent.memory.recall;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.springframework.r2dbc.core.DatabaseClient;

import io.github.sukimiya.agileagent.memory.MemoryScope;
import reactor.core.publisher.Flux;

/**
 * Databases without ngram FULLTEXT (H2 for development and tests, D8): a row is a candidate when it contains any
 * query term ({@code LIKE}, case-insensitive). Slower than FULLTEXT and not meant for production volumes.
 */
public class LikeFullTextSearch implements FullTextSearch {

    private final DatabaseClient db;

    public LikeFullTextSearch(DatabaseClient db) {
        this.db = db;
    }

    private static String anyTerm(String column, int count) {
        return IntStream.range(0, count).mapToObj(i -> "LOWER(" + column + ") LIKE :t" + i + " ESCAPE '!'")
                .collect(Collectors.joining(" OR ", "(", ")"));
    }

    private static DatabaseClient.GenericExecuteSpec bindTerms(DatabaseClient.GenericExecuteSpec spec,
            List<String> terms) {
        for (int i = 0; i < terms.size(); i++) {
            spec = spec.bind("t" + i, "%" + ChunkRepository.escapeLike(terms.get(i)) + "%");
        }
        return spec;
    }

    @Override
    public Flux<Hit> chunks(MemoryScope scope, List<String> conversationIds, List<String> terms, int limit) {
        if (conversationIds.isEmpty() || terms.isEmpty()) {
            return Flux.empty();
        }
        var spec = db.sql("SELECT c.id, c.turn_id, c.conversation_id, c.from_sequence, c.to_sequence, c.content"
                        + " FROM memory_chunks c" + ChunkRepository.LIVE_SESSION.formatted("c")
                        + " WHERE c.tenant_id = :tenantId AND c.subject = :subject AND c.conversation_id IN (:ids)"
                        + " AND " + anyTerm("c.content", terms.size()) + " ORDER BY c.to_sequence DESC LIMIT :limit")
                .bind("tenantId", scope.tenantId()).bind("subject", scope.subject())
                .bind("ids", List.copyOf(conversationIds)).bind("limit", limit);
        return bindTerms(spec, terms)
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
        var spec = db.sql("SELECT s.turn_id, s.conversation_id, s.from_sequence, s.to_sequence, s.search_text"
                        + " FROM memory_summaries s" + ChunkRepository.LIVE_SESSION.formatted("s")
                        + " WHERE s.tenant_id = :tenantId AND s.subject = :subject AND s.conversation_id IN (:ids)"
                        + " AND s.superseded_at IS NULL AND " + anyTerm("s.search_text", terms.size())
                        + " ORDER BY s.to_sequence DESC LIMIT :limit")
                .bind("tenantId", scope.tenantId()).bind("subject", scope.subject())
                .bind("ids", List.copyOf(conversationIds)).bind("limit", limit);
        return bindTerms(spec, terms)
                .map(row -> new Hit(null, row.get("turn_id", String.class), row.get("conversation_id", String.class),
                        ChunkRepository.number(row.get("from_sequence")),
                        ChunkRepository.number(row.get("to_sequence")), row.get("search_text", String.class)))
                .all();
    }
}
