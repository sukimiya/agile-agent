package io.github.sukimiya.agileagent.web;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeoutException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.MethodParameter;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.BindingContext;
import org.springframework.web.reactive.result.method.HandlerMethodArgumentResolver;
import org.springframework.web.server.ServerWebExchange;

import io.github.sukimiya.agileagent.service.AuthSessionStore;
import io.github.sukimiya.agileagent.service.OAuth2Client;
import io.github.sukimiya.agileagent.util.NonBlockingIds;

import reactor.core.publisher.Mono;

@Component
public class CurrentUserArgumentResolver implements HandlerMethodArgumentResolver {

    private static final Logger log = LoggerFactory.getLogger(CurrentUserArgumentResolver.class);

    private static final Duration REFRESH_SKEW = Duration.ofSeconds(30);
    private static final Duration IDENTITY_FRESHNESS = Duration.ofMinutes(5);
    private static final String REFRESH_LOCK_PREFIX = "oauth2refreshlock:";
    // The whole invalidation/refresh/userinfo/persist owner path is capped at 75s, SETNX at 5s,
    // and owner-scoped lock cleanup at 10s. The 120s lease leaves at least 30s of margin.
    static final Duration LOCK_ACQUIRE_TIMEOUT = Duration.ofSeconds(5);
    static final Duration LOCKED_OPERATION_TIMEOUT = Duration.ofSeconds(75);
    static final Duration SESSION_INVALIDATION_TIMEOUT = Duration.ofSeconds(10);
    static final Duration LOCK_CLEANUP_TIMEOUT = Duration.ofSeconds(10);
    static final Duration REFRESH_LOCK_TTL = Duration.ofSeconds(120);
    private static final Duration WINNER_POLL_INTERVAL = Duration.ofMillis(100);
    // Polling is demand-driven (not a fixed-rate timer); this cap also bounds stalled Redis reads.
    private static final int WINNER_POLL_COUNT = 1_000;
    static final Duration WINNER_WAIT_TIMEOUT = Duration.ofSeconds(105);
    private static final RedisScript<Long> RELEASE_LOCK_SCRIPT = new DefaultRedisScript<>(
            "if redis.call('get', KEYS[1]) == ARGV[1] then return redis.call('del', KEYS[1]) else return 0 end",
            Long.class);

    private final AuthSessionStore sessions;
    private final OAuth2Client oauth2;
    private final ReactiveStringRedisTemplate redis;

