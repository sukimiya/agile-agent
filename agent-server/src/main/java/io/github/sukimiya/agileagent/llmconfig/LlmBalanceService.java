package io.github.sukimiya.agileagent.llmconfig;

import java.time.Duration;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
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
    private final LlmBalanceProperties properties;

    @Autowired
    public LlmBalanceService(AuthorizationService authorization, TenantLlmClientResolver resolver,
                             WebClient.Builder webClientBuilder, LlmBalanceProperties properties) {
        this.authorization = authorization;
        this.resolver = resolver;
        this.webClientBuilder = webClientBuilder;
        this.properties = properties;
    }

    /** Tests that do not care about the configured currency default. */
    public LlmBalanceService(AuthorizationService authorization, TenantLlmClientResolver resolver,
                             WebClient.Builder webClientBuilder) {
        this(authorization, resolver, webClientBuilder, new LlmBalanceProperties());
    }

    public Mono<List<BalanceAmount>> getBalance(IdentityContext identity) {
        return Mono.defer(() -> {
            String tenantId = authorization.requireTenant(identity);
            if (!identity.tenantAdmin() && !identity.platformAdmin()) {
                return Mono.error(new ForbiddenException());
            }
            return resolver.resolveBalance(tenantId).flatMap(this::requestBalance);
        });
    }

    private Mono<List<BalanceAmount>> requestBalance(TenantLlmClientResolver.BalanceCredentials resolved) {
        final var target = BalanceEndpoint.resolve(resolved.preset(), resolved.teamId());
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
                            .switchIfEmpty(Mono.error(LlmBalanceException.providerError()))
                            .map(body -> BalanceResponseReader.read(body, resolved.preset(), properties));
                })
                .timeout(REQUEST_TIMEOUT)
                .onErrorMap(error -> error instanceof LlmBalanceException
                        ? error : LlmBalanceException.providerError());
    }

    /** One balance line shown to the caller. {@code balance} is already scaled. */
    public record BalanceAmount(String currency, String balance) {}
}
