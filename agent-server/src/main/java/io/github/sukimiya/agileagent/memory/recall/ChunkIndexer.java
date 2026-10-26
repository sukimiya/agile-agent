package io.github.sukimiya.agileagent.memory.recall;

import java.util.ArrayList;
import java.util.List;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.r2dbc.connection.R2dbcTransactionManager;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.reactive.TransactionalOperator;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.github.sukimiya.agileagent.memory.MemoryProperties;
import io.github.sukimiya.agileagent.memory.MemoryScope;
import io.github.sukimiya.agileagent.memory.SourceType;
import io.github.sukimiya.agileagent.memory.jobs.ClaimedJob;
import io.github.sukimiya.agileagent.memory.jobs.JobType;
import io.github.sukimiya.agileagent.memory.jobs.MemoryJobWorker;
import io.github.sukimiya.agileagent.memory.recall.ChunkRepository.TurnText;
import io.r2dbc.spi.ConnectionFactory;
import reactor.core.publisher.Mono;

/**
 * The {@link JobType#INDEX_CHUNKS} handler (M2a Task 5, D6): cuts one completed turn into original-text chunks.
 * <ul>
 * <li>One chunk per turn, {@code 用户：…\n助手：…} (user message and final answer; tool traffic is not indexed),
 * split by paragraph, and hard within a paragraph, when longer than {@code app.memory.recall.chunk-max-tokens}
 * estimated tokens.</li>
 * <li>Each chunk carries its codes ({@link EntityTerms#codes}) as a JSON array for exact matching, its source
 * types, and links to the neighbouring chunks of the conversation (both ways, also across turns).</li>
 * <li>Idempotent: a turn that already has chunks, is not COMPLETED, or whose conversation is deleted is a no-op
 * (false). The session row is locked first, the same order as the deletion transaction, and the whole turn
 * commits together with {@code memory_index_progress.indexed_through_sequence}.</li>
 * </ul>
 */
@Component
public class ChunkIndexer {

    private static final ObjectMapper JSON = new ObjectMapper();

    private final DatabaseClient db;
    private final ChunkRepository chunks;
    private final MemoryProperties props;
    private final TransactionalOperator transactions;

    public ChunkIndexer(DatabaseClient db, ConnectionFactory connectionFactory, ChunkRepository chunks,
            MemoryJobWorker worker, MemoryProperties props) {
        this.db = db;
        this.chunks = chunks;
        this.props = props;
        this.transactions = TransactionalOperator.create(new R2dbcTransactionManager(connectionFactory));
        worker.register(JobType.INDEX_CHUNKS, this::handle);
    }

    private Mono<Void> handle(ClaimedJob job) {
        return index(job.scope(), job.conversationId(), turnIdOf(job)).then();
    }

    private static String turnIdOf(ClaimedJob job) {
        if (job.payload() != null) {
            try {
                var node = JSON.readTree(job.payload()).get("turnId");
                if (node != null && node.isTextual()) {
                    return node.asText();
                }
            } catch (JsonProcessingException e) {
                // fall back to the dedupe key, which is the turn id
            }
        }
        return job.dedupeKey();
    }

    /** Indexes one turn; true when chunks were written. */
    public Mono<Boolean> index(MemoryScope scope, String conversationId, String turnId) {
        return transactions.transactional(Mono.defer(() -> lockLiveSession(scope, conversationId)
                        .flatMap(live -> !live ? Mono.just(false) : hasChunks(turnId)
                                .flatMap(done -> done ? Mono.just(false)
                                        : chunks.turnTexts(scope, List.of(conversationId), List.of(turnId)).next()
                                                .flatMap(turn -> write(scope, turn))
                                                .defaultIfEmpty(false)))))
                .onErrorResume(DuplicateKeyException.class, e -> Mono.just(false));
    }

    private Mono<Boolean> lockLiveSession(MemoryScope scope, String conversationId) {
        return db.sql("SELECT id FROM sessions WHERE id = :c AND tenant_id = :tenantId AND user_id = :subject"
                        + " AND deleted_at IS NULL FOR UPDATE")
                .bind("c", conversationId).bind("tenantId", scope.tenantId()).bind("subject", scope.subject())
                .map(row -> row.get("id", String.class))
                .all().hasElements();
    }

    private Mono<Boolean> hasChunks(String turnId) {
        return db.sql("SELECT id FROM memory_chunks WHERE turn_id = :turn LIMIT 1").bind("turn", turnId)
                .map(row -> row.get("id", String.class)).all().hasElements();
    }

    /** A chunk before it is stored. */
    record Piece(String id, long fromSequence, long toSequence, String content, String sourceTypes,
            String entitiesJson) {}

