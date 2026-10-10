package io.github.sukimiya.agileagent.llmconfig;

import java.net.URI;
import java.net.URISyntaxException;
import java.time.Clock;
import java.time.LocalDateTime;

import io.github.sukimiya.agileagent.auth.AuthorizationService;
import io.github.sukimiya.agileagent.llmconfig.LlmProviderPresetRepository.LlmProviderPresetRow;
import io.github.sukimiya.agileagent.service.TenantLlmClientResolver;
import io.github.sukimiya.agileagent.spi.identity.IdentityContext;
import io.github.sukimiya.agileagent.util.NonBlockingIds;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Service
public class LlmProviderPresetService {

    private final LlmProviderPresetRepository repository;
    private final AuthorizationService authorization;
    private final TenantLlmClientResolver resolver;
    private final Clock clock;
    private final boolean allowHttp;

    @Autowired
    public LlmProviderPresetService(LlmProviderPresetRepository repository,
            AuthorizationService authorization, TenantLlmClientResolver resolver,
            @Value("${llm.provider-preset.allow-http:false}") boolean allowHttp) {
        this(repository, authorization, resolver, Clock.systemDefaultZone(), allowHttp);
    }

    LlmProviderPresetService(LlmProviderPresetRepository repository,
            AuthorizationService authorization, TenantLlmClientResolver resolver, Clock clock, boolean allowHttp) {
        this.repository = repository;
        this.authorization = authorization;
        this.resolver = resolver;
        this.clock = clock;
        this.allowHttp = allowHttp;
    }

    public Flux<LlmProviderPresetRow> list(IdentityContext identity) {
        if (identity == null) {
            throw new IllegalArgumentException("authenticated identity required");
        }
        return repository.findAll();
    }

    public Mono<LlmProviderPresetRow> create(IdentityContext identity, PresetInput input) {
        authorization.requirePlatformAdmin(identity);
        var normalized = validate(input);
        var now = LocalDateTime.ofInstant(clock.instant(), clock.getZone());
        var row = new LlmProviderPresetRow(NonBlockingIds.uuid(), normalized.name(), normalized.baseUrl(),
                normalized.model(), normalized.balancePath(), identity.subject(), now, now);
        return repository.insert(row).thenReturn(row);
    }

    public Mono<LlmProviderPresetRow> update(IdentityContext identity, String id, PresetInput input) {
        authorization.requirePlatformAdmin(identity);
        var normalized = validate(input);
        return repository.findById(id)
                .switchIfEmpty(Mono.error(new LlmProviderPresetNotFoundException()))
                .flatMap(existing -> {
                    var updated = new LlmProviderPresetRow(existing.id(), normalized.name(), normalized.baseUrl(),
                            normalized.model(), normalized.balancePath(), existing.createdBySubject(),
                            existing.createdAt(), LocalDateTime.ofInstant(clock.instant(), clock.getZone()));
                    return repository.update(updated).flatMap(rows -> rows == 1
                            ? Mono.fromRunnable(resolver::evictAll).thenReturn(updated)
                            : Mono.error(new LlmProviderPresetNotFoundException()));
                });
    }

    public Mono<Void> delete(IdentityContext identity, String id) {
        authorization.requirePlatformAdmin(identity);
        return repository.deleteById(id).flatMap(rows -> rows == 1
                ? Mono.fromRunnable(resolver::evictAll)
                : Mono.error(new LlmProviderPresetNotFoundException()));
    }

    private NormalizedInput validate(PresetInput input) {
        if (input == null || blank(input.name()) || blank(input.baseUrl()) || blank(input.model())) {
            throw new LlmProviderPresetValidationException();
        }
        String name = input.name().trim();
        String baseUrl = input.baseUrl().trim();
        String model = input.model().trim();
        String balancePath = input.balancePath() == null || input.balancePath().isBlank()
                ? null : input.balancePath().trim();

        try {
            URI uri = new URI(baseUrl);
            String scheme = uri.getScheme();
            boolean supportedScheme = "https".equalsIgnoreCase(scheme)
                    || (allowHttp && "http".equalsIgnoreCase(scheme));
            if (!uri.isAbsolute() || !supportedScheme || uri.getHost() == null
                    || uri.getUserInfo() != null || uri.getQuery() != null || uri.getFragment() != null) {
                throw new LlmProviderPresetValidationException();
            }
        } catch (URISyntaxException exception) {
            throw new LlmProviderPresetValidationException();
        }

        if (balancePath != null) {
            try {
                URI path = new URI(balancePath);
                if (path.isAbsolute() || path.getRawAuthority() != null || path.getRawFragment() != null) {
                    throw new LlmProviderPresetValidationException();
                }
            } catch (URISyntaxException exception) {
                throw new LlmProviderPresetValidationException();
            }
        }
        return new NormalizedInput(name, baseUrl, model, balancePath);
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }

    public record PresetInput(String name, String baseUrl, String model, String balancePath) { }

    private record NormalizedInput(String name, String baseUrl, String model, String balancePath) { }
}
