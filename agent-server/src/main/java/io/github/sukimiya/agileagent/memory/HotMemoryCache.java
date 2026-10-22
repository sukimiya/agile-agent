package io.github.sukimiya.agileagent.memory;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.NestedExceptionUtils;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.github.sukimiya.agileagent.memory.MemoryModel.HistoryWindow;
import io.github.sukimiya.agileagent.memory.MemoryModel.StoredMessage;
import io.github.sukimiya.agileagent.memory.MemoryModel.TurnStatus;
import io.github.sukimiya.agileagent.service.LlmClient;
import reactor.core.publisher.Mono;

/**
 * Rebuildable Redis cache of a conversation's recent window: at most {@code cache-max-messages} newest messages
 * whose serialized form fits {@code cache-max-bytes}; a newest message that alone exceeds the budget bypasses the
 * cache. Entries are a hash {@code rev}/{@code data} keyed by namespace, tenant, subject and conversation, and are
 * only returned for the exact revision asked for. Writes never replace a newer revision. The TTL is an idle
 * timeout refreshed on every hit. Redis failures or corrupt entries are misses; the database stays the source of
 * truth. Old {@code chatmemory:*} keys are never read.
 */
@Component
public class HotMemoryCache {

    private static final Logger log = LoggerFactory.getLogger(HotMemoryCache.class);
    private static final Base64.Encoder KEY_PART = Base64.getUrlEncoder().withoutPadding();
    private static final RedisScript<Long> PUT_IF_NOT_OLDER = RedisScript.of("""
            local current = redis.call('HGET', KEYS[1], 'rev')
            if current and tonumber(current) > tonumber(ARGV[1]) then return 0 end
            redis.call('HSET', KEYS[1], 'rev', ARGV[1], 'data', ARGV[2])
            redis.call('PEXPIRE', KEYS[1], ARGV[3])
            return 1
            """, Long.class);

    /** Revision above any real one: a deleted conversation's entry refuses every later put and matches no get. */
    static final String DELETED_REVISION = "9007199254740991";
    private static final RedisScript<Long> TOMBSTONE = RedisScript.of("""
            redis.call('HDEL', KEYS[1], 'data')
            redis.call('HSET', KEYS[1], 'rev', ARGV[1])
            redis.call('PEXPIRE', KEYS[1], ARGV[2])
            return 1
            """, Long.class);

    /** Serialized cache form; {@code occurredAt} is an ISO-8601 instant so no Jackson time module is needed. */
    record CachedMessage(String id, long sequence, LlmClient.Message payload, SourceType source, TurnStatus status,
            String occurredAt) {}

    record CachedWindow(boolean hasMore, List<CachedMessage> items) {}

    private final ReactiveStringRedisTemplate redis;
    private final ObjectMapper mapper;
    private final MemoryProperties props;

    public HotMemoryCache(ReactiveStringRedisTemplate redis, ObjectMapper mapper, MemoryProperties props) {
        this.redis = redis;
        this.mapper = mapper;
        this.props = props;
    }

    static String key(String namespace, MemoryScope scope, String conversationId) {
        return "memory:" + namespace + ":history:" + encode(scope.tenantId()) + ":" + encode(scope.subject()) + ":"
                + conversationId;
    }

    /** Cached window for exactly {@code revision}; empty on miss, other revision, corrupt entry or Redis error. */
    public Mono<HistoryWindow> get(MemoryScope scope, String conversationId, long revision) {
        var key = key(props.getNamespace(), scope, conversationId);
        return redis.<String, String>opsForHash().multiGet(key, List.of("rev", "data"))
                .flatMap(values -> {
                    if (values.size() != 2 || !Long.toString(revision).equals(values.get(0)) || values.get(1) == null) {
                        return Mono.empty();
                    }
                    var window = decode(values.get(1), revision);
                    return window == null ? Mono.empty()
                            : redis.expire(key, props.getCacheIdleTtl()).thenReturn(window);
                })
                .onErrorResume(e -> {
                    log.warn("memory.cache read failed, using database: {}", NestedExceptionUtils.getMostSpecificCause(e).toString());
                    return Mono.empty();
                });
    }

