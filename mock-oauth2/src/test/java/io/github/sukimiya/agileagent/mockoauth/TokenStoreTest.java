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
        TokenStore.TokenPair first = store.issue("tenant-a-user");

        TokenStore.TokenPair second = store.refresh(first.refreshToken());
        assertNotEquals(first.refreshToken(), second.refreshToken());
        assertThrows(TokenStore.InvalidTokenException.class, () -> store.refresh(first.refreshToken()));
        assertTrue(store.findIdentityByAccessToken(second.accessToken()).isEmpty());
    }

    @Test
    void replayingSpentRefreshRevokesRotatedAccessAndRefreshTokens() {
        TokenStore store = new TokenStore(new MockIdentityCatalog(), new MutableClock(Instant.EPOCH),
                new java.security.SecureRandom());
        TokenStore.TokenPair first = store.issue("tenant-a-user");
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
        TokenStore.TokenPair first = store.issue("tenant-a-user");
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
        TokenStore.TokenPair tokens = store.issue("tenant-a-user");
        clock.advanceSeconds(3600);

        assertTrue(store.findIdentityByAccessToken(tokens.accessToken()).isEmpty());
    }

    @Test
    void revocationInvalidatesBothAccessAndRefreshTokens() {
        TokenStore store = new TokenStore(new MockIdentityCatalog(), new MutableClock(Instant.EPOCH),
                new java.security.SecureRandom());
        TokenStore.TokenPair tokens = store.issue("tenant-a-user");
        store.revoke(tokens.refreshToken());

        assertTrue(store.findIdentityByAccessToken(tokens.accessToken()).isEmpty());
        assertThrows(TokenStore.InvalidTokenException.class, () -> store.refresh(tokens.refreshToken()));
    }

    @Test
    void revokingByAccessTokenInvalidatesItsRefreshToken() {
        TokenStore store = new TokenStore(new MockIdentityCatalog(), new MutableClock(Instant.EPOCH),
                new java.security.SecureRandom());
        TokenStore.TokenPair tokens = store.issue("tenant-a-user");
        store.revoke(tokens.accessToken());

        assertTrue(store.findIdentityByAccessToken(tokens.accessToken()).isEmpty());
        assertThrows(TokenStore.InvalidTokenException.class, () -> store.refresh(tokens.refreshToken()));
    }

    @Test
    void seededUserInfoHasMapperCompatibleClaimsAndDistinctTenantScopes() {
        MockIdentityCatalog catalog = new MockIdentityCatalog();
        assertEquals(5, catalog.identities().size());
        var platformAdmin = catalog.userInfo("platform-admin").orElseThrow();
        var tenantAAdmin = catalog.userInfo("tenant-a-admin").orElseThrow();
        var tenantAUser = catalog.userInfo("tenant-a-user").orElseThrow();
        var tenantBAdmin = catalog.userInfo("tenant-b-admin").orElseThrow();
        var tenantBUser = catalog.userInfo("tenant-b-user").orElseThrow();

        assertEquals("platform_admin", platformAdmin.at("/roles/0").textValue());
        assertEquals("tenant_admin", tenantAAdmin.at("/roles/0").textValue());
        assertEquals("tenant_admin", tenantBAdmin.at("/roles/0").textValue());
        assertEquals("user", tenantAUser.at("/roles/0").textValue());
        assertEquals("user", tenantBUser.at("/roles/0").textValue());
        assertEquals("tenant-a", tenantAUser.at("/tenant_id").textValue());
        assertEquals("tenant-b", tenantBUser.at("/tenant_id").textValue());
        assertTrue(tenantAAdmin.at("/manageable_org_ids").size() >= 2);
        assertTrue(tenantBAdmin.at("/manageable_org_ids").size() >= 2);
        assertTrue(!tenantAUser.at("/manageable_org_ids").equals(tenantAUser.at("/readable_org_ids")));
        assertTrue(!tenantBUser.at("/manageable_org_ids").equals(tenantBUser.at("/readable_org_ids")));
        assertTrue(!tenantAAdmin.at("/readable_org_ids").equals(tenantBAdmin.at("/readable_org_ids")));
    }

    @Test
    void concurrentRefreshReplayHasOnlyOneWinner() throws Exception {
        TokenStore store = new TokenStore(new MockIdentityCatalog(), new MutableClock(Instant.EPOCH),
                new java.security.SecureRandom());
        TokenStore.TokenPair tokens = store.issue("tenant-a-user");
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
