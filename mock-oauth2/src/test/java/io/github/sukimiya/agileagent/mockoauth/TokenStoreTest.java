package io.github.sukimiya.agileagent.mockoauth;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TokenStoreTest {
    @Test
    void rotatesRefreshTokenAndRejectsReplay() {
        MutableClock clock = new MutableClock(Instant.parse("2026-10-10T00:00:00Z"));
        TokenStore store = new TokenStore(new MockIdentityCatalog(), clock, new java.security.SecureRandom());
        TokenStore.TokenPair first = store.issue("demo-user");

        TokenStore.TokenPair second = store.refresh(first.refreshToken());
        assertNotEquals(first.refreshToken(), second.refreshToken());
        assertThrows(TokenStore.InvalidTokenException.class, () -> store.refresh(first.refreshToken()));
        assertTrue(store.findIdentityByAccessToken(second.accessToken()).isEmpty());
    }

    @Test
    void replayingSpentRefreshRevokesRotatedAccessAndRefreshTokens() {
        TokenStore store = new TokenStore(new MockIdentityCatalog(), new MutableClock(Instant.EPOCH),
                new java.security.SecureRandom());
        TokenStore.TokenPair first = store.issue("demo-user");
        TokenStore.TokenPair rotated = store.refresh(first.refreshToken());

        var exception = assertThrows(TokenStore.InvalidTokenException.class,
                () -> store.refresh(first.refreshToken()));
        assertFalse(exception.getMessage().contains(first.refreshToken()));
        assertTrue(store.findIdentityByAccessToken(rotated.accessToken()).isEmpty());
        assertThrows(TokenStore.InvalidTokenException.class, () -> store.refresh(rotated.refreshToken()));
        assertFalse(store.findIdentityByAccessToken(first.accessToken()).isPresent());
    }

    @Test
    void replayingAnySpentAncestorAfterMultipleRotationsRevokesLatestPair() {
        TokenStore store = new TokenStore(new MockIdentityCatalog(), new MutableClock(Instant.EPOCH),
                new java.security.SecureRandom());
        TokenStore.TokenPair first = store.issue("demo-user");
        TokenStore.TokenPair second = store.refresh(first.refreshToken());
        TokenStore.TokenPair latest = store.refresh(second.refreshToken());

        assertThrows(TokenStore.InvalidTokenException.class, () -> store.refresh(first.refreshToken()));
        assertTrue(store.findIdentityByAccessToken(latest.accessToken()).isEmpty());
        assertThrows(TokenStore.InvalidTokenException.class, () -> store.refresh(latest.refreshToken()));
        assertFalse(store.toString().contains(latest.accessToken()));
        assertFalse(latest.toString().contains(latest.accessToken()));
        assertFalse(latest.toString().contains(latest.refreshToken()));
    }

    @Test
    void expiresAccessTokenAfterOneHour() {
        MutableClock clock = new MutableClock(Instant.parse("2026-10-10T00:00:00Z"));
        TokenStore store = new TokenStore(new MockIdentityCatalog(), clock, new java.security.SecureRandom());
        TokenStore.TokenPair tokens = store.issue("demo-user");
        clock.advanceSeconds(3600);

        assertTrue(store.findIdentityByAccessToken(tokens.accessToken()).isEmpty());
    }

    @Test
    void revocationInvalidatesBothAccessAndRefreshTokens() {
        TokenStore store = new TokenStore(new MockIdentityCatalog(), new MutableClock(Instant.EPOCH),
                new java.security.SecureRandom());
        TokenStore.TokenPair tokens = store.issue("demo-user");
        store.revoke(tokens.refreshToken());

        assertTrue(store.findIdentityByAccessToken(tokens.accessToken()).isEmpty());
        assertThrows(TokenStore.InvalidTokenException.class, () -> store.refresh(tokens.refreshToken()));
    }

    @Test
    void revokingByAccessTokenInvalidatesItsRefreshToken() {
        TokenStore store = new TokenStore(new MockIdentityCatalog(), new MutableClock(Instant.EPOCH),
                new java.security.SecureRandom());
        TokenStore.TokenPair tokens = store.issue("demo-user");
        store.revoke(tokens.accessToken());

        assertTrue(store.findIdentityByAccessToken(tokens.accessToken()).isEmpty());
        assertThrows(TokenStore.InvalidTokenException.class, () -> store.refresh(tokens.refreshToken()));
    }

    @Test
    void seededUserInfoHasMapperCompatibleClaimsAndDistinctTenantScopes() {
        MockIdentityCatalog catalog = new MockIdentityCatalog();
        var demo = catalog.userInfo("demo-user").orElseThrow();
        var analyst = catalog.userInfo("analyst-user").orElseThrow();

        assertEquals("demo-user", demo.at("/sub").textValue());
        assertTrue(demo.at("/primary_org/id").isTextual());
        assertTrue(demo.at("/manageable_org_ids").isArray());
        assertTrue(demo.at("/readable_org_ids").isArray());
        assertTrue(!demo.at("/tenant_id").textValue().equals(analyst.at("/tenant_id").textValue()));
        assertTrue(!demo.at("/manageable_org_ids").equals(analyst.at("/manageable_org_ids")));
        assertTrue(!demo.at("/readable_org_ids").equals(analyst.at("/readable_org_ids")));
    }

    @Test
    void concurrentRefreshReplayHasOnlyOneWinner() throws Exception {
        TokenStore store = new TokenStore(new MockIdentityCatalog(), new MutableClock(Instant.EPOCH),
                new java.security.SecureRandom());
        TokenStore.TokenPair tokens = store.issue("demo-user");
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger successes = new AtomicInteger();
        try (var pool = Executors.newFixedThreadPool(2)) {
            for (int i = 0; i < 2; i++) {
                pool.submit(() -> {
                    ready.countDown();
                    start.await();
                    try {
                        store.refresh(tokens.refreshToken());
                        successes.incrementAndGet();
                    } catch (TokenStore.InvalidTokenException ignored) {
                        // A rotated refresh token is single use.
                    }
                    return null;
                });
            }
            ready.await();
            start.countDown();
        }
        assertEquals(1, successes.get());
    }

    @Test
    void tokenPairDoesNotExposeSecretsInItsStringRepresentation() {
        TokenStore.TokenPair tokens = new TokenStore.TokenPair("access-secret", "refresh-secret", Instant.EPOCH);
        String printable = tokens.toString();

        assertFalse(printable.contains("access-secret"));
        assertFalse(printable.contains("refresh-secret"));
    }
}
