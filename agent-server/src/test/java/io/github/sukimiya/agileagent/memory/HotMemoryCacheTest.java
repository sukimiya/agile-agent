package io.github.sukimiya.agileagent.memory;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.github.sukimiya.agileagent.LocalRedisTestServer;
import io.github.sukimiya.agileagent.memory.MemoryModel.HistoryWindow;
import io.github.sukimiya.agileagent.memory.MemoryModel.StoredMessage;
import io.github.sukimiya.agileagent.memory.MemoryModel.TurnStatus;
import io.github.sukimiya.agileagent.service.LlmClient;
import io.github.sukimiya.agileagent.util.NonBlockingIds;

class HotMemoryCacheTest {

    static final int REDIS_DB = 4;
    static final MemoryScope ALICE = new MemoryScope("tenant-1", "alice");
    static final Duration WAIT = Duration.ofSeconds(5);

    LettuceConnectionFactory connectionFactory;
    ReactiveStringRedisTemplate redis;
    MemoryProperties props;
    HotMemoryCache cache;
    String conversation;

    @BeforeEach
    void setUp() {
        connectionFactory = new LettuceConnectionFactory(LocalRedisTestServer.configuration(REDIS_DB));
        connectionFactory.afterPropertiesSet();
        redis = new ReactiveStringRedisTemplate(connectionFactory);
        props = new MemoryProperties();
        cache = new HotMemoryCache(redis, new ObjectMapper(), props);
        conversation = NonBlockingIds.uuid();
    }

    @AfterEach
    void tearDown() {
        connectionFactory.destroy();
    }

    static StoredMessage message(long sequence, String content) {
        return new StoredMessage(NonBlockingIds.uuid(), sequence, LlmClient.Message.user(content), SourceType.USER_INPUT,
                TurnStatus.COMPLETED, Instant.parse("2026-10-22T01:02:03.123456Z"));
    }

    static List<StoredMessage> messages(int from, int to, String content) {
        var list = new ArrayList<StoredMessage>();
        for (int i = from; i <= to; i++) {
            list.add(message(i, content + i));
        }
        return list;
    }

    String key(MemoryScope scope) {
        return HotMemoryCache.key(props.getNamespace(), scope, conversation);
    }

    @Test
    void putThenGetOfTheSameRevisionReturnsTheWindowUnchanged() {
        var window = new HistoryWindow(messages(1, 3, "m"), false, 7);

        cache.put(ALICE, conversation, 7, window).block(WAIT);

        assertThat(cache.get(ALICE, conversation, 7).block(WAIT)).isEqualTo(window);
    }

    @Test
    void staleRevisionIsIgnored() {
        cache.put(ALICE, conversation, 2, new HistoryWindow(messages(1, 2, "v2-"), false, 2)).block(WAIT);

        assertThat(cache.get(ALICE, conversation, 3).blockOptional(WAIT)).isEmpty();
        assertThat(cache.get(ALICE, conversation, 1).blockOptional(WAIT)).isEmpty();

        cache.put(ALICE, conversation, 1, new HistoryWindow(messages(1, 1, "v1-"), false, 1)).block(WAIT);

        assertThat(cache.get(ALICE, conversation, 2).block(WAIT).items()).extracting(m -> m.payload().content())
                .containsExactly("v2-1", "v2-2");
        assertThat(cache.get(ALICE, conversation, 1).blockOptional(WAIT)).isEmpty();
    }

    @Test
    void cacheHasSevenDayIdleTtlRenewedOnHit() {
        cache.put(ALICE, conversation, 1, new HistoryWindow(messages(1, 1, "m"), false, 1)).block(WAIT);
        var sevenDays = Duration.ofDays(7);

        assertThat(redis.getExpire(key(ALICE)).block(WAIT)).isBetween(sevenDays.minusMinutes(1), sevenDays);

        redis.expire(key(ALICE), Duration.ofMinutes(5)).block(WAIT);
        cache.get(ALICE, conversation, 1).block(WAIT);

        assertThat(redis.getExpire(key(ALICE)).block(WAIT)).isBetween(sevenDays.minusMinutes(1), sevenDays);
    }

    /**
     * Controlled clock over Redis expiry (acceptance 13): Redis keeps the time, so elapsed idle time is applied by
     * rewriting the key's remaining TTL ({@code remaining - elapsed}), and an elapsed time at or beyond the
     * remaining TTL lets the key expire for real.
     */
    final class RedisIdleClock {
        void advance(String key, Duration elapsed) {
            var remaining = redis.getExpire(key).block(WAIT);
            assertThat(remaining).as("key has a TTL").isNotNull().isPositive();
            if (elapsed.compareTo(remaining) >= 0) {
                redis.expire(key, Duration.ofMillis(1)).block(WAIT);
                long deadline = System.nanoTime() + WAIT.toNanos();
                while (Boolean.TRUE.equals(redis.hasKey(key).block(WAIT)) && System.nanoTime() < deadline) {
                    Thread.onSpinWait();
                }
            } else {
                redis.expire(key, remaining.minus(elapsed)).block(WAIT);
            }
        }
    }

