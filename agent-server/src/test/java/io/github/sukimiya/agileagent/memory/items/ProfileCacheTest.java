package io.github.sukimiya.agileagent.memory.items;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.github.sukimiya.agileagent.memory.MemoryProperties;
import io.github.sukimiya.agileagent.memory.MemoryScope;
import io.github.sukimiya.agileagent.memory.MemoryTestStack;

class ProfileCacheTest {

    static final Duration WAIT = Duration.ofSeconds(5);
    static final MemoryScope ALICE = MemoryTestStack.ALICE;

    AtomicReference<Instant> now;
    ProfileCache cache;
    MemoryProperties props;

    @BeforeEach
    void setUp() {
        now = new AtomicReference<>(Instant.parse("2026-10-27T00:00:00Z"));
        props = new MemoryProperties();
        props.getProfile().setCacheTtl(Duration.ofHours(1));
        Clock clock = new Clock() {
            @Override public ZoneOffset getZone() { return ZoneOffset.UTC; }
            @Override public Clock withZone(java.time.ZoneId zone) { return this; }
            @Override public Instant instant() { return now.get(); }
        };
        cache = new ProfileCache(null, props, clock);
    }

    @Test
    void profileCacheTtlOneHourWithControlledClock() {
        var profile = new Profile(ALICE, "hello", 3, 1);
        cache.put(profile).block(WAIT);
        assertThat(cache.get(ALICE, 3).block(WAIT)).isNotNull();
        now.set(now.get().plus(Duration.ofMinutes(59)));
        assertThat(cache.get(ALICE, 3).block(WAIT)).isNotNull();
        now.set(now.get().plus(Duration.ofMinutes(2)));
        assertThat(cache.get(ALICE, 3).block(WAIT)).isNull();
    }

    @Test
    void profileVersionChangeInvalidatesCacheImmediately() {
        cache.put(new Profile(ALICE, "v1", 1, 0)).block(WAIT);
        assertThat(cache.get(ALICE, 1).block(WAIT).content()).isEqualTo("v1");
        cache.put(new Profile(ALICE, "v2", 2, 0)).block(WAIT);
        assertThat(cache.get(ALICE, 1).block(WAIT)).isNull();
        assertThat(cache.get(ALICE, 2).block(WAIT).content()).isEqualTo("v2");
    }
}