    private Mono<Boolean> write(MemoryScope scope, TurnText turn) {
        var pieces = pieces(turn, props.getRecall().getChunkMaxTokens());
        var conversation = turn.conversationId();
        Mono<String> prev = db.sql("SELECT id FROM memory_chunks WHERE conversation_id = :c AND from_sequence < :f"
                        + " ORDER BY from_sequence DESC, id DESC LIMIT 1")
                .bind("c", conversation).bind("f", turn.fromSequence())
                .map(row -> row.get("id", String.class)).one();
        Mono<String> next = db.sql("SELECT id FROM memory_chunks WHERE conversation_id = :c AND from_sequence > :t"
                        + " ORDER BY from_sequence, id LIMIT 1")
                .bind("c", conversation).bind("t", turn.toSequence())
                .map(row -> row.get("id", String.class)).one();
        return prev.defaultIfEmpty("").zipWith(next.defaultIfEmpty("")).flatMap(neighbours -> {
            var before = neighbours.getT1().isEmpty() ? null : neighbours.getT1();
            var after = neighbours.getT2().isEmpty() ? null : neighbours.getT2();
            Mono<Void> inserts = Mono.empty();
            for (int i = 0; i < pieces.size(); i++) {
                var piece = pieces.get(i);
                var p = i == 0 ? before : pieces.get(i - 1).id();
                var n = i == pieces.size() - 1 ? after : pieces.get(i + 1).id();
                inserts = inserts.then(insert(scope, conversation, turn.turnId(), piece, p, n));
            }
            return inserts
                    .then(before == null ? Mono.empty() : link("next_chunk_id", before, pieces.getFirst().id()))
                    .then(after == null ? Mono.empty() : link("prev_chunk_id", after, pieces.getLast().id()))
                    .then(progress(scope, conversation, turn.toSequence()))
                    .thenReturn(true);
        });
    }

    private Mono<Void> insert(MemoryScope scope, String conversation, String turnId, Piece piece, String prev,
            String next) {
        var spec = db.sql("INSERT INTO memory_chunks (id, tenant_id, subject, conversation_id, turn_id, from_sequence,"
                        + " to_sequence, prev_chunk_id, next_chunk_id, content, source_types, entities_json, created_at)"
                        + " VALUES (:id, :tenantId, :subject, :c, :turn, :f, :t, :prev, :next, :content, :sources,"
                        + " :entities, CURRENT_TIMESTAMP(6))")
                .bind("id", piece.id()).bind("tenantId", scope.tenantId()).bind("subject", scope.subject())
                .bind("c", conversation).bind("turn", turnId).bind("f", piece.fromSequence())
                .bind("t", piece.toSequence()).bind("content", piece.content()).bind("sources", piece.sourceTypes())
                .bind("entities", piece.entitiesJson());
        spec = prev == null ? spec.bindNull("prev", String.class) : spec.bind("prev", prev);
        spec = next == null ? spec.bindNull("next", String.class) : spec.bind("next", next);
        return spec.fetch().rowsUpdated().then();
    }

    private Mono<Void> link(String column, String id, String target) {
        return db.sql("UPDATE memory_chunks SET " + column + " = :target WHERE id = :id")
                .bind("target", target).bind("id", id).fetch().rowsUpdated().then();
    }

    private Mono<Void> progress(MemoryScope scope, String conversation, long through) {
        var update = db.sql("UPDATE memory_index_progress SET indexed_through_sequence ="
                        + " GREATEST(indexed_through_sequence, :through), updated_at = CURRENT_TIMESTAMP(6)"
                        + " WHERE conversation_id = :c AND tenant_id = :tenantId AND subject = :subject")
                .bind("through", through).bind("c", conversation).bind("tenantId", scope.tenantId())
                .bind("subject", scope.subject())
                .fetch().rowsUpdated();
        return update.flatMap(n -> n > 0 ? Mono.<Void>empty() : db.sql("INSERT INTO memory_index_progress"
                        + " (conversation_id, tenant_id, subject, indexed_through_sequence, summarized_through_sequence,"
                        + " updated_at) VALUES (:c, :tenantId, :subject, :through, 0, CURRENT_TIMESTAMP(6))")
                .bind("c", conversation).bind("tenantId", scope.tenantId()).bind("subject", scope.subject())
                .bind("through", through)
                .fetch().rowsUpdated().then());
    }

