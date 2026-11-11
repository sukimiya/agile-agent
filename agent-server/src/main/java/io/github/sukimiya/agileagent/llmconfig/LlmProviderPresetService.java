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
                normalized.contextWindowTokens(), normalized.maxOutputTokens(), normalized.balanceBaseUrl(),
                normalized.balanceAmountPointer(), normalized.balanceCurrencyPointer(), normalized.balanceCurrency(),
                normalized.balanceDivisor(), normalized.balanceNegate());
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
                            normalized.contextWindowTokens(), normalized.maxOutputTokens(), normalized.balanceBaseUrl(),
                            normalized.balanceAmountPointer(), normalized.balanceCurrencyPointer(),
                            normalized.balanceCurrency(), normalized.balanceDivisor(), normalized.balanceNegate());
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
        if (input == null) {
            throw new LlmProviderPresetValidationException("preset", "Provider preset input is invalid");
        }
        if (blank(input.name())) {
            throw new LlmProviderPresetValidationException("name", "name is required");
        }
        if (blank(input.baseUrl())) {
            throw new LlmProviderPresetValidationException("baseUrl", "base URL is required");
        }
        if (blank(input.model())) {
            throw new LlmProviderPresetValidationException("model", "model is required");
        }
        String name = input.name().trim();
        String baseUrl = input.baseUrl().trim();
        String model = input.model().trim();
        String balancePath = blank(input.balancePath()) ? null : input.balancePath().trim();
        String balanceBaseUrl = blank(input.balanceBaseUrl()) ? null : input.balanceBaseUrl().trim();
        requireAbsoluteUrl("baseUrl", baseUrl, allowHttp);
        if (balanceBaseUrl != null) {
            requireAbsoluteUrl("balanceBaseUrl", balanceBaseUrl, false);
        }
        if (balancePath != null) {
            requireRelative("balancePath", balancePath);
        }
        String amountPointer = pointer("balanceAmountPointer", input.balanceAmountPointer());
        String currencyPointer = pointer("balanceCurrencyPointer", input.balanceCurrencyPointer());
        String currency = blank(input.balanceCurrency()) ? null : input.balanceCurrency().trim();
        if (input.balanceDivisor() != null && input.balanceDivisor() < 1) {
            throw new LlmProviderPresetValidationException("balanceDivisor", "balance divisor must be at least 1");
        }
        validateWindow(input.contextWindowTokens(), input.maxOutputTokens());
        return new NormalizedInput(name, baseUrl, model, balancePath, input.contextWindowTokens(),
                input.maxOutputTokens(), balanceBaseUrl, amountPointer, currencyPointer, currency,
                input.balanceDivisor(), input.balanceNegate());
    }

    private static void requireAbsoluteUrl(String field, String value, boolean allowHttp) {
        try {
            URI uri = new URI(value);
            String scheme = uri.getScheme();
            boolean supportedScheme = "https".equalsIgnoreCase(scheme)
                    || (allowHttp && "http".equalsIgnoreCase(scheme));
            if (!uri.isAbsolute() || !supportedScheme || uri.getHost() == null
                    || uri.getUserInfo() != null || uri.getQuery() != null || uri.getFragment() != null) {
                throw new LlmProviderPresetValidationException(field,
                        field + " must be an absolute https URL without user info, query, or fragment");
            }
        } catch (URISyntaxException exception) {
            throw new LlmProviderPresetValidationException(field, field + " is not a valid URL");
        }
    }

    private static void requireRelative(String field, String value) {
        try {
            URI path = new URI(value.replace(BalancePlaceholders.BRACE, "team"));
            if (path.isAbsolute() || path.getRawAuthority() != null || path.getRawFragment() != null) {
                throw new LlmProviderPresetValidationException(field,
                        "balance path must be a relative path without a host or fragment");
            }
        } catch (URISyntaxException exception) {
            throw new LlmProviderPresetValidationException(field, "balance path is not a valid URI");
        }
    }

    private static String pointer(String field, String value) {
        if (blank(value)) {
            return null;
        }
        String pointer = value.trim();
        if (!pointer.startsWith("/")) {
            throw new LlmProviderPresetValidationException(field, field + " must be a JSON pointer starting with /");
        }
        return pointer;
    }

    /** Smallest context window a preset may declare: anything below cannot hold a prompt plus an answer. */
    static final int MIN_CONTEXT_WINDOW_TOKENS = 1024;

    /**
     * Both values are optional (unset means the configured default). A window must be at least
     * {@value #MIN_CONTEXT_WINDOW_TOKENS}; an output reserve must be positive and, with a window, smaller than it.
     */
    private static void validateWindow(Integer contextWindowTokens, Integer maxOutputTokens) {
        if (contextWindowTokens != null && contextWindowTokens < MIN_CONTEXT_WINDOW_TOKENS) {
            throw new LlmProviderPresetValidationException("contextWindowTokens",
                    "context window must be at least " + MIN_CONTEXT_WINDOW_TOKENS);
        }
        if (maxOutputTokens != null && maxOutputTokens < 1) {
            throw new LlmProviderPresetValidationException("maxOutputTokens", "max output must be at least 1");
        }
        if (maxOutputTokens != null && contextWindowTokens != null && maxOutputTokens >= contextWindowTokens) {
            throw new LlmProviderPresetValidationException("maxOutputTokens",
                    "max output must be smaller than the context window");
        }
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }

    public record PresetInput(String name, String baseUrl, String model, String balancePath,
            Integer contextWindowTokens, Integer maxOutputTokens, String balanceBaseUrl,
            String balanceAmountPointer, String balanceCurrencyPointer, String balanceCurrency,
            Integer balanceDivisor, Boolean balanceNegate) {
        public PresetInput(String name, String baseUrl, String model, String balancePath,
                Integer contextWindowTokens, Integer maxOutputTokens) {
            this(name, baseUrl, model, balancePath, contextWindowTokens, maxOutputTokens,
                    null, null, null, null, null, null);
        }

        public PresetInput(String name, String baseUrl, String model, String balancePath) {
            this(name, baseUrl, model, balancePath, null, null);
        }
    }

    private record NormalizedInput(String name, String baseUrl, String model, String balancePath,
            Integer contextWindowTokens, Integer maxOutputTokens, String balanceBaseUrl,
            String balanceAmountPointer, String balanceCurrencyPointer, String balanceCurrency,
            Integer balanceDivisor, Boolean balanceNegate) { }
}