    @Test
    void recentWindowIdleTtlSevenDaysWithControlledClock() {
        var clock = new RedisIdleClock();
        var window = new HistoryWindow(messages(1, 3, "m"), false, 1);
        cache.put(ALICE, conversation, 1, window).block(WAIT);
        var almostSevenDays = Duration.ofDays(7).minusHours(1);

        // Six days 23 hours idle: still cached; the read is a hit and restarts the 7-day idle period.
        clock.advance(key(ALICE), almostSevenDays);
        assertThat(cache.get(ALICE, conversation, 1).block(WAIT)).isEqualTo(window);
        assertThat(redis.getExpire(key(ALICE)).block(WAIT)).isGreaterThan(Duration.ofDays(7).minusMinutes(1));

        // Another 6 days 23 hours: still cached, because the last read renewed it.
        clock.advance(key(ALICE), almostSevenDays);
        assertThat(cache.get(ALICE, conversation, 1).block(WAIT)).isEqualTo(window);

        // Seven full days without any access: gone, the next read is a miss (the database serves it).
        clock.advance(key(ALICE), Duration.ofDays(7));
        assertThat(cache.get(ALICE, conversation, 1).blockOptional(WAIT)).isEmpty();
        assertThat(redis.hasKey(key(ALICE)).block(WAIT)).isFalse();
    }

    @Test
    void windowIsCappedAtHundredMessagesKeepingTheNewestAndHasMore() {
        cache.put(ALICE, conversation, 1, new HistoryWindow(messages(1, 150, "m"), false, 1)).block(WAIT);

        var cached = cache.get(ALICE, conversation, 1).block(WAIT);

        assertThat(cached.items()).hasSize(100);
        assertThat(cached.items().get(0).sequence()).isEqualTo(51);
        assertThat(cached.items().get(99).sequence()).isEqualTo(150);
        assertThat(cached.hasMore()).isTrue();
    }

    @Test
    void windowIsCappedAt256KibOfSerializedPayload() {
        var big = "x".repeat(10_000);
        cache.put(ALICE, conversation, 1, new HistoryWindow(messages(1, 60, big), false, 1)).block(WAIT);

        var raw = redis.<String, String>opsForHash().get(key(ALICE), "data").block(WAIT);
        var cached = cache.get(ALICE, conversation, 1).block(WAIT);

        assertThat(raw.getBytes(StandardCharsets.UTF_8).length).isLessThanOrEqualTo(262_144);
        assertThat(cached.items()).hasSizeLessThan(60).isNotEmpty();
        assertThat(cached.items().get(cached.items().size() - 1).sequence()).isEqualTo(60);
        assertThat(cached.hasMore()).isTrue();
    }

    @Test
    void singleOversizedNewestMessageBypassesTheCache() {
        var window = new HistoryWindow(List.of(message(1, "small"), message(2, "y".repeat(300_000))), false, 1);

        cache.put(ALICE, conversation, 1, window).block(WAIT);

        assertThat(redis.hasKey(key(ALICE)).block(WAIT)).isFalse();
    }

    @Test
    void keysAreScopedByNamespaceTenantAndSubject() {
        cache.put(ALICE, conversation, 1, new HistoryWindow(messages(1, 1, "m"), false, 1)).block(WAIT);

        assertThat(cache.get(new MemoryScope("tenant-2", "alice"), conversation, 1).blockOptional(WAIT)).isEmpty();
        assertThat(cache.get(new MemoryScope("tenant-1", "Alice"), conversation, 1).blockOptional(WAIT)).isEmpty();
        assertThat(HotMemoryCache.key("default", new MemoryScope("a:b", "c"), "x"))
                .isNotEqualTo(HotMemoryCache.key("default", new MemoryScope("a", "b:c"), "x"));
        assertThat(key(ALICE)).startsWith("memory:" + props.getNamespace() + ":");

        var otherNamespace = new MemoryProperties();
        otherNamespace.setNamespace("staging");
        assertThat(new HotMemoryCache(redis, new ObjectMapper(), otherNamespace)
                .get(ALICE, conversation, 1).blockOptional(WAIT)).isEmpty();
    }

    @Test
    void corruptJsonIsAMiss() {
        redis.<String, String>opsForHash().put(key(ALICE), "rev", "1").block(WAIT);
        redis.<String, String>opsForHash().put(key(ALICE), "data", "{not json").block(WAIT);

        assertThat(cache.get(ALICE, conversation, 1).blockOptional(WAIT)).isEmpty();

        var window = new HistoryWindow(messages(1, 1, "m"), false, 1);
        cache.put(ALICE, conversation, 1, window).block(WAIT);
        assertThat(cache.get(ALICE, conversation, 1).block(WAIT)).isEqualTo(window);
    }

    @Test
    void redisErrorsAreMissesAndWritesAreDropped() {
        var down = new LettuceConnectionFactory(new RedisStandaloneConfiguration("127.0.0.1", 1));
        down.afterPropertiesSet();
        try {
            var broken = new HotMemoryCache(new ReactiveStringRedisTemplate(down), new ObjectMapper(), props);

            assertThat(broken.get(ALICE, conversation, 1).blockOptional(Duration.ofSeconds(20))).isEmpty();
            broken.put(ALICE, conversation, 1, new HistoryWindow(messages(1, 1, "m"), false, 1))
                    .block(Duration.ofSeconds(20));
            broken.evict(ALICE, conversation).block(Duration.ofSeconds(20));
        } finally {
            down.destroy();
        }
    }

    @Test
    void evictRemovesTheWindow() {
        cache.put(ALICE, conversation, 1, new HistoryWindow(messages(1, 1, "m"), false, 1)).block(WAIT);

        cache.evict(ALICE, conversation).block(WAIT);

        assertThat(cache.get(ALICE, conversation, 1).blockOptional(WAIT)).isEmpty();
    }
}
