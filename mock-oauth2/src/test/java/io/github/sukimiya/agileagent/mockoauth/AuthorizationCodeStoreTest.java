package io.github.sukimiya.agileagent.mockoauth;

import org.junit.jupiter.api.Test;

import java.net.URI;
import java.time.Clock;
import java.time.Instant;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AuthorizationCodeStoreTest {
    private static final String VERIFIER = "dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk";
    private static final String CHALLENGE = "E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM";
    private static final URI REDIRECT = URI.create("https://client.example/callback");

    @Test
    void bindsCodeToClientRedirectAndVerifierAndAllowsOneExchange() {
        AuthorizationCodeStore store = new AuthorizationCodeStore(Clock.systemUTC(), new java.security.SecureRandom());
        String code = store.issue("client-a", REDIRECT, "user-a", CHALLENGE, "S256");

        assertThrows(AuthorizationCodeStore.InvalidAuthorizationCodeException.class,
                () -> store.consume(code, "client-b", REDIRECT, VERIFIER));
        assertThrows(AuthorizationCodeStore.InvalidAuthorizationCodeException.class,
                () -> store.consume(code, "client-a", URI.create("https://other.example/callback"), VERIFIER));
        assertThrows(AuthorizationCodeStore.InvalidAuthorizationCodeException.class,
                () -> store.consume(code, "client-a", URI.create("HTTPS://CLIENT.EXAMPLE/callback"), VERIFIER));
        assertThrows(AuthorizationCodeStore.InvalidAuthorizationCodeException.class,
                () -> store.consume(code, "client-a", REDIRECT, "wrong-verifier"));
        assertEquals("user-a", store.consume(code, "client-a", REDIRECT, VERIFIER));
        assertThrows(AuthorizationCodeStore.InvalidAuthorizationCodeException.class,
                () -> store.consume(code, "client-a", REDIRECT, VERIFIER));
    }

    @Test
    void rejectsExpiredCode() {
        MutableClock clock = new MutableClock(Instant.parse("2026-10-10T00:00:00Z"));
        AuthorizationCodeStore store = new AuthorizationCodeStore(clock, new java.security.SecureRandom());
        String code = store.issue("client-a", REDIRECT, "user-a", CHALLENGE, "S256");
        clock.advanceSeconds(600);

        assertThrows(AuthorizationCodeStore.InvalidAuthorizationCodeException.class,
                () -> store.consume(code, "client-a", REDIRECT, VERIFIER));
    }

    @Test
    void invalidBindingDoesNotRevealAuthorizationCode() {
        AuthorizationCodeStore store = new AuthorizationCodeStore(Clock.systemUTC(), new java.security.SecureRandom());
        String code = store.issue("client-a", REDIRECT, "user-a", CHALLENGE, "S256");

        var exception = assertThrows(AuthorizationCodeStore.InvalidAuthorizationCodeException.class,
                () -> store.consume("some-other-code", "client-a", REDIRECT, VERIFIER));
        org.junit.jupiter.api.Assertions.assertFalse(exception.getMessage().contains("some-other-code"));
        assertEquals("user-a", store.consume(code, "client-a", REDIRECT, VERIFIER));
    }

    @Test
    void concurrentExchangeHasOnlyOneWinner() throws Exception {
        AuthorizationCodeStore store = new AuthorizationCodeStore(Clock.systemUTC(), new java.security.SecureRandom());
        String code = store.issue("client-a", REDIRECT, "user-a", CHALLENGE, "S256");
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger successes = new AtomicInteger();
        try (var pool = Executors.newFixedThreadPool(2)) {
            for (int i = 0; i < 2; i++) {
                pool.submit(() -> {
                    ready.countDown();
                    start.await();
                    try {
                        store.consume(code, "client-a", REDIRECT, VERIFIER);
                        successes.incrementAndGet();
                    } catch (AuthorizationCodeStore.InvalidAuthorizationCodeException ignored) {
                        // Exactly one exchange is expected to consume the code.
                    }
                    return null;
                });
            }
            ready.await();
            start.countDown();
        }
        assertEquals(1, successes.get());
    }
}
