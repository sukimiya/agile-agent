package io.github.sukimiya.agileagent.memory.recall;

import java.util.List;
import java.util.Locale;

import org.springframework.r2dbc.core.DatabaseClient;

import io.github.sukimiya.agileagent.memory.MemoryScope;
import reactor.core.publisher.Flux;

/**
 * Full-text candidate search over chunk contents and record search texts, scoped to the owner's live
 * conversations. Candidates only: {@link MemoryRetriever} re-scores them on the query terms and drops weak hits,
 * so both implementations give the same results for the same data. MySQL: ngram FULLTEXT in natural-language
 * mode (D7); H2: {@code LIKE} on each term (D8, development and tests).
 */
public interface FullTextSearch {

    /** A candidate: the matched text of a chunk ({@code chunkId} set) or a record ({@code chunkId} null). */
    record Hit(String chunkId, String turnId, String conversationId, long fromSequence, long toSequence,
            String text) {}

    Flux<Hit> chunks(MemoryScope scope, List<String> conversationIds, List<String> terms, int limit);

    Flux<Hit> records(MemoryScope scope, List<String> conversationIds, List<String> terms, int limit);

    static FullTextSearch forDatabase(DatabaseClient db) {
        var name = db.getConnectionFactory().getMetadata().getName().toLowerCase(Locale.ROOT);
        return name.contains("mysql") ? new MySqlFullTextSearch(db) : new LikeFullTextSearch(db);
    }
}
