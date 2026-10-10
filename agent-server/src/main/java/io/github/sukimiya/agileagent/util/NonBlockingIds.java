package io.github.sukimiya.agileagent.util;

import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Generates UUID-shaped identifiers for non-secret application data.
 * These values must not be used as credentials or security tokens.
 */
public final class NonBlockingIds {

    private static final long MOST_SIGNIFICANT_BITS_MASK = 0xffffffffffff0fffL;
    private static final long UUID_VERSION_FOUR = 0x0000000000004000L;
    private static final long LEAST_SIGNIFICANT_BITS_MASK = 0x3fffffffffffffffL;
    private static final long UUID_VARIANT_TWO = 0x8000000000000000L;

    private NonBlockingIds() { }

    public static String uuid() {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        long mostSignificantBits = (random.nextLong() & MOST_SIGNIFICANT_BITS_MASK) | UUID_VERSION_FOUR;
        long leastSignificantBits = (random.nextLong() & LEAST_SIGNIFICANT_BITS_MASK) | UUID_VARIANT_TWO;
        return new UUID(mostSignificantBits, leastSignificantBits).toString();
    }

    public static String shortUuid() {
        return uuid().substring(0, 8);
    }
}
