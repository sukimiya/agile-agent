package io.github.sukimiya.agileagent.llmconfig;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import java.time.LocalDateTime;
import java.util.Set;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClient;

import io.github.sukimiya.agileagent.auth.AuthorizationService;
import io.github.sukimiya.agileagent.llmconfig.LlmProviderPresetRepository.LlmProviderPresetRow;
import io.github.sukimiya.agileagent.service.TenantLlmClientResolver;
import io.github.sukimiya.agileagent.spi.identity.IdentityContext;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.SocketPolicy;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

class LlmBalanceServiceTest {

    private MockWebServer server;
    private TenantLlmClientResolver resolver;
    private LlmBalanceService service;

    @BeforeEach
    void setUp() throws Exception {
        server = new MockWebServer();
        server.start();
        resolver = mock(TenantLlmClientResolver.class);
        service = new LlmBalanceService(new AuthorizationService(), resolver, WebClient.builder());
    }

    @AfterEach
    void tearDown() throws Exception {
        server.shutdown();
    }

    private IdentityContext tenantAdmin() {
        return new IdentityContext("user-1", "User", "tenant-1", "Tenant", null, null,
                Set.of("tenant_admin"), Set.of(), Set.of(), false, true);
    }

    private LlmProviderPresetRow preset(String balancePath) {
        return new LlmProviderPresetRow("preset-1", "Provider", server.url("/v1").toString(), "model-1",
                balancePath, "admin", LocalDateTime.now(), LocalDateTime.now());
    }

    @Test
    void balanceUsesOnlyTenantBearerAndPresetRelativePath() throws Exception {
        when(resolver.resolvePresetAndKey("tenant-1"))
                .thenReturn(Mono.just(new TenantLlmClientResolver.PresetAndKey(preset("/balance"), "tenant-secret")));
        server.enqueue(new MockResponse().setHeader("Content-Type", "application/json")
                .setBody("{\"remaining\":12}"));

        StepVerifier.create(service.getBalance(tenantAdmin()))
                .assertNext(body -> assertThat(body.path("remaining").asInt()).isEqualTo(12))
                .verifyComplete();

        var request = server.takeRequest();
        assertThat(request.getMethod()).isEqualTo("GET");
        assertThat(request.getPath()).isEqualTo("/balance");
        assertThat(request.getHeader("Authorization")).isEqualTo("Bearer tenant-secret");
        assertThat(request.getRequestUrl().toString()).doesNotContain("tenant-secret");
        verify(resolver).resolvePresetAndKey("tenant-1");
    }

    @Test
    void unsupportedBalanceIsAStable404() {
        when(resolver.resolvePresetAndKey("tenant-1"))
                .thenReturn(Mono.just(new TenantLlmClientResolver.PresetAndKey(preset(null), "tenant-secret")));

        StepVerifier.create(service.getBalance(tenantAdmin()))
                .expectErrorSatisfies(error -> {
                    assertThat(error).hasFieldOrPropertyWithValue("status", 404);
                    assertThat(error).hasFieldOrPropertyWithValue("code", "LLM_BALANCE_UNSUPPORTED");
                }).verify();
    }

    @Test
    void providerFailureIsSanitizedAs502() {
        when(resolver.resolvePresetAndKey("tenant-1"))
                .thenReturn(Mono.just(new TenantLlmClientResolver.PresetAndKey(preset("balance"), "tenant-secret")));
        server.enqueue(new MockResponse().setResponseCode(503).setBody("tenant-secret echoed"));

        StepVerifier.create(service.getBalance(tenantAdmin()))
                .expectErrorSatisfies(error -> {
                    assertThat(error).hasFieldOrPropertyWithValue("status", 502);
                    assertThat(error.getMessage()).doesNotContain("tenant-secret", "echoed");
                }).verify();
    }

    @Test
    void invalidJsonAndNetworkFailureAreSanitizedAs502() {
        when(resolver.resolvePresetAndKey("tenant-1"))
                .thenReturn(Mono.just(new TenantLlmClientResolver.PresetAndKey(preset("balance"), "tenant-secret")));
        server.enqueue(new MockResponse().setHeader("Content-Type", "application/json").setBody("not-json"));
        StepVerifier.create(service.getBalance(tenantAdmin()))
                .expectErrorSatisfies(error -> assertThat(error).hasFieldOrPropertyWithValue("status", 502))
                .verify();

        server.enqueue(new MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AT_START));
        StepVerifier.create(service.getBalance(tenantAdmin()))
                .expectErrorSatisfies(error -> {
                    assertThat(error).hasFieldOrPropertyWithValue("status", 502);
                    assertThat(error.getMessage()).doesNotContain("tenant-secret");
                }).verify();
    }

    @Test
    void balancePathCannotReplaceProviderAuthority() {
        when(resolver.resolvePresetAndKey("tenant-1"))
                .thenReturn(Mono.just(new TenantLlmClientResolver.PresetAndKey(preset("//evil.example/balance"), "tenant-secret")));

        StepVerifier.create(service.getBalance(tenantAdmin()))
                .expectErrorSatisfies(error -> assertThat(error).hasFieldOrPropertyWithValue("status", 502))
                .verify();
        assertThat(server.getRequestCount()).isZero();
    }

    @Test
    void regularAndTenantlessUsersCannotReadBalance() {
        var regular = new IdentityContext("user-2", "User", "tenant-1", "Tenant", null, null,
                Set.of("member"), Set.of(), Set.of(), false, false);
        var tenantlessAdmin = new IdentityContext("user-3", "User", null, null, null, null,
                Set.of("platform_admin"), Set.of(), Set.of(), true, false);

        StepVerifier.create(service.getBalance(regular)).expectError(io.github.sukimiya.agileagent.web.ForbiddenException.class).verify();
        StepVerifier.create(service.getBalance(tenantlessAdmin)).expectError(io.github.sukimiya.agileagent.web.ForbiddenException.class).verify();
        verifyNoInteractions(resolver);
    }
}
