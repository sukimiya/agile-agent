package io.github.sukimiya.agileagent.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClient;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;

import io.github.sukimiya.agileagent.config.LlmSecretProperties;
import io.github.sukimiya.agileagent.llmconfig.ApiKeyCrypto;
import io.github.sukimiya.agileagent.llmconfig.LlmProviderPresetRepository;
import io.github.sukimiya.agileagent.llmconfig.TenantLlmConfigRepository;
import io.github.sukimiya.agileagent.service.LlmClient.Message;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;

class TenantLlmClientResolverTest {

    private static final String RAW_KEY = "sk-resolver-secret-value";
    private static final String SECRET = Base64.getEncoder().encodeToString(new byte[32]);
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 10, 0, 0);

    @Test
    void cachesResolvedClientPerTenantAndSeparatesTenants() {
        var fixture = new Fixture();
        fixture.add("tenant-a", "preset-a", "https://a.example/v1", "model-a", RAW_KEY);
        fixture.add("tenant-b", "preset-b", "https://b.example/v1", "model-b", "sk-other-secret");
        fixture.add("tenant-deleted", "deleted-preset", "https://deleted.example/v1", "old-model", RAW_KEY);
        when(fixture.presets.findById("deleted-preset")).thenReturn(Mono.empty());

        var clientA1 = fixture.resolver.resolve("tenant-a").block();
        var clientA2 = fixture.resolver.resolve("tenant-a").block();
        var clientB = fixture.resolver.resolve("tenant-b").block();

        assertThat(clientA1).isSameAs(clientA2);
        assertThat(clientA1).isNotSameAs(clientB);
        assertThat(fixture.configLookups.get()).isEqualTo(2);
        assertThat(fixture.resolver.resolvePresetAndKey("tenant-a").block().apiKey()).isEqualTo(RAW_KEY);
    }

    @Test
    void expirationReloadsConfigAndLocalEvictionsTakeEffect() {
        var fixture = new Fixture();
        fixture.add("tenant-a", "preset-a", "https://a.example/v1", "model-a", RAW_KEY);
        var first = fixture.resolver.resolve("tenant-a").block();

        fixture.clock.advance(Duration.ofMinutes(5));
        var second = fixture.resolver.resolve("tenant-a").block();
        fixture.resolver.evict("tenant-a");
        var third = fixture.resolver.resolve("tenant-a").block();
        fixture.resolver.evictAll();
        var fourth = fixture.resolver.resolve("tenant-a").block();

        assertThat(second).isNotSameAs(first);
        assertThat(third).isNotSameAs(second);
        assertThat(fourth).isNotSameAs(third);
        assertThat(fixture.configLookups.get()).isEqualTo(4);
    }

    @Test
    void missingBlankTenantConfigDeletedPresetAndMalformedKeyFailSanitized() {
        var fixture = new Fixture();
        fixture.add("tenant-malformed", "preset-bad", "https://bad.example/v1", "model", RAW_KEY);
        when(fixture.configs.findByTenantId("tenant-malformed"))
                .thenReturn(Mono.just(config("tenant-malformed", "preset-bad", "not-valid-ciphertext")));

        for (String tenant : new String[] { null, "", "  ", "tenant-missing", "tenant-deleted", "tenant-malformed" }) {
            assertThatThrownBy(() -> fixture.resolver.resolve(tenant).block())
                    .isInstanceOf(TenantLlmConfigRequiredException.class)
                    .hasMessage("LLM configuration is required")
                    .hasNoCause()
                    .hasToString("io.github.sukimiya.agileagent.service.TenantLlmConfigRequiredException: LLM configuration is required");
        }
    }

    @Test
    void failedResolutionIsNotCachedAndCanSucceedAfterRepositoryCorrection() {
        var fixture = new Fixture();
        fixture.add("tenant-a", "preset-a", "https://a.example/v1", "model-a", RAW_KEY);
        when(fixture.configs.findByTenantId("tenant-a"))
                .thenReturn(Mono.empty())
                .thenReturn(Mono.just(config("tenant-a", "preset-a", fixture.crypto.encrypt(RAW_KEY))));

        assertThatThrownBy(() -> fixture.resolver.resolve("tenant-a").block())
                .isInstanceOf(TenantLlmConfigRequiredException.class);
        assertThat(fixture.resolver.resolve("tenant-a").block()).isNotNull();
        verify(fixture.configs, times(2)).findByTenantId("tenant-a");
    }

    @Test
    void concurrentMissesShareOneConsistentResolvedClient() throws Exception {
        var fixture = new Fixture();
        fixture.add("tenant-a", "preset-a", "https://a.example/v1", "model-a", RAW_KEY);
        var start = new CountDownLatch(1);
        try (var pool = Executors.newFixedThreadPool(12)) {
            var futures = java.util.stream.IntStream.range(0, 12).mapToObj(i -> pool.submit(() -> {
                start.await();
                return fixture.resolver.resolve("tenant-a").block();
            })).toList();
            start.countDown();
            var clients = futures.stream().map(f -> {
                try { return f.get(5, TimeUnit.SECONDS); }
                catch (Exception e) { throw new RuntimeException(e); }
            }).toList();
            assertThat(clients).allMatch(client -> client == clients.getFirst());
        }
        assertThat(fixture.configLookups.get()).isEqualTo(1);
    }

    @Test
    void evictionPreventsAnOlderInFlightResolutionFromRepopulatingTheCache() {
        var fixture = new Fixture();
        fixture.add("tenant-a", "preset-a", "https://old.example/v1", "old-model", RAW_KEY);
        fixture.add("tenant-a", "preset-b", "https://new.example/v1", "new-model", "sk-new-secret");
        var oldConfig = Sinks.<TenantLlmConfigRepository.TenantLlmConfigRow>one();
        when(fixture.configs.findByTenantId("tenant-a"))
                .thenReturn(oldConfig.asMono(), Mono.just(config("tenant-a", "preset-b",
                        fixture.crypto.encrypt("sk-new-secret"))));

        var oldResolution = fixture.resolver.resolve("tenant-a").toFuture();
        fixture.resolver.evict("tenant-a");
        var newClient = fixture.resolver.resolve("tenant-a").block();
        oldConfig.tryEmitValue(config("tenant-a", "preset-a", fixture.crypto.encrypt(RAW_KEY)));
        oldResolution.join();

        assertThat(fixture.resolver.resolve("tenant-a").block()).isSameAs(newClient);
    }

    @Test
    void resolvedPresetAndKeyRedactsPlaintextAndClientUsesConfiguredValues() {
        var fixture = new Fixture();
        fixture.add("tenant-a", "preset-a", "https://a.example/v1", "preset-model", RAW_KEY);

        var resolved = fixture.resolver.resolvePresetAndKey("tenant-a").block();

        assertThat(resolved.preset().baseUrl()).isEqualTo("https://a.example/v1");
        assertThat(resolved.preset().model()).isEqualTo("preset-model");
        assertThat(resolved.apiKey()).isEqualTo(RAW_KEY);
        assertThat(resolved.toString()).doesNotContain(RAW_KEY);
        var client = fixture.resolver.resolve("tenant-a").block();
        assertThat(client.toString()).doesNotContain(RAW_KEY);
    }

    @Test
    void constructedClientSendsPresetModelAndDecryptedKeyOnlyToConfiguredProvider() throws Exception {
        try (var server = new MockWebServer()) {
            server.enqueue(new MockResponse().setBody("data: {\"choices\":[{\"delta\":{},\"finish_reason\":\"stop\"}]}\n\ndata: [DONE]\n\n")
                    .addHeader("Content-Type", "text/event-stream"));
            server.start();
            var fixture = new Fixture();
            fixture.add("tenant-http", "preset-http", server.url("/v1").toString(), "preset-model", RAW_KEY);

            fixture.resolver.resolve("tenant-http").block()
                    .chatStream(java.util.List.of(Message.user("hello")), java.util.List.of()).blockLast();

            var request = server.takeRequest(2, TimeUnit.SECONDS);
            assertThat(request).isNotNull();
            assertThat(request.getHeader("Authorization")).isEqualTo("Bearer " + RAW_KEY);
            assertThat(request.getBody().readUtf8()).contains("\"model\":\"preset-model\"");
        }
    }

    private static TenantLlmConfigRepository.TenantLlmConfigRow config(String tenant, String preset, String encrypted) {
        return new TenantLlmConfigRepository.TenantLlmConfigRow("id-" + tenant, tenant, preset, encrypted, NOW, NOW);
    }

    private static final class Fixture {
        final TenantLlmConfigRepository configs = mock(TenantLlmConfigRepository.class);
        final LlmProviderPresetRepository presets = mock(LlmProviderPresetRepository.class);
        final LlmSecretProperties secretProperties = secretProperties();
        final ApiKeyCrypto crypto = new ApiKeyCrypto(secretProperties);
        final MutableClock clock = new MutableClock(Instant.parse("2026-10-10T00:00:00Z"));
        final AtomicInteger configLookups = new AtomicInteger();
        final TenantLlmClientResolver resolver = new TenantLlmClientResolver(
                configs, presets, crypto, WebClient.builder(), clock, Duration.ofMinutes(5));

        void add(String tenant, String presetId, String baseUrl, String model, String key) {
            when(configs.findByTenantId(tenant)).thenAnswer(invocation -> {
                configLookups.incrementAndGet();
                return Mono.just(config(tenant, presetId, crypto.encrypt(key)));
            });
            when(presets.findById(presetId)).thenReturn(Mono.just(new LlmProviderPresetRepository.LlmProviderPresetRow(
                    presetId, "Preset", baseUrl, model, null, "admin", NOW, NOW)));
        }

        private static LlmSecretProperties secretProperties() {
            var properties = new LlmSecretProperties();
            properties.setSecret(SECRET);
            return properties;
        }
    }

    private static final class MutableClock extends Clock {
        private Instant instant;
        MutableClock(Instant instant) { this.instant = instant; }
        void advance(Duration duration) { instant = instant.plus(duration); }
        @Override public ZoneOffset getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(java.time.ZoneId zone) { return this; }
        @Override public Instant instant() { return instant; }
    }
}