    public CurrentUserArgumentResolver(AuthSessionStore sessions, OAuth2Client oauth2,
                                        ReactiveStringRedisTemplate redis) {
        this.sessions = sessions;
        this.oauth2 = oauth2;
        this.redis = redis;
    }

    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        return parameter.hasParameterAnnotation(CurrentUser.class);
    }

    @Override
    public Mono<Object> resolveArgument(MethodParameter parameter, BindingContext bindingContext,
                                         ServerWebExchange exchange) {
        String header = exchange.getRequest().getHeaders().getFirst("Authorization");
        String token = header != null && header.startsWith("Bearer ") ? header.substring(7) : null;
        if (token == null) return Mono.error(new UnauthorizedException());
        return sessions.resolve(token)
                .flatMap(user -> refreshIfRequired(token, user))
                // A near-expiry owner replaces the old session with a refresh generation before
                // consuming its refresh token. Losers wait for the persisted winner.
                .switchIfEmpty(Mono.defer(() -> awaitWinner(token, REFRESH_LOCK_PREFIX + token)))
                .cast(Object.class);
    }

    /**
     * Token validity and identity freshness share one per-app-session single-flight. A session is
     * returned only when both its access token and mapped identity are current.
     */
    private Mono<AuthSessionStore.AuthUser> refreshIfRequired(String token, AuthSessionStore.AuthUser user) {
        if (isReadyToReturn(user, Instant.now())) {
            return Mono.just(user);
        }
        String lockKey = REFRESH_LOCK_PREFIX + token;
        String lockOwner = NonBlockingIds.uuid();
        return redis.opsForValue().setIfAbsent(lockKey, lockOwner, REFRESH_LOCK_TTL)
                .defaultIfEmpty(Boolean.FALSE)
                .timeout(LOCK_ACQUIRE_TIMEOUT)
                // SETNX may have executed before its response timed out. Compare-delete only our
                // opaque owner value so this cleanup cannot release a successor's lease.
                .onErrorResume(error -> releaseRefreshLock(lockKey, lockOwner).then(Mono.error(error)))
                .flatMap(acquired -> Boolean.TRUE.equals(acquired)
                        ? refreshAsLockOwner(token, lockKey, lockOwner)
                        : awaitWinner(token, lockKey));
    }

    /** Every terminal path after SETNX, including the second Redis read, is cleaned up by this owner. */
    private Mono<AuthSessionStore.AuthUser> refreshAsLockOwner(String token, String lockKey, String lockOwner) {
        Mono<AuthSessionStore.AuthUser> work = sessions.resolve(token)
                .switchIfEmpty(Mono.error(new UnauthorizedException()))
                .flatMap(current -> isReadyToReturn(current, Instant.now())
                        ? Mono.just(current)
                        : refreshAndPersist(token, current, lockOwner));

        return work.timeout(LOCKED_OPERATION_TIMEOUT)
                .flatMap(current -> releaseRefreshLock(lockKey, lockOwner).thenReturn(current))
                .onErrorResume(error -> {
                    return releaseRefreshLock(lockKey, lockOwner).then(Mono.error(error));
                })
                .doOnError(error -> log.warn("OAuth2 session refresh or identity update failed"))
                .onErrorMap(OAuth2Client.OAuth2AuthException.class, error -> new UnauthorizedException())
                .onErrorMap(AuthSessionStore.SessionLoggedOutException.class, error -> new UnauthorizedException());
    }

    private Mono<AuthSessionStore.AuthUser> refreshAndPersist(String token, AuthSessionStore.AuthUser user,
                                                              String lockOwner) {
        Instant now = Instant.now();
        boolean refreshAccessToken = isAccessTokenNearExpiry(user, now);
        Mono<TokenFamily> tokenFamily = refreshAccessToken
                // A provider may irreversibly rotate the refresh token. Replace the session with
                // this owner's generation before invoking it; logout can atomically replace that
                // marker, and neither invalidation nor persistence may overwrite the logout fence.
                ? sessions.beginRefresh(token, lockOwner, REFRESH_LOCK_TTL)
                        .timeout(SESSION_INVALIDATION_TIMEOUT)
                        .then(Mono.defer(() -> oauth2.refresh(user.refreshToken())))
                        .switchIfEmpty(Mono.error(new OAuth2Client.OAuth2UnavailableException(
                                "OAuth2 token provider response was empty")))
                        .map(tokens -> {
                            String refreshToken = tokens.refreshToken();
                            if (tokens.accessToken() == null || tokens.accessToken().isBlank()
                                    || tokens.expiresInSeconds() <= REFRESH_SKEW.toSeconds()) {
                                throw new OAuth2Client.OAuth2UnavailableException(
                                        "OAuth2 token provider response was invalid");
                            }
                            refreshToken = refreshToken == null || refreshToken.isBlank()
                                    ? user.refreshToken() : refreshToken;
                            return new TokenFamily(tokens.accessToken(), refreshToken,
                                    Instant.now().plusSeconds(tokens.expiresInSeconds()));
                        })
                : Mono.just(new TokenFamily(user.accessToken(), user.refreshToken(), user.expiresAt()));

        return tokenFamily.flatMap(tokens -> oauth2.fetchIdentity(tokens.accessToken())
                .switchIfEmpty(Mono.error(new OAuth2Client.OAuth2UnavailableException(
                        "OAuth2 userinfo response was empty")))
                .map(identity -> {
                    if (!user.subject().equals(identity.subject())) {
                        throw new OAuth2Client.OAuth2AuthException("userinfo subject changed for current session");
                    }
                    return new AuthSessionStore.AuthUser(identity.subject(), identity.displayName(),
                            tokens.accessToken(), tokens.refreshToken(), tokens.expiresAt(), identity, Instant.now());
                })
                .flatMap(refreshed -> (refreshAccessToken
                        ? sessions.updateAfterRefresh(token, lockOwner, refreshed)
                        : sessions.update(token, refreshed)).thenReturn(refreshed))
                .flatMap(refreshed -> isReadyToReturn(refreshed, Instant.now())
                        ? Mono.just(refreshed)
                        : Mono.error(new OAuth2Client.OAuth2UnavailableException(
                                "OAuth2 token provider returned a near-expiry access token"))));
    }

    /**
     * A lock loser repeatedly re-reads Redis. It may return only the persisted winner's record,
     * never the snapshot it loaded before the lock race. If the lock disappears without a ready
     * session, fail closed; the loser must not race a second refresh or return stale privileges.
     */
    private Mono<AuthSessionStore.AuthUser> awaitWinner(String token, String lockKey) {
        return Mono.defer(() -> sessions.resolve(token)
                        .map(Optional::of)
                        .defaultIfEmpty(Optional.empty())
                        .flatMap(maybeCurrent -> {
                            if (maybeCurrent.isPresent()
                                    && isReadyToReturn(maybeCurrent.get(), Instant.now())) {
                                return Mono.just(maybeCurrent.get());
                            }
                            return redis.hasKey(lockKey)
                                    .defaultIfEmpty(Boolean.FALSE)
                                    .flatMap(lockExists -> {
                                        if (Boolean.TRUE.equals(lockExists)) {
                                            return Mono.empty();
                                        }
                                        if (maybeCurrent.isEmpty()) {
                                            return Mono.error(new UnauthorizedException());
                                        }
                                        return Mono.error(new OAuth2Client.OAuth2UnavailableException(
                                                "OAuth2 session refresh did not complete"));
                                    });
                        }))
                .repeatWhenEmpty(WINNER_POLL_COUNT, retries -> retries.delayElements(WINNER_POLL_INTERVAL))
                .switchIfEmpty(Mono.error(new OAuth2Client.OAuth2UnavailableException(
                        "OAuth2 session refresh did not complete")))
                .timeout(WINNER_WAIT_TIMEOUT)
                .onErrorMap(TimeoutException.class, error -> new OAuth2Client.OAuth2UnavailableException(
                        "OAuth2 session refresh did not complete"));
    }

    private static boolean isReadyToReturn(AuthSessionStore.AuthUser user, Instant now) {
        return !isAccessTokenNearExpiry(user, now) && isIdentityFresh(user, now);
    }

    private static boolean isAccessTokenNearExpiry(AuthSessionStore.AuthUser user, Instant now) {
        return !user.expiresAt().isAfter(now.plus(REFRESH_SKEW));
    }

    private static boolean isIdentityFresh(AuthSessionStore.AuthUser user, Instant now) {
        Instant fetchedAt = user.identityFetchedAt();
        return fetchedAt != null && !fetchedAt.isAfter(now) && now.isBefore(fetchedAt.plus(IDENTITY_FRESHNESS));
    }

    private record TokenFamily(String accessToken, String refreshToken, Instant expiresAt) {}

    private Mono<Void> releaseRefreshLock(String lockKey, String lockOwner) {
        return redis.execute(RELEASE_LOCK_SCRIPT, List.of(lockKey), lockOwner)
                .next()
                .then()
                .timeout(LOCK_CLEANUP_TIMEOUT)
                .onErrorResume(error -> {
                    log.warn("OAuth2 refresh lock cleanup failed");
                    return Mono.empty();
                });
    }
}
