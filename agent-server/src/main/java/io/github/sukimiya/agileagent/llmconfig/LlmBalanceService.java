package io.github.sukimiya.agileagent.llmconfig;

import java.net.URI;
import java.time.Duration;

import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import com.fasterxml.jackson.databind.JsonNode;
import io.github.sukimiya.agileagent.auth.AuthorizationService;
import io.github.sukimiya.agileagent.service.TenantLlmClientResolver;
import io.github.sukimiya.agileagent.spi.identity.IdentityContext;
import io.github.sukimiya.agileagent.web.ForbiddenException;
import reactor.core.publisher.Mono;

@Service
public class LlmBalanceService {

    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(10);

    private final AuthorizationService authorization;
    private final TenantLlmClientResolver resolver;
    private final WebClient.Builder webClientBuilder;

    public LlmBalanceService(AuthorizationService authorization, TenantLlmClientResolver resolver,
                             WebClient.Builder webClientBuilder) {
        this.authorization = authorization;
        this.resolver = resolver;
        this.webClientBuilder = webClientBuilder;
    }

    public Mono<JsonNode> getBalance(IdentityContext identity) {
        return Mono.defer(() -> {
            String tenantId = authorization.requireTenant(identity);
            if (!identity.tenantAdmin() && !identity.platformAdmin()) {
                return Mono.error(new ForbiddenException());
            }
            return resolver.resolvePresetAndKey(tenantId).flatMap(this::requestBalance);
        });
    }

    private Mono<JsonNode> requestBalance(TenantLlmClientResolver.PresetAndKey resolved) {
        var preset = resolved.preset();
        if (preset.balancePath() == null || preset.balancePath().isBlank()) {
            return Mono.error(LlmBalanceException.unsupported());
        }
        final URI target;
        try {
            URI base = URI.create(preset.baseUrl().endsWith("/") ? preset.baseUrl() : preset.baseUrl() + "/");
            URI relative = URI.create(preset.balancePath());
            if (relative.isAbsolute() || relative.getRawAuthority() != null || relative.getRawFragment() != null) {
                return Mono.error(LlmBalanceException.providerError());
            }
            target = base.resolve(relative);
            if (!sameAuthority(base, target)) return Mono.error(LlmBalanceException.providerError());
        } catch (RuntimeException error) {
            return Mono.error(LlmBalanceException.providerError());
        }

        WebClient client = webClientBuilder.clone().build();
        return client.get().uri(target)
                .headers(headers -> headers.setBearerAuth(resolved.apiKey()))
                .exchangeToMono(response -> {
                    HttpStatusCode status = response.statusCode();
                    if (!status.is2xxSuccessful()) {
                        return response.bodyToFlux(DataBuffer.class)
                                .doOnNext(DataBufferUtils::release)
                                .then(Mono.error(LlmBalanceException.providerError()));
                    }
                    return response.bodyToMono(JsonNode.class)
                            .switchIfEmpty(Mono.error(LlmBalanceException.providerError()));
                })
                .timeout(REQUEST_TIMEOUT)
                .onErrorMap(error -> error instanceof LlmBalanceException
                        ? error : LlmBalanceException.providerError());
    }

    private static boolean sameAuthority(URI base, URI target) {
        return base.getScheme().equalsIgnoreCase(target.getScheme())
                && base.getHost().equalsIgnoreCase(target.getHost())
                && base.getPort() == target.getPort()
                && target.getUserInfo() == null;
    }
}
