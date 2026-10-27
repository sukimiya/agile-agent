package io.github.sukimiya.agileagent.memory.items;

import io.github.sukimiya.agileagent.memory.MemoryProperties;
import io.github.sukimiya.agileagent.memory.items.MemoryItem.Assertion;

/**
 * Decay by last recall (M2b Task 6, D12 as revised 2026-10-27; {@code app.memory.decay.*}, read live).
 * <ul>
 * <li>Age: seconds since {@code COALESCE(last_recalled_at, created_at)} in database time.</li>
 * <li>{@link #weight}: {@code 0.5^(age / half-life)} (default 30 days); an item's recall score is
 * {@code (ITEM_BASE_SCORE + importance) * weight}, applied before items are selected. Chunks and turn records
 * do not decay.</li>
 * <li>{@link #inactivates}: only non-user assertions ({@code INFERRED}, or none) older than {@code inactive-after}
 * (default 180 days) become INACTIVE; {@code USER_STATED} and {@code USER_CONFIRMED} never do.</li>
 * </ul>
 */
public final class DecayPolicy {

    /** The base recall score of a memory item before importance and decay. */
    public static final double ITEM_BASE_SCORE = 50;

    private final MemoryProperties.Decay props;

    public DecayPolicy(MemoryProperties.Decay props) {
        this.props = props;
    }

    public double weight(long ageSeconds) {
        if (ageSeconds <= 0) {
            return 1.0;
        }
        return Math.pow(0.5, ageSeconds / (double) props.getHalfLife().toSeconds());
    }

    public double score(Double importance, long ageSeconds) {
        return (ITEM_BASE_SCORE + (importance == null ? 0 : importance)) * weight(ageSeconds);
    }

    public static boolean exempt(Assertion assertion) {
        return assertion == Assertion.USER_STATED || assertion == Assertion.USER_CONFIRMED;
    }

    public boolean inactivates(Assertion assertion, long ageSeconds) {
        return !exempt(assertion) && ageSeconds >= props.getInactiveAfter().toSeconds();
    }
}