    /** Stores the bounded tail of {@code window} unless a newer revision is cached; failures are logged only. */
    public Mono<Void> put(MemoryScope scope, String conversationId, long revision, HistoryWindow window) {
        return Mono.defer(() -> {
            var data = fit(window);
            if (data == null) {
                return Mono.empty();
            }
            var key = key(props.getNamespace(), scope, conversationId);
            return redis.execute(PUT_IF_NOT_OLDER, List.of(key),
                            List.of(Long.toString(revision), data, Long.toString(props.getCacheIdleTtl().toMillis())))
                    .then();
        }).onErrorResume(e -> {
            log.warn("memory.cache write skipped: {}", NestedExceptionUtils.getMostSpecificCause(e).toString());
            return Mono.empty();
        });
    }

    /**
     * Replaces the entry with a content-free deletion marker so a reader that resolved the session before the
     * deletion cannot write the window back. Errors propagate: the deletion cleanup retries until it succeeds.
     */
    public Mono<Void> tombstone(MemoryScope scope, String conversationId) {
        return Mono.defer(() -> redis.execute(TOMBSTONE, List.of(key(props.getNamespace(), scope, conversationId)),
                        List.of(DELETED_REVISION, Long.toString(props.getCacheIdleTtl().toMillis())))
                .then());
    }

    public Mono<Void> evict(MemoryScope scope, String conversationId) {
        return redis.delete(key(props.getNamespace(), scope, conversationId)).then()
                .onErrorResume(e -> {
                    log.warn("memory.cache evict failed: {}", NestedExceptionUtils.getMostSpecificCause(e).toString());
                    return Mono.empty();
                });
    }

    /**
     * Serialized newest messages within the count and byte limits; {@code hasMore} is set whenever anything was
     * dropped. Returns null (bypass) when not even the newest message fits.
     */
    String fit(HistoryWindow window) {
        var all = window.items();
        var maxCount = props.getCacheMaxMessages();
        var maxBytes = props.getCacheMaxBytes();
        var kept = new ArrayList<CachedMessage>();
        long bytes = 0;
        for (int i = all.size() - 1; i >= 0 && kept.size() < maxCount; i--) {
            var cached = toCached(all.get(i));
            var size = utf8Length(json(cached)) + 1;
            if (bytes + size > maxBytes) {
                break;
            }
            bytes += size;
            kept.add(0, cached);
        }
        if (kept.isEmpty() && !all.isEmpty()) {
            return null;
        }
        var hasMore = window.hasMore() || kept.size() < all.size();
        var data = json(new CachedWindow(hasMore, kept));
        while (utf8Length(data) > maxBytes && !kept.isEmpty()) {
            kept.remove(0);
            data = json(new CachedWindow(true, kept));
        }
        return kept.isEmpty() && !all.isEmpty() ? null : data;
    }

    private HistoryWindow decode(String data, long revision) {
        try {
            var cached = mapper.readValue(data, CachedWindow.class);
            var items = cached.items().stream()
                    .map(m -> new StoredMessage(m.id(), m.sequence(), m.payload(), m.source(), m.status(),
                            m.occurredAt() == null ? null : Instant.parse(m.occurredAt())))
                    .toList();
            return new HistoryWindow(items, cached.hasMore(), revision);
        } catch (Exception e) {
            log.warn("memory.cache entry unreadable, using database: {}", e.toString());
            return null;
        }
    }

    private static CachedMessage toCached(StoredMessage m) {
        return new CachedMessage(m.id(), m.sequence(), m.payload(), m.source(), m.status(),
                m.occurredAt() == null ? null : m.occurredAt().toString());
    }

    private String json(Object value) {
        try {
            return mapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("cache value not serializable", e);
        }
    }

    private static int utf8Length(String s) {
        return s.getBytes(StandardCharsets.UTF_8).length;
    }

    private static String encode(String part) {
        return KEY_PART.encodeToString(part.getBytes(StandardCharsets.UTF_8));
    }
}
