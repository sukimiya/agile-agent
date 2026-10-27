package io.github.sukimiya.agileagent.memory.recall;

import java.time.Duration;
import java.util.List;

/**
 * Trusted recall candidates of one question, best first. {@code coverageLimited}: some completed turns in scope
 * were neither indexed nor scanned directly, so "nothing found" does not mean "not there".
 * {@code profileContent} / {@code profileVersion} / {@code relations} are optional user-memory extras (M2b
 * Task 4); the version and relation ids feed the per-turn recall explanation (Task 5).
 */
public record RecallResult(List<RecallCandidate> items, boolean coverageLimited, Duration latency,
        String profileContent, Long profileVersion, List<RelationHit> relations) {

    /** One injected one-hop relation: its {@code memory_relations.id} (null when unknown) and rendered line. */
    public record RelationHit(String relationId, String line) {}

    private static final RecallResult EMPTY = new RecallResult(List.of(), false, Duration.ZERO, null, null,
            List.of());

    public RecallResult {
        items = List.copyOf(items);
        relations = relations == null ? List.of() : List.copyOf(relations);
    }

    /** Turn-only recall (M2a compatibility). */
    public RecallResult(List<RecallCandidate> items, boolean coverageLimited, Duration latency) {
        this(items, coverageLimited, latency, null, null, List.of());
    }

    /** With profile text and relation lines without ids (not explainable, so not recorded). */
    public RecallResult(List<RecallCandidate> items, boolean coverageLimited, Duration latency,
            String profileContent, List<String> relationLines) {
        this(items, coverageLimited, latency, profileContent, null,
                relationLines == null ? List.of() : relationLines.stream().map(l -> new RelationHit(null, l)).toList());
    }

    public static RecallResult empty() {
        return EMPTY;
    }

    /** The rendered relation lines, in order. */
    public List<String> relationLines() {
        return relations.stream().map(RelationHit::line).toList();
    }

    public RecallResult withLatency(Duration latency) {
        return new RecallResult(items, coverageLimited, latency, profileContent, profileVersion, relations);
    }
}
