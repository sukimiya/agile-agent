package io.github.sukimiya.agileagent.memory.recall;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.function.Function;

import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.github.sukimiya.agileagent.memory.MemoryMetrics;
import io.github.sukimiya.agileagent.memory.MemoryModel.TurnTicket;
import io.github.sukimiya.agileagent.memory.MemoryProperties;
import io.github.sukimiya.agileagent.memory.MemoryScope;
import io.github.sukimiya.agileagent.memory.SourceType;
import io.github.sukimiya.agileagent.memory.recall.ChunkRepository.TurnText;
import io.github.sukimiya.agileagent.memory.items.EntityGraphRepository;
import io.github.sukimiya.agileagent.memory.items.MemoryItem;
import io.github.sukimiya.agileagent.memory.items.MemoryItemRepository;
import io.github.sukimiya.agileagent.memory.items.ProfileService;
import io.github.sukimiya.agileagent.memory.summary.SummaryRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Basic recall (M2a Task 5, D22): finds earlier turns relevant to the current question, without a model call.
 * <ol>
 * <li><b>Scope.</b> The current conversation; with cross-conversation recall switched on for the owner (read now,
 * never cached) also the owner's newest other live conversations, at most {@code max-conversations}. Never another
 * subject or tenant, never a deleted conversation (every read joins the live session).</li>
 * <li><b>Candidates.</b> {@code ENTITY_EXACT}: codes in the question ({@link EntityTerms#codes}) against chunk
 * codes and record texts, and record entity names contained in the question. {@code CHUNK_FULLTEXT} /
 * {@code RECORD_FULLTEXT}: full-text search ({@link FullTextSearch}) re-scored on the question's terms; a hit
 * with fewer than {@code min-matched-terms} distinct terms is weak and dropped. {@code RECENT_GAP}: completed
 * turns of the current conversation not indexed yet, the newest {@code backfill-turns}, scanned directly.</li>
 * <li><b>Merge.</b> Per turn, the highest score wins; best first, at most {@code candidate-limit}.</li>
 * <li><b>Expand.</b> Each turn becomes its original text (user message and final answer), re-read with the
 * owner and tombstone checked again. Records themselves are never returned.</li>
 * </ol>
 * {@code coverageLimited} when un-indexed turns exist beyond the back-fill or in other conversations in scope.
 * Results are not cached (spec 6.1). Which items fit the prompt is the {@code ContextAssembler}'s decision (D24).
 */
@Component
public class MemoryRetriever {

    private final RecallExclusions exclusions;

    static final String ENTITY_EXACT = "ENTITY_EXACT";
    static final String RECORD_FULLTEXT = "RECORD_FULLTEXT";
    static final String CHUNK_FULLTEXT = "CHUNK_FULLTEXT";
    static final String RECENT_GAP = "RECENT_GAP";
    /** Exact matches always rank above full-text hits. */
    private static final double EXACT_BASE = 100;
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final List<SourceType> TURN_SOURCES = List.of(SourceType.USER_INPUT, SourceType.ASSISTANT);

    private final DatabaseClient db;
    private final MemoryProperties props;
    private final MemorySettingsService settings;
    private final ChunkRepository chunks;
    private final SummaryRepository summaries;
    private final FullTextSearch fullText;
    private final MemoryMetrics metrics;
    private final MemoryItemRepository memoryItems;
    private final EntityGraphRepository entities;
    private final ProfileService profiles;

    public MemoryRetriever(DatabaseClient db, MemoryProperties props, MemorySettingsService settings,
            ChunkRepository chunks, SummaryRepository summaries, FullTextSearch fullText, MemoryMetrics metrics) {
        this(db, props, settings, chunks, summaries, fullText, metrics, null, null, null);
    }

    @Autowired
    public MemoryRetriever(DatabaseClient db, MemoryProperties props, MemorySettingsService settings,
            ChunkRepository chunks, SummaryRepository summaries, FullTextSearch fullText, MemoryMetrics metrics,
            MemoryItemRepository memoryItems, EntityGraphRepository entities, ProfileService profiles) {
        this.db = db;
        this.exclusions = new RecallExclusions(db);
        this.props = props;
        this.settings = settings;
        this.chunks = chunks;
        this.summaries = summaries;
        this.fullText = fullText;
        this.metrics = metrics;
        this.memoryItems = memoryItems;
        this.entities = entities;
        this.profiles = profiles;
        if (props.getRecall().isEnabled() && metrics != null) {
            metrics.enableRecall();
        }
    }

    public boolean enabled() {
        return props.getRecall().isEnabled();
    }

    /** At most this many recalled turns go into one prompt (D24). */
    public int maxItems() {
        return props.getRecall().getMaxItems();
    }

    /** Share of the history budget the recalled turns may take when there are hits (D24). */
    public double budgetShare() {
        return props.getRecall().getBudgetShare();
    }

    /** A hit before expansion. */
    private record Scored(String turnId, String conversationId, String chunkId, String reason, double score,
            String excerpt) {}

    public Mono<RecallResult> recall(TurnTicket ticket, String query) {
        if (!enabled() || query == null || query.isBlank()) {
            return Mono.just(RecallResult.empty());
        }
        return Mono.defer(() -> {
            long start = System.nanoTime();
            var scope = ticket.scope();
            var current = ticket.conversationId();
            var recall = props.getRecall();
            return settings.crossConversationEnabled(scope)
                    .flatMap(on -> !on ? Mono.just(List.of(current))
                            : chunks.otherLiveConversations(scope, current, recall.getMaxConversations())
                                    .collectList().map(others -> {
                                        var ids = new ArrayList<String>();
                                        ids.add(current);
                                        ids.addAll(others);
                                        return List.copyOf(ids);
                                    }))
                    .flatMap(ids -> search(scope, current, ids, query)
                            .flatMap(result -> enrich(scope, current, query, result)))
                    .map(result -> result.withLatency(Duration.ofNanos(System.nanoTime() - start)))
                    .doOnNext(result -> {
                        if (metrics != null) {
                            metrics.recalled(result.latency(), result.items().size(), result.coverageLimited());
                        }
                    });
        });
    }

    private Mono<RecallResult> search(MemoryScope scope, String current, List<String> ids, String query) {
        var recall = props.getRecall();
        int limit = recall.getCandidateLimit();
        var codes = EntityTerms.codes(query);
        var terms = EntityTerms.terms(query);
        int min = recall.getMinMatchedTerms();

        Flux<Scored> exact = Flux.fromIterable(codes).concatMap(code -> Flux.concat(
                chunks.withEntity(scope, ids, code, limit).map(c -> new Scored(c.turnId(), c.conversationId(),
                        c.id(), ENTITY_EXACT, EXACT_BASE + EntityTerms.matchedCodes(codes, c.content()), c.content())),
                summaries.search(scope, ids, code, limit).map(hit -> new Scored(hit.turnId(), hit.conversationId(),
                        null, ENTITY_EXACT, EXACT_BASE + 1, null))));
        Flux<Scored> names = recordNames(scope, ids, query, recall.getEntityScanRecords());
        Flux<Scored> chunkText = fullText.chunks(scope, ids, terms, limit * 2)
                .flatMap(hit -> trusted(hit.turnId(), hit.conversationId(), hit.chunkId(), CHUNK_FULLTEXT,
                        terms, min, hit.text(), hit.text()));
        Flux<Scored> recordText = fullText.records(scope, ids, terms, limit * 2)
                .flatMap(hit -> trusted(hit.turnId(), hit.conversationId(), null, RECORD_FULLTEXT, terms, min,
                        hit.text(), null));
        int backfill = recall.getBackfillTurns();
        Mono<List<String>> unindexed = chunks.unindexedTurns(scope, current, backfill + 1).collectList();
        var others = ids.stream().filter(id -> !id.equals(current)).toList();

        return unindexed.flatMap(gapTurns -> {
            boolean beyondBackfill = gapTurns.size() > backfill;
            var scanned = beyondBackfill ? gapTurns.subList(0, backfill) : gapTurns;
            Flux<Scored> gap = chunks.turnTexts(scope, List.of(current), scanned)
                    .flatMap(turn -> gapHit(turn, codes, terms, min));
            return Flux.concat(exact, names, chunkText, recordText, gap).collectList()
                    .flatMap(hits -> exclusions.excluded(scope, hits.stream().map(Scored::turnId).toList())
                            .map(excluded -> excluded.isEmpty() ? hits
                                    : hits.stream().filter(h -> !excluded.contains(h.turnId())).toList()))
                    .zipWith(chunks.anyUnindexed(scope, others))
                    .flatMap(t -> expand(scope, current, ids, merge(t.getT1(), limit))
                            .map(items -> new RecallResult(items, beyondBackfill || t.getT2(), Duration.ZERO)));
        });
    }

    private static Mono<Scored> trusted(String turnId, String conversationId, String chunkId, String reason,
            List<String> terms, int min, String text, String excerpt) {
        int matched = EntityTerms.matched(terms, text);
        return matched >= min
                ? Mono.just(new Scored(turnId, conversationId, chunkId, reason, termScore(matched, terms), excerpt))
                : Mono.empty();
    }

    private static double termScore(int matched, List<String> terms) {
        return matched + (double) matched / Math.max(1, terms.size());
    }

    private static Mono<Scored> gapHit(TurnText turn, List<String> codes, List<String> terms, int min) {
        var content = turn.content();
        int codeHits = EntityTerms.matchedCodes(codes, content);
        if (codeHits > 0) {
            return Mono.just(new Scored(turn.turnId(), turn.conversationId(), null, RECENT_GAP,
                    EXACT_BASE + codeHits, null));
        }
        int matched = EntityTerms.matched(terms, content);
        return matched >= min ? Mono.just(new Scored(turn.turnId(), turn.conversationId(), null, RECENT_GAP,
                termScore(matched, terms), null)) : Mono.empty();
    }

    /** Entity names of the newest records that occur in the question (names of at least 2 characters). */
    private Flux<Scored> recordNames(MemoryScope scope, List<String> ids, String query, int scan) {
        var lower = query.toLowerCase(Locale.ROOT);
        return db.sql("SELECT s.turn_id, s.conversation_id, s.record_json FROM memory_summaries s"
                        + ChunkRepository.LIVE_SESSION.formatted("s")
                        + " WHERE s.tenant_id = :tenantId AND s.subject = :subject AND s.conversation_id IN (:ids)"
                        + " AND s.superseded_at IS NULL ORDER BY s.created_at DESC LIMIT :scan")
                .bind("tenantId", scope.tenantId()).bind("subject", scope.subject()).bind("ids", ids)
                .bind("scan", scan)
                .map(row -> new String[] {row.get("turn_id", String.class), row.get("conversation_id", String.class),
                        row.get("record_json", String.class)})
                .all()
                .flatMap(row -> {
                    int hits = 0;
                    try {
                        for (var entity : JSON.readTree(row[2]).path("entities")) {
                            var name = entity.path("name").asText("").strip().toLowerCase(Locale.ROOT);
                            if (name.codePointCount(0, name.length()) >= 2 && lower.contains(name)) {
                                hits++;
                            }
                        }
                    } catch (Exception e) {
                        return Mono.empty();
                    }
                    return hits == 0 ? Mono.<Scored>empty()
                            : Mono.just(new Scored(row[0], row[1], null, ENTITY_EXACT, EXACT_BASE + hits, null));
                });
    }

    /** Per turn the highest score (ties: the first found, exact before full text before gap); best first. */
    private static List<Scored> merge(List<Scored> hits, int limit) {
        var best = new LinkedHashMap<String, Scored>();
        for (var hit : hits) {
            best.merge(hit.turnId(), hit, (a, b) -> b.score() > a.score() ? withExcerpt(b, a) : withExcerpt(a, b));
        }
        return best.values().stream().sorted(Comparator.comparingDouble(Scored::score).reversed()).limit(limit)
                .toList();
    }

    /** Keeps a chunk excerpt from the other hit of the same turn when the winner has none. */
    private static Scored withExcerpt(Scored winner, Scored other) {
        return winner.excerpt() != null || other.excerpt() == null ? winner
                : new Scored(winner.turnId(), winner.conversationId(), other.chunkId(), winner.reason(),
                        winner.score(), other.excerpt());
    }

    private Mono<List<RecallCandidate>> expand(MemoryScope scope, String current, List<String> ids,
            List<Scored> hits) {
        if (hits.isEmpty()) {
            return Mono.just(List.of());
        }
        return chunks.turnTexts(scope, ids, hits.stream().map(Scored::turnId).toList())
                .collectMap(TurnText::turnId, Function.identity())
                .map(turns -> hits.stream().filter(h -> turns.containsKey(h.turnId())).map(h -> {
                    var turn = turns.get(h.turnId());
                    return new RecallCandidate(turn.turnId(), h.chunkId(), turn.conversationId(),
                            turn.fromSequence(), turn.toSequence(), turn.answer() == null
                                    ? List.of(SourceType.USER_INPUT) : TURN_SOURCES,
                            h.reason(), h.score(), !turn.conversationId().equals(current), turn.finishedAt(),
                            turn.content(), h.excerpt());
                }).toList());
    }

    private Mono<RecallResult> enrich(MemoryScope scope, String current, String query, RecallResult base) {
        Mono<java.util.Optional<io.github.sukimiya.agileagent.memory.items.Profile>> profileMono = profiles == null
                ? Mono.just(java.util.Optional.empty())
                : profiles.current(scope);
        Mono<List<RecallResult.RelationHit>> relMono = relationLines(scope, query);
        Mono<List<RecallCandidate>> itemMono = itemCandidates(scope, current, query);
        return Mono.zip(profileMono.defaultIfEmpty(java.util.Optional.empty()), relMono.defaultIfEmpty(List.of()),
                        itemMono.defaultIfEmpty(List.of()))
                .map(t -> {
                    var merged = new ArrayList<>(base.items());
                    merged.addAll(t.getT3());
                    var profile = t.getT1().filter(p -> p.content() != null && !p.content().isBlank());
                    return new RecallResult(merged, base.coverageLimited(), base.latency(),
                            profile.map(p -> p.content()).orElse(null), profile.map(p -> p.version()).orElse(null),
                            t.getT2());
                });
    }

    private Mono<List<RecallCandidate>> itemCandidates(MemoryScope scope, String current, String query) {
        if (memoryItems == null) {
            return Mono.just(List.of());
        }
        var needles = new ArrayList<String>();
        needles.addAll(EntityTerms.codes(query));
        needles.addAll(EntityTerms.terms(query));
        return settings.crossConversationEnabled(scope).flatMapMany(cross -> {
            String conv = cross ? null : current;
            return memoryItems.searchRecallable(scope, conv, needles, props.getRecall().getMaxItems(),
                    new io.github.sukimiya.agileagent.memory.items.DecayPolicy(props.getDecay()));
        }).map(MemoryRetriever::toItemCandidate).collectList();
    }

    /** An item hit; its score is already decayed by the last recall (M2b Task 6). */
    private static RecallCandidate toItemCandidate(MemoryItemRepository.ScoredItem scored) {
        var item = scored.item();
        return new RecallCandidate(item.id(), null, item.conversationId() == null ? "" : item.conversationId(),
                0, 0, TURN_SOURCES, "ITEM", scored.score(),
                item.conversationId() == null, null, item.content(), null);
    }

    private Mono<List<RecallResult.RelationHit>> relationLines(MemoryScope scope, String query) {
        if (entities == null) {
            return Mono.just(List.of());
        }
        var keys = new ArrayList<String>();
        keys.addAll(EntityTerms.codes(query));
        // CJK bigrams alone are weak keys; also try contiguous Han runs of length >= 2 from the query.
        if (query != null) {
            var run = new StringBuilder();
            for (int i = 0; i < query.length(); ) {
                int cp = query.codePointAt(i);
                i += Character.charCount(cp);
                if (Character.UnicodeScript.of(cp) == Character.UnicodeScript.HAN) {
                    run.appendCodePoint(cp);
                } else {
                    if (run.length() >= 2) {
                        keys.add(run.toString());
                    }
                    run.setLength(0);
                }
            }
            if (run.length() >= 2) {
                keys.add(run.toString());
            }
        }
        int limit = props.getProfile().getMaxRelations();
        return entities.oneHop(scope, keys, limit)
                .concatMap(rel -> formatRelation(scope, rel))
                .take(limit)
                .collectList();
    }

    private Mono<RecallResult.RelationHit> formatRelation(MemoryScope scope,
            io.github.sukimiya.agileagent.memory.items.MemoryRelation rel) {
        return entities.findById(scope, rel.fromEntityId())
                .zipWith(entities.findById(scope, rel.toEntityId()))
                .map(t -> new RecallResult.RelationHit(rel.id(),
                        t.getT1().name() + " —" + rel.relation() + "→ " + t.getT2().name()));
    }

}
