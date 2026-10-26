package io.github.sukimiya.agileagent.memory.recall;

import java.time.Duration;
import java.util.List;

/**
 * Trusted recall candidates of one question, best first. {@code coverageLimited}: some completed turns in scope
 * were neither indexed nor scanned directly, so "nothing found" does not mean "not there".
 * {@code profileContent} / {@code relationLines} are optional user-memory extras (M2b Task 4).
 */
public record RecallResult(List<RecallCandidate> items, boolean coverageLimited, Duration latency,
        String profileContent, List<String> relationLines) {

    private static final RecallResult EMPTY = new RecallResult(List.of(), false, Duration.ZERO, null, List.of());

    public RecallResult {
        items = List.copyOf(items);
        relationLines = relationLines == null ? List.of() : List.copyOf(relationLines);
    }

    /** Turn-only recall (M2a compatibility). */
    public RecallResult(List<RecallCandidate> items, boolean coverageLimited, Duration latency) {
        this(items, coverageLimited, latency, null, List.of());
    }

    public static RecallResult empty() {
        return EMPTY;
    }

    public RecallResult withProfileAndRelations(String profile, List<String> relations) {
        return new RecallResult(items, coverageLimited, latency, profile, relations == null ? List.of() : relations);
    }

    public RecallResult withLatency(Duration latency) {
        return new RecallResult(items, coverageLimited, latency, profileContent, relationLines);
    }
}
