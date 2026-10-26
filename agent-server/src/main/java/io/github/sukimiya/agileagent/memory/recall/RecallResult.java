package io.github.sukimiya.agileagent.memory.recall;

import java.time.Duration;
import java.util.List;

/**
 * Trusted recall candidates of one question, best first. {@code coverageLimited}: some completed turns in scope
 * were neither indexed nor scanned directly, so "nothing found" does not mean "not there".
 */
public record RecallResult(List<RecallCandidate> items, boolean coverageLimited, Duration latency) {

    private static final RecallResult EMPTY = new RecallResult(List.of(), false, Duration.ZERO);

    public RecallResult {
        items = List.copyOf(items);
    }

    public static RecallResult empty() {
        return EMPTY;
    }
}
