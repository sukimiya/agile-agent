package io.github.sukimiya.agileagent.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import org.junit.jupiter.api.Test;

class TtlCacheTest {

    @Test
    void expiresEntriesAndScavengesExpiredColdEntriesDuringNormalOperations() {
        var clock = new MutableClock(Instant.parse("2026-10-10T00:00:00Z"));
        var cache = new TtlCache<String, String>(Duration.ofMinutes(5), clock);
        cache.put("cold", "one");
        cache.put("active", "two");

        clock.advance(Duration.ofMinutes(5));
        assertThat(cache.get("active")).isEmpty();
        assertThat(cache.size()).isZero();
    }

    @Test
    void keepsValuesUntilTtlAndEvictsExplicitly() {
        var clock = new MutableClock(Instant.parse("2026-10-10T00:00:00Z"));
        var cache = new TtlCache<String, String>(Duration.ofMinutes(5), clock);
        cache.put("a", "one");

        assertThat(cache.get("a")).contains("one");
        cache.evict("a");
        assertThat(cache.get("a")).isEmpty();
    }

    private static final class MutableClock extends Clock {
        private Instant instant;

        private MutableClock(Instant instant) { this.instant = instant; }
        void advance(Duration duration) { instant = instant.plus(duration); }
        @Override public ZoneOffset getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(java.time.ZoneId zone) { return this; }
        @Override public Instant instant() { return instant; }
    }
}
