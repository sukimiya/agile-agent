package io.github.sukimiya.agileagent.memory.retention;

import java.time.Duration;
import java.util.List;

import io.github.sukimiya.agileagent.memory.MemoryProperties.Purge.Tier;

/**
 * Validated retention tiers: the retention of a purge candidate is the {@code keep} of the last tier whose
 * {@code fromCount} is at most its recall reference count. Tiers must be non-empty, start at 0, have strictly
 * ascending {@code fromCount} (so they never overlap) and positive {@code keep}. Any number of tiers.
 */
public final class RetentionTiers {

    private final long[] from;
    private final Duration[] keep;

    private RetentionTiers(long[] from, Duration[] keep) {
        this.from = from;
        this.keep = keep;
    }

    /** Fails with {@link IllegalArgumentException} naming the problem (startup fails on a bad config). */
    public static RetentionTiers of(List<Tier> tiers) {
        if (tiers == null || tiers.isEmpty()) {
            throw new IllegalArgumentException("app.memory.purge.tiers needs at least one tier");
        }
        var from = new long[tiers.size()];
        var keep = new Duration[tiers.size()];
        for (int i = 0; i < tiers.size(); i++) {
            var tier = tiers.get(i);
            if (tier == null) {
                throw new IllegalArgumentException("app.memory.purge.tiers[" + i + "] is empty");
            }
            if (i == 0 && tier.getFromCount() != 0) {
                throw new IllegalArgumentException("app.memory.purge.tiers must start at 0");
            }
            if (i > 0 && tier.getFromCount() <= from[i - 1]) {
                throw new IllegalArgumentException("app.memory.purge.tiers[" + i
                        + "].from-count must be strictly ascending (no overlapping tiers)");
            }
            if (tier.getKeep() == null || tier.getKeep().isNegative() || tier.getKeep().isZero()) {
                throw new IllegalArgumentException("app.memory.purge.tiers[" + i + "].keep must be positive");
            }
            from[i] = tier.getFromCount();
            keep[i] = tier.getKeep();
        }
        return new RetentionTiers(from, keep);
    }

    /** How long a deleted-source candidate with {@code referenceCount} references is kept. */
    public Duration retentionFor(long referenceCount) {
        if (referenceCount < 0) {
            throw new IllegalArgumentException("reference count must not be negative");
        }
        int i = from.length - 1;
        while (from[i] > referenceCount) {
            i--;
        }
        return keep[i];
    }

    public int size() {
        return from.length;
    }

    /** The shortest retention of any tier: nothing younger than this can be purged. */
    public Duration shortest() {
        var min = keep[0];
        for (var d : keep) {
            if (d.compareTo(min) < 0) {
                min = d;
            }
        }
        return min;
    }
}
