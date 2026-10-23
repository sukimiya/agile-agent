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
                normalized.model(), normalized.balancePath(), identity.subject(), now, now,
                normalized.contextWindowTokens(), normalized.maxOutputTokens());
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
                            existing.createdAt(), LocalDateTime.ofInstant(clock.instant(), clock.getZone()),
                            normalized.contextWindowTokens(), normalized.maxOutputTokens());
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
        validateWindow(input.contextWindowTokens(), input.maxOutputTokens());
        return new NormalizedInput(name, baseUrl, model, balancePath, input.contextWindowTokens(),
                input.maxOutputTokens());
    }

    /** Smallest context window a preset may declare: anything below cannot hold a prompt plus an answer. */
    static final int MIN_CONTEXT_WINDOW_TOKENS = 1024;

    /**
     * Both values are optional (unset means the configured default). A window must be at least
     * {@value #MIN_CONTEXT_WINDOW_TOKENS}; an output reserve must be positive and, with a window, smaller than it.
     */
    private static void validateWindow(Integer contextWindowTokens, Integer maxOutputTokens) {
        if (contextWindowTokens != null && contextWindowTokens < MIN_CONTEXT_WINDOW_TOKENS) {
            throw new LlmProviderPresetValidationException();
        }
        if (maxOutputTokens != null && (maxOutputTokens < 1
                || (contextWindowTokens != null && maxOutputTokens >= contextWindowTokens))) {
            throw new LlmProviderPresetValidationException();
        }
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }

    public record PresetInput(String name, String baseUrl, String model, String balancePath,
            Integer contextWindowTokens, Integer maxOutputTokens) {
        public PresetInput(String name, String baseUrl, String model, String balancePath) {
            this(name, baseUrl, model, balancePath, null, null);
        }
    }

    private record NormalizedInput(String name, String baseUrl, String model, String balancePath,
            Integer contextWindowTokens, Integer maxOutputTokens) { }
}
