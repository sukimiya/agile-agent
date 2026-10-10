package io.github.sukimiya.agileagent.service;

import java.time.Clock;
import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import io.github.sukimiya.agileagent.llmconfig.ApiKeyCrypto;
import io.github.sukimiya.agileagent.llmconfig.LlmProviderPresetRepository;
import io.github.sukimiya.agileagent.llmconfig.TenantLlmConfigRepository;
import reactor.core.publisher.Mono;

@Component
public final class TenantLlmClientResolver {

    private final TenantLlmConfigRepository configs;
    private final LlmProviderPresetRepository presets;
    private final ApiKeyCrypto crypto;
    private final WebClient.Builder webClientBuilder;
    private final TtlCache<String, ResolvedClient> cache;
    private final Runnable afterGenerationCheck;
    private final ConcurrentMap<String, Mono<ResolvedClient>> loads = new ConcurrentHashMap<>();
    private final AtomicLong evictionGeneration = new AtomicLong();

    @Autowired
    public TenantLlmClientResolver(
            TenantLlmConfigRepository configs,
            LlmProviderPresetRepository presets,
            ApiKeyCrypto crypto,
            WebClient.Builder webClientBuilder,
            @Value("${llm.client-cache.ttl:5m}") Duration ttl) {
        this(configs, presets, crypto, webClientBuilder, Clock.systemUTC(), ttl);
    }

    public TenantLlmClientResolver(
            TenantLlmConfigRepository configs,
            LlmProviderPresetRepository presets,
            ApiKeyCrypto crypto,
            WebClient.Builder webClientBuilder,
            Clock clock,
            Duration ttl) {
        this(configs, presets, crypto, webClientBuilder, clock, ttl, () -> { });
    }

    TenantLlmClientResolver(
            TenantLlmConfigRepository configs,
            LlmProviderPresetRepository presets,
            ApiKeyCrypto crypto,
            WebClient.Builder webClientBuilder,
            Clock clock,
            Duration ttl,
            Runnable afterGenerationCheck) {
        this.configs = configs;
        this.presets = presets;
        this.crypto = crypto;
        this.webClientBuilder = webClientBuilder;
        this.cache = new TtlCache<>(ttl, clock);
        this.afterGenerationCheck = afterGenerationCheck;
    }

    public Mono<LlmClient> resolve(String tenantId) {
        return resolved(tenantId).map(ResolvedClient::client);
    }

    public Mono<PresetAndKey> resolvePresetAndKey(String tenantId) {
        return resolved(tenantId).map(value -> new PresetAndKey(value.preset(), value.apiKey()));
    }

    public void evict(String tenantId) {
        if (tenantId != null) {
            synchronized (cache) {
                evictionGeneration.incrementAndGet();
                cache.evict(tenantId);
                loads.remove(tenantId);
            }
        }
    }

    public void evictAll() {
        synchronized (cache) {
            evictionGeneration.incrementAndGet();
            cache.evictAll();
            loads.clear();
        }
    }

    private Mono<ResolvedClient> resolved(String tenantId) {
        if (tenantId == null || tenantId.isBlank()) {
            return Mono.error(new TenantLlmConfigRequiredException());
        }
        return Mono.defer(() -> {
            var cached = cache.get(tenantId);
            if (cached.isPresent()) {
                return Mono.just(cached.get());
            }
            return loads.computeIfAbsent(tenantId, key -> {
                long generation = evictionGeneration.get();
                var self = new AtomicReference<Mono<ResolvedClient>>();
                Mono<ResolvedClient> loading = load(key)
                        .map(value -> {
                            synchronized (cache) {
                                boolean generationIsCurrent = evictionGeneration.get() == generation;
                                afterGenerationCheck.run();
                                return generationIsCurrent ? cache.putIfAbsent(key, value) : value;
                            }
                        })
                        .doFinally(signal -> loads.remove(key, self.get()))
                        .cache();
                self.set(loading);
                return loading;
            });
        });
    }

    private Mono<ResolvedClient> load(String tenantId) {
        return Mono.defer(() -> configs.findByTenantId(tenantId)
                        .switchIfEmpty(Mono.error(new TenantLlmConfigRequiredException()))
                        .flatMap(config -> presets.findById(config.presetId())
                                .switchIfEmpty(Mono.error(new TenantLlmConfigRequiredException()))
                                .map(preset -> {
                                    String apiKey = crypto.decrypt(config.apiKeyEncrypted());
                                    WebClient.Builder isolatedBuilder = webClientBuilder.clone();
                                    var client = new OpenAiCompatibleLlmClient(
                                            preset.baseUrl(), apiKey, preset.model(), isolatedBuilder);
                                    return new ResolvedClient(preset, apiKey, client);
                                })))
                .onErrorMap(error -> error instanceof TenantLlmConfigRequiredException
                        ? error
                        : new TenantLlmConfigRequiredException());
    }

    public record PresetAndKey(LlmProviderPresetRepository.LlmProviderPresetRow preset, String apiKey) {
        @Override
        public String toString() {
            return "PresetAndKey[preset=" + preset + ", apiKey=[REDACTED]]";
        }
    }

    private record ResolvedClient(
            LlmProviderPresetRepository.LlmProviderPresetRow preset,
            String apiKey,
            LlmClient client) { }
}