    /** The chunks of one turn: the whole turn when it fits, else paragraphs packed up to {@code maxTokens}. */
    static List<Piece> pieces(TurnText turn, int maxTokens) {
        var whole = turn.content();
        var sources = turn.answer() == null ? SourceType.USER_INPUT.name()
                : SourceType.USER_INPUT.name() + "," + SourceType.ASSISTANT.name();
        if (Cost.of(whole) <= maxTokens) {
            return List.of(new Piece(id(turn, 0), turn.fromSequence(), turn.toSequence(), whole, sources,
                    entities(whole)));
        }
        // Paragraphs, each tagged with whether it belongs to the user message.
        var paragraphs = new ArrayList<String>();
        var fromUser = new ArrayList<Boolean>();
        split("用户：" + turn.user(), true, maxTokens, paragraphs, fromUser);
        if (turn.answer() != null) {
            split("助手：" + turn.answer(), false, maxTokens, paragraphs, fromUser);
        }
        var out = new ArrayList<Piece>();
        var current = new StringBuilder();
        boolean hasUser = false;
        boolean hasAnswer = false;
        for (int i = 0; i < paragraphs.size(); i++) {
            var paragraph = paragraphs.get(i);
            var candidate = current.isEmpty() ? paragraph : current + "\n" + paragraph;
            if (!current.isEmpty() && Cost.of(candidate) > maxTokens) {
                out.add(piece(turn, out.size(), current.toString(), hasUser, hasAnswer));
                current.setLength(0);
                hasUser = false;
                hasAnswer = false;
                candidate = paragraph;
            }
            current.setLength(0);
            current.append(candidate);
            hasUser |= fromUser.get(i);
            hasAnswer |= !fromUser.get(i);
        }
        if (!current.isEmpty()) {
            out.add(piece(turn, out.size(), current.toString(), hasUser, hasAnswer));
        }
        return out;
    }

    private static Piece piece(TurnText turn, int index, String content, boolean user, boolean answer) {
        var sources = new ArrayList<String>();
        if (user) {
            sources.add(SourceType.USER_INPUT.name());
        }
        if (answer) {
            sources.add(SourceType.ASSISTANT.name());
        }
        return new Piece(id(turn, index), user ? turn.fromSequence() : turn.answerSequence(), turn.toSequence(),
                content, String.join(",", sources), entities(content));
    }

    /** Deterministic ids: the turn id and the chunk's position, so chunks sort in order and repeats collide. */
    private static String id(TurnText turn, int index) {
        return turn.turnId() + ":" + String.format("%03d", index);
    }

    private static void split(String text, boolean user, int maxTokens, List<String> out, List<Boolean> tags) {
        for (var paragraph : text.split("\n\\s*\n")) {
            if (paragraph.isBlank()) {
                continue;
            }
            for (var part : hardSplit(paragraph.strip(), maxTokens)) {
                out.add(part);
                tags.add(user);
            }
        }
    }

    /** Cuts a paragraph longer than {@code maxTokens} at code-point boundaries. */
    static List<String> hardSplit(String text, int maxTokens) {
        if (Cost.of(text) <= maxTokens) {
            return List.of(text);
        }
        var out = new ArrayList<String>();
        var cost = new Cost();
        var current = new StringBuilder();
        for (int i = 0; i < text.length(); ) {
            int cp = text.codePointAt(i);
            i += Character.charCount(cp);
            if (!cost.fits(cp, maxTokens) && !current.isEmpty()) {
                out.add(current.toString());
                current.setLength(0);
                cost = new Cost();
            }
            cost.add(cp);
            current.appendCodePoint(cp);
        }
        if (!current.isEmpty()) {
            out.add(current.toString());
        }
        return out;
    }

    private static String entities(String content) {
        try {
            return JSON.writeValueAsString(EntityTerms.codes(content));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }

    /** Incremental {@link io.github.sukimiya.agileagent.memory.context.TokenEstimator} for splitting. */
    static final class Cost {
        private long cjk;
        private long other;

        static long of(String text) {
            return io.github.sukimiya.agileagent.memory.context.TokenEstimator.estimate(text);
        }

        void add(int cp) {
            if (Character.isWhitespace(cp)) {
                return;
            }
            if (isCjk(cp)) {
                cjk++;
            } else {
                other++;
            }
        }

        boolean fits(int cp, int max) {
            long c = cjk;
            long o = other;
            if (!Character.isWhitespace(cp)) {
                if (isCjk(cp)) {
                    c++;
                } else {
                    o++;
                }
            }
            return c + (o + 3) / 4 <= max;
        }

        private static boolean isCjk(int cp) {
            var script = Character.UnicodeScript.of(cp);
            return script == Character.UnicodeScript.HAN || script == Character.UnicodeScript.HIRAGANA
                    || script == Character.UnicodeScript.KATAKANA || script == Character.UnicodeScript.HANGUL
                    || (cp >= 0x3000 && cp <= 0x303F) || (cp >= 0xFF00 && cp <= 0xFFEF);
        }
    }
}
