package io.github.sukimiya.agileagent.llmconfig;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import io.github.sukimiya.agileagent.auth.AuthorizationService;
import io.github.sukimiya.agileagent.llmconfig.LlmProviderPresetRepository.LlmProviderPresetRow;
import io.github.sukimiya.agileagent.llmconfig.TenantLlmConfigRepository.TenantLlmConfigRow;
import io.github.sukimiya.agileagent.service.TenantLlmClientResolver;
import io.github.sukimiya.agileagent.spi.identity.IdentityContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.MediaType;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

@Service
public class TenantLlmConfigService {

    private final LlmProviderPresetRepository presets;
    private final TenantLlmConfigRepository configs;
    private final AuthorizationService authorization;
    private final ApiKeyCrypto crypto;
    private final TenantLlmClientResolver resolver;
    private final WebClient.Builder webClientBuilder;
    private final Clock clock;

    @Autowired
    public TenantLlmConfigService(LlmProviderPresetRepository presets, TenantLlmConfigRepository configs,
            AuthorizationService authorization, ApiKeyCrypto crypto, TenantLlmClientResolver resolver,
            WebClient.Builder webClientBuilder) {
        this(presets, configs, authorization, crypto, resolver, webClientBuilder, Clock.systemUTC());
    }

    TenantLlmConfigService(LlmProviderPresetRepository presets, TenantLlmConfigRepository configs,
            AuthorizationService authorization, ApiKeyCrypto crypto, TenantLlmClientResolver resolver,
            WebClient.Builder webClientBuilder, Clock clock) {
        this.presets = presets;
        this.configs = configs;
        this.authorization = authorization;
        this.crypto = crypto;
        this.resolver = resolver;
        this.webClientBuilder = webClientBuilder;
        this.clock = clock;
    }

    public Mono<ConfigResponse> get(IdentityContext identity) {
        String tenantId = requireReadableTenant(identity);
        return configs.findByTenantId(tenantId)
                .switchIfEmpty(Mono.error(LlmConfigValidationException.notFound()))
                .flatMap(config -> presets.findById(config.presetId())
                        .switchIfEmpty(Mono.error(LlmConfigValidationException.notFound()))
                        .map(preset -> response(preset)));
    }

    public Mono<ConfigResponse> save(IdentityContext identity, ConfigInput input) {
        String tenantId = authorization.requireTenant(identity);
        authorization.requireTenantAdmin(identity);
        if (input == null || blank(input.presetId()) || blank(input.apiKey())) {
            return Mono.error(LlmConfigValidationException.invalidInput());
        }
        String presetId = input.presetId().trim();
        String apiKey = input.apiKey();
        return presets.findById(presetId)
                .switchIfEmpty(Mono.error(LlmConfigValidationException.presetNotFound()))
                .flatMap(preset -> configs.findByTenantId(tenantId)
                        .map(Optional::of)
                        .defaultIfEmpty(Optional.empty())
                        .flatMap(existing -> probe(preset, apiKey)
                                .then(Mono.fromCallable(() -> crypto.encrypt(apiKey)))
                                .flatMap(encrypted -> persist(tenantId, preset, encrypted, existing))));
    }

    public Mono<Void> delete(IdentityContext identity) {
        String tenantId = authorization.requireTenant(identity);
        authorization.requireTenantAdmin(identity);
        return configs.findByTenantId(tenantId)
                .switchIfEmpty(Mono.error(LlmConfigValidationException.notFound()))
                .flatMap(existing -> configs.deleteByTenantId(tenantId)
                        .flatMap(rows -> rows == 1
                                ? Mono.fromRunnable(() -> resolver.evict(tenantId))
                                : Mono.error(LlmConfigValidationException.notFound())));
    }

    private Mono<ConfigResponse> persist(String tenantId, LlmProviderPresetRow preset, String encrypted,
            Optional<TenantLlmConfigRow> existing) {
        LocalDateTime now = LocalDateTime.ofInstant(clock.instant(), clock.getZone());
        TenantLlmConfigRow row = existing
                .map(old -> new TenantLlmConfigRow(old.id(), tenantId, preset.id(), encrypted, old.createdAt(), now))
                .orElseGet(() -> new TenantLlmConfigRow(UUID.randomUUID().toString(), tenantId, preset.id(),
                        encrypted, now, now));
        return configs.upsert(row)
                .then(Mono.fromRunnable(() -> resolver.evict(tenantId)))
                .thenReturn(response(preset))
                .onErrorMap(error -> error instanceof LlmConfigValidationException
                        ? error : LlmConfigValidationException.saveFailed());
    }

    private Mono<Void> probe(LlmProviderPresetRow preset, String apiKey) {
        String baseUrl = preset.baseUrl().endsWith("/") ? preset.baseUrl() : preset.baseUrl() + "/";
        WebClient client = webClientBuilder.clone().baseUrl(baseUrl).build();
        var body = new ProbeRequest(preset.model(), List.of(new ProbeMessage("user", "Reply with OK.")), true);
        return client.post()
                .uri("chat/completions")
                .accept(MediaType.TEXT_EVENT_STREAM)
                .headers(headers -> headers.setBearerAuth(apiKey))
                .bodyValue(body)
                .exchangeToMono(response -> {
                    HttpStatusCode status = response.statusCode();
                    if (!status.is2xxSuccessful()) {
                        return response.bodyToFlux(org.springframework.core.io.buffer.DataBuffer.class)
                                .doOnNext(DataBufferUtils::release)
                                .take(1)
                                .then(Mono.error(LlmConfigValidationException.providerRejected(status.value())));
                    }
                    return response.bodyToFlux(org.springframework.core.io.buffer.DataBuffer.class)
                            .doOnNext(DataBufferUtils::release)
                            .take(1)
                            .then();
                })
                .timeout(Duration.ofSeconds(10))
                .onErrorMap(error -> error instanceof LlmConfigValidationException
                        ? error : LlmConfigValidationException.providerUnavailable());
    }

    private String requireReadableTenant(IdentityContext identity) {
        if (identity == null || (!identity.tenantAdmin() && !identity.platformAdmin())) {
            throw new io.github.sukimiya.agileagent.web.ForbiddenException();
        }
        return authorization.requireTenant(identity);
    }

    private static ConfigResponse response(LlmProviderPresetRow preset) {
        return new ConfigResponse(preset.id(), preset.name(), preset.model(), "********");
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }

    public record ConfigInput(String presetId, String apiKey) {
        @Override
        public String toString() {
            return "ConfigInput[presetId=" + presetId + ", apiKey=[REDACTED]]";
        }
    }

    public record ConfigResponse(String presetId, String presetName, String model, String apiKeyMasked) { }

    private record ProbeRequest(String model, List<ProbeMessage> messages, boolean stream) { }

    private record ProbeMessage(String role, String content) { }
}
