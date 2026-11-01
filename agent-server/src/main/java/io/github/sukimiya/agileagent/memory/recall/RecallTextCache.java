package io.github.sukimiya.agileagent.memory.recall;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.NestedExceptionUtils;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import io.github.sukimiya.agileagent.memory.MemoryProperties;
import io.github.sukimiya.agileagent.memory.MemoryScope;
import io.github.sukimiya.agileagent.memory.recall.ChunkRepository.TurnText;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Plain text of recalled compressed original turns (M2b Task 9, user decision 2026-10-30 point 8): one Redis hash
 * per conversation ({@code memory:<namespace>:recall:<tenant>:<subject>:<conversationId>}, field = turn id),
 * expiring {@code app.memory.storage.recall-cache-ttl} (default 1h) after the last write. Deleting the
 * conversation, forgetting an item and deleting an item evict the conversations concerned. The cache only saves
 * decompression: {@link ChunkRepository#recallTexts} still asks the database which turns are live before serving
 * one. Redis errors are logged and read as a miss. Without Redis (tests) an in-process map with a clock is used.
 */
@Component
public class RecallTextCache {

    private static final Logger log = LoggerFactory.getLogger(RecallTextCache.class);
    private static final Base64.Encoder ENC = Base64.getUrlEncoder().withoutPadding();
    private static final ObjectMapper JSON = new ObjectMapper().registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    private final ReactiveStringRedisTemplate redis;
    private final MemoryProperties props;
    private final Clock clock;
    private final ConcurrentHashMap<String, Map<String, Local>> local = new ConcurrentHashMap<>();

    private record Local(String json, long expiresAtMillis) {}

    @Autowired
    public RecallTextCache(ReactiveStringRedisTemplate redis, MemoryProperties props) {
        this(redis, props, Clock.systemUTC());
    }

    /** Test helper: the in-process map (when {@code redis} is null) expires by {@code clock}. */
    public RecallTextCache(ReactiveStringRedisTemplate redis, MemoryProperties props, Clock clock) {
        this.redis = redis;
        this.props = props;
        this.clock = clock;
    }

    static String key(String namespace, MemoryScope scope, String conversationId) {
        return "memory:" + namespace + ":recall:"
                + ENC.encodeToString(scope.tenantId().getBytes(StandardCharsets.UTF_8)) + ":"
                + ENC.encodeToString(scope.subject().getBytes(StandardCharsets.UTF_8)) + ":" + conversationId;
    }

    private String key(MemoryScope scope, String conversationId) {
        return key(props.getNamespace(), scope, conversationId);
    }

    /** Cached turns among {@code turnIds} in {@code conversationIds}, by turn id. */
    public Mono<Map<String, TurnText>> get(MemoryScope scope, List<String> conversationIds, List<String> turnIds) {
        if (conversationIds.isEmpty() || turnIds.isEmpty()) {
            return Mono.just(Map.of());
        }
        if (redis == null) {
            return Mono.fromCallable(() -> {
                var out = new HashMap<String, TurnText>();
                long now = clock.millis();
                for (var c : conversationIds) {
                    var hash = local.get(key(scope, c));
                    if (hash == null) {
                        continue;
                    }
                    for (var t : turnIds) {
                        var e = hash.get(t);
                        if (e != null && e.expiresAtMillis() > now) {
                            add(out, c, e.json());
                        }
                    }
                }
                return (Map<String, TurnText>) out;
            });
        }
        var fields = List.<Object>copyOf(turnIds);
        return Flux.fromIterable(conversationIds)
                .concatMap(c -> redis.opsForHash().multiGet(key(scope, c), fields)
                        .map(values -> {
                            var out = new HashMap<String, TurnText>();
                            values.forEach(v -> {
                                if (v != null) {
                                    add(out, c, v.toString());
                                }
                            });
                            return out;
                        }))
                .reduce(new HashMap<String, TurnText>(), (a, b) -> {
                    a.putAll(b);
                    return a;
                })
                .<Map<String, TurnText>>map(m -> m)
                .onErrorResume(e -> {
                    log.warn("memory.recall.cache read failed: {}",
                            NestedExceptionUtils.getMostSpecificCause(e).toString());
                    return Mono.just(Map.of());
                });
    }

    private static void add(Map<String, TurnText> out, String conversationId, String json) {
        try {
            var turn = JSON.readValue(json, TurnText.class);
            if (conversationId.equals(turn.conversationId())) {
                out.put(turn.turnId(), turn);
            }
        } catch (Exception e) {
            // unreadable entry: a miss
        }
    }

    /** Stores turns (grouped by conversation), each hash expiring {@code recall-cache-ttl} from now. */
    public Mono<Void> put(MemoryScope scope, Collection<TurnText> turns) {
        if (turns.isEmpty()) {
            return Mono.empty();
        }
        Duration ttl = props.getStorage().getRecallCacheTtl();
        var byConversation = new HashMap<String, Map<String, String>>();
        for (var t : turns) {
            try {
                byConversation.computeIfAbsent(t.conversationId(), k -> new HashMap<>())
                        .put(t.turnId(), JSON.writeValueAsString(t));
            } catch (Exception e) {
                return Mono.empty();
            }
        }
        if (redis == null) {
            long expires = clock.millis() + ttl.toMillis();
            byConversation.forEach((c, fields) -> {
                var hash = local.computeIfAbsent(key(scope, c), k -> new ConcurrentHashMap<>());
                fields.forEach((f, v) -> hash.put(f, new Local(v, expires)));
            });
            return Mono.empty();
        }
        return Flux.fromIterable(byConversation.entrySet())
                .concatMap(e -> redis.opsForHash().putAll(key(scope, e.getKey()), e.getValue())
                        .then(redis.expire(key(scope, e.getKey()), ttl)))
                .then()
                .onErrorResume(e -> {
                    log.warn("memory.recall.cache write failed: {}",
                            NestedExceptionUtils.getMostSpecificCause(e).toString());
                    return Mono.empty();
                });
    }

    /** Drops the cached turns of these conversations. */
    public Mono<Void> evict(MemoryScope scope, Collection<String> conversationIds) {
        if (conversationIds.isEmpty()) {
            return Mono.empty();
        }
        var keys = new ArrayList<String>();
        conversationIds.forEach(c -> keys.add(key(scope, c)));
        keys.forEach(local::remove);
        if (redis == null) {
            return Mono.empty();
        }
        return redis.delete(keys.toArray(String[]::new)).then()
                .onErrorResume(e -> {
                    log.warn("memory.recall.cache evict failed: {}",
                            NestedExceptionUtils.getMostSpecificCause(e).toString());
                    return Mono.empty();
                });
    }
}
