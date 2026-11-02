package io.github.sukimiya.agileagent.memory.items;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.util.Base64;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.NestedExceptionUtils;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.github.sukimiya.agileagent.memory.MemoryProperties;
import io.github.sukimiya.agileagent.memory.MemoryScope;
import reactor.core.publisher.Mono;

/**
 * Redis cache of the user profile snapshot (M2b Task 4): TTL {@code app.memory.profile.cache-ttl} (default 1h),
 * keyed by version so a rebuild invalidates immediately. An in-process map with a controllable {@link Clock} is
 * used when Redis is unavailable (tests).
 */
@Component
public class ProfileCache {

    private static final Logger log = LoggerFactory.getLogger(ProfileCache.class);
    private static final Base64.Encoder ENC = Base64.getUrlEncoder().withoutPadding();
    private static final ObjectMapper JSON = new ObjectMapper();

    private final ReactiveStringRedisTemplate redis;
    private final MemoryProperties props;
    private final Clock clock;
    private final ConcurrentHashMap<String, LocalEntry> local = new ConcurrentHashMap<>();

    private record LocalEntry(String json, long version, long expiresAtMillis) {}

    @Autowired
    public ProfileCache(ReactiveStringRedisTemplate redis, MemoryProperties props) {
        this(redis, props, Clock.systemUTC());
    }

    /** Test helper with a controllable clock (local map only when redis is null). */
    public ProfileCache(ReactiveStringRedisTemplate redis, MemoryProperties props, Clock clock) {
        this.redis = redis;
        this.props = props;
        this.clock = clock == null ? Clock.systemUTC() : clock;
    }

    static String key(String namespace, MemoryScope scope) {
        return "memory:" + namespace + ":profile:" + ENC.encodeToString(scope.tenantId().getBytes(StandardCharsets.UTF_8))
                + ":" + ENC.encodeToString(scope.subject().getBytes(StandardCharsets.UTF_8));
    }

    public Mono<Profile> get(MemoryScope scope, long expectedVersion) {
        var k = key(props.getNamespace(), scope);
        if (redis == null) {
            return Mono.fromCallable(() -> {
                var e = local.get(k);
                if (e == null || e.version != expectedVersion || e.expiresAtMillis <= clock.millis()) {
                    return null;
                }
                return decode(e.json, scope);
            }).flatMap(p -> p == null ? Mono.empty() : Mono.just(p));
        }
        return redis.opsForValue().get(k)
                .flatMap(json -> {
                    var p = decode(json, scope);
                    if (p == null || p.version() != expectedVersion) {
                        return Mono.empty();
                    }
                    return redis.expire(k, props.getProfile().getCacheTtl()).thenReturn(p);
                })
                .onErrorResume(e -> {
                    log.warn("memory.profile.cache read failed: {}",
                            NestedExceptionUtils.getMostSpecificCause(e).toString());
                    return Mono.empty();
                });
    }

    public Mono<Void> put(Profile profile) {
        if (profile == null) {
            return Mono.empty();
        }
        var k = key(props.getNamespace(), profile.scope());
        String json;
        try {
            json = JSON.writeValueAsString(new Cached(profile.content(), profile.version(), profile.builtThrough()));
        } catch (Exception e) {
            return Mono.empty();
        }
        Duration ttl = props.getProfile().getCacheTtl();
        if (redis == null) {
            local.put(k, new LocalEntry(json, profile.version(), clock.millis() + ttl.toMillis()));
            return Mono.empty();
        }
        return redis.opsForValue().set(k, json, ttl)
                .then()
                .onErrorResume(e -> {
                    log.warn("memory.profile.cache write failed: {}",
                            NestedExceptionUtils.getMostSpecificCause(e).toString());
                    return Mono.empty();
                });
    }

    public Mono<Void> invalidate(MemoryScope scope) {
        var k = key(props.getNamespace(), scope);
        local.remove(k);
        if (redis == null) {
            return Mono.empty();
        }
        return redis.delete(k).then()
                .onErrorResume(e -> Mono.empty());
    }

    private record Cached(String content, long version, long builtThrough) {}

    private static Profile decode(String json, MemoryScope scope) {
        try {
            var c = JSON.readValue(json, Cached.class);
            return new Profile(scope, c.content(), c.version(), c.builtThrough());
        } catch (Exception e) {
            return null;
        }
    }
}
