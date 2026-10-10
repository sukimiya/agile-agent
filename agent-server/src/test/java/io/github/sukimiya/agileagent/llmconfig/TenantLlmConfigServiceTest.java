package io.github.sukimiya.agileagent.llmconfig;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Set;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.sukimiya.agileagent.auth.AuthorizationService;
import io.github.sukimiya.agileagent.llmconfig.LlmProviderPresetRepository.LlmProviderPresetRow;
import io.github.sukimiya.agileagent.llmconfig.TenantLlmConfigRepository.TenantLlmConfigRow;
import io.github.sukimiya.agileagent.service.TenantLlmClientResolver;
import io.github.sukimiya.agileagent.spi.identity.IdentityContext;
import io.github.sukimiya.agileagent.web.ForbiddenException;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.SocketPolicy;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

@ExtendWith(MockitoExtension.class)
class TenantLlmConfigServiceTest {

    @Mock LlmProviderPresetRepository presets;
    @Mock TenantLlmConfigRepository configs;
    @Mock ApiKeyCrypto crypto;
    @Mock TenantLlmClientResolver resolver;

    private MockWebServer upstream;
    private TenantLlmConfigService service;
    private final IdentityContext tenantAdmin = identity("tenant-admin", "tenant-a", false, true);

    @BeforeEach
    void setUp() throws Exception {
        upstream = new MockWebServer();
        upstream.start();
        service = new TenantLlmConfigService(presets, configs, new AuthorizationService(), crypto, resolver,
                WebClient.builder(), Clock.fixed(Instant.parse("2026-10-10T01:02:03Z"), ZoneOffset.UTC));
    }

    @AfterEach
    void tearDown() throws Exception {
        upstream.shutdown();
    }

    @Test
    void getReturnsFixedMaskWithoutDecryptingAndUsesOnlyAuthenticatedTenant() {
        var existing = config("tenant-a", "encrypted-value", "preset-1");
        when(configs.findByTenantId("tenant-a")).thenReturn(Mono.just(existing));
        when(presets.findById("preset-1")).thenReturn(Mono.just(preset("preset-1")));

        assertThat(service.get(tenantAdmin).block())
                .isEqualTo(new TenantLlmConfigService.ConfigResponse("preset-1", "Provider", "model-a", "********"));

        verify(configs).findByTenantId("tenant-a");
        verify(crypto, never()).decrypt(any());
        verifyNoInteractions(resolver);
    }

    @Test
    void regularUserAndTenantlessIdentityCannotReadOrWrite() {
        var regular = identity("reader", "tenant-a", false, false);
        var tenantlessAdmin = identity("admin", null, false, true);
        assertThatThrownBy(() -> service.get(regular).block()).isInstanceOf(ForbiddenException.class);
        assertThatThrownBy(() -> service.save(regular, new TenantLlmConfigService.ConfigInput("preset-1", "secret-key")).block())
                .isInstanceOf(ForbiddenException.class);
        assertThatThrownBy(() -> service.get(tenantlessAdmin).block()).isInstanceOf(ForbiddenException.class);
        assertThatThrownBy(() -> service.delete(tenantlessAdmin).block()).isInstanceOf(ForbiddenException.class);
        verifyNoInteractions(configs, presets, crypto, resolver);
    }

    @Test
    void platformAdminCanReadOwnTenantButCannotWriteUnlessAlsoTenantAdmin() {
        var platformOnly = identity("platform", "tenant-a", true, false);
        when(configs.findByTenantId("tenant-a")).thenReturn(Mono.empty());
        assertThatThrownBy(() -> service.get(platformOnly).block())
                .isInstanceOf(LlmConfigValidationException.class);
        assertThatThrownBy(() -> service.save(platformOnly,
                new TenantLlmConfigService.ConfigInput("preset-1", "secret-key")).block())
                .isInstanceOf(ForbiddenException.class);
        assertThatThrownBy(() -> service.delete(platformOnly).block()).isInstanceOf(ForbiddenException.class);
        verify(configs).findByTenantId("tenant-a");
        verifyNoInteractions(presets, crypto, resolver);
    }

    @Test
    void successfulProbeThenSaveUsesTenantScopedRowAndEvictsOnlyThatTenant() throws Exception {
        upstream.enqueue(new MockResponse().setResponseCode(200).setHeader("Content-Type", "text/event-stream")
                .setBody("data: {\"choices\":[]}\n\n"));
        when(presets.findById("preset-1")).thenReturn(Mono.just(preset("preset-1")));
        when(configs.findByTenantId("tenant-a")).thenReturn(Mono.empty());
        when(crypto.encrypt("plain-key-value")).thenReturn("encrypted-value");
        when(configs.upsert(any())).thenReturn(Mono.empty());

        var response = service.save(tenantAdmin,
                new TenantLlmConfigService.ConfigInput("preset-1", "plain-key-value")).block();

        assertThat(response).isEqualTo(new TenantLlmConfigService.ConfigResponse("preset-1", "Provider", "model-a", "********"));
        var request = upstream.takeRequest();
        assertThat(request.getPath()).isEqualTo("/v1/chat/completions");
        assertThat(request.getHeader("Authorization")).isEqualTo("Bearer plain-key-value");
        assertThat(request.getHeader("Accept")).contains("text/event-stream");
        assertThat(request.getBody().readUtf8()).contains("\"model\":\"model-a\"", "\"stream\":true", "\"role\":\"user\"");
        ArgumentCaptor<TenantLlmConfigRow> saved = ArgumentCaptor.forClass(TenantLlmConfigRow.class);
        verify(configs).upsert(saved.capture());
        assertThat(saved.getValue().tenantId()).isEqualTo("tenant-a");
        assertThat(saved.getValue().presetId()).isEqualTo("preset-1");
        assertThat(saved.getValue().apiKeyEncrypted()).isEqualTo("encrypted-value");
        assertThat(saved.getValue().id()).isNotBlank();
        assertThat(saved.getValue().createdAt()).isEqualTo(LocalDateTime.parse("2026-10-10T01:02:03"));
        verify(resolver).evict("tenant-a");
        verifyNoMoreInteractions(resolver);
    }

    @Test
    void failedProbeDoesNotOverwriteExistingRowOrEvictAndDoesNotExposeProviderSecrets() {
        var old = config("tenant-a", "old-encrypted-value", "preset-1");
        when(presets.findById("preset-1")).thenReturn(Mono.just(preset("preset-1")));
        when(configs.findByTenantId("tenant-a")).thenReturn(Mono.just(old));
        upstream.enqueue(new MockResponse().setResponseCode(401)
                .setHeader("X-Leak", "private-header-value")
                .setBody("private-response-body"));

        assertThatThrownBy(() -> service.save(tenantAdmin,
                new TenantLlmConfigService.ConfigInput("preset-1", "plain-key-value")).block())
                .isInstanceOf(LlmConfigValidationException.class)
                .satisfies(error -> {
                    assertThat(error.getMessage()).doesNotContain("private-response-body", "private-header-value", "plain-key-value");
                    assertThat(error.getCause()).isNull();
                });

        verify(configs, never()).upsert(any());
        verify(crypto, never()).encrypt(any());
        verifyNoInteractions(resolver);
    }

    @Test
    void disconnectedProviderAndPersistenceFailureReturnSanitizedErrorsWithoutEviction() {
        when(presets.findById("preset-1")).thenReturn(Mono.just(preset("preset-1")));
        when(configs.findByTenantId("tenant-a")).thenReturn(Mono.just(config("tenant-a", "old-encrypted-value", "preset-1")));
        upstream.enqueue(new MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AT_START));

        assertThatThrownBy(() -> service.save(tenantAdmin,
                new TenantLlmConfigService.ConfigInput("preset-1", "secret-provider-key")).block())
                .isInstanceOf(LlmConfigValidationException.class)
                .satisfies(error -> {
                    assertThat(error.getMessage()).isEqualTo("Provider could not be reached");
                    assertThat(error.getCause()).isNull();
                    assertThat(error.toString()).doesNotContain("secret-provider-key", "DISCONNECT_AT_START");
                });
        verify(configs, never()).upsert(any());
        verifyNoInteractions(resolver);

        reset(configs, resolver, crypto);
        when(presets.findById("preset-1")).thenReturn(Mono.just(preset("preset-1")));
        when(configs.findByTenantId("tenant-a")).thenReturn(Mono.just(config("tenant-a", "old-encrypted-value", "preset-1")));
        when(crypto.encrypt("secret-provider-key")).thenReturn("encrypted-value");
        when(configs.upsert(any())).thenReturn(Mono.error(new IllegalStateException("db error: secret-provider-key")));
        upstream.enqueue(new MockResponse().setResponseCode(200).setBody("data: [DONE]\n\n"));

        assertThatThrownBy(() -> service.save(tenantAdmin,
                new TenantLlmConfigService.ConfigInput("preset-1", "secret-provider-key")).block())
                .isInstanceOf(LlmConfigValidationException.class)
                .satisfies(error -> {
                    assertThat(error.getMessage()).isEqualTo("LLM configuration could not be saved");
                    assertThat(error.getCause()).isNull();
                    assertThat(error.toString()).doesNotContain("secret-provider-key");
                });
        verify(configs).upsert(any());
        verifyNoInteractions(resolver);
    }

    @Test
    void updatePreservesIdAndCreationTimestampAndDeleteOnlyEvictsAfterExistingRowDeleted() {
        var existing = config("tenant-a", "encrypted-value", "preset-1");
        upstream.enqueue(new MockResponse().setResponseCode(200).setBody("data: [DONE]\n\n"));
        when(presets.findById("preset-1")).thenReturn(Mono.just(preset("preset-1")));
        when(configs.findByTenantId("tenant-a")).thenReturn(Mono.just(existing));
        when(crypto.encrypt("new-key")).thenReturn("new-encrypted-value");
        when(configs.upsert(any())).thenReturn(Mono.empty());

        service.save(tenantAdmin, new TenantLlmConfigService.ConfigInput("preset-1", "new-key")).block();
        ArgumentCaptor<TenantLlmConfigRow> updated = ArgumentCaptor.forClass(TenantLlmConfigRow.class);
        verify(configs).upsert(updated.capture());
        assertThat(updated.getValue().id()).isEqualTo("row-1");
        assertThat(updated.getValue().createdAt()).isEqualTo(existing.createdAt());
        assertThat(updated.getValue().apiKeyEncrypted()).isEqualTo("new-encrypted-value");

        reset(configs, resolver);
        when(configs.findByTenantId("tenant-a")).thenReturn(Mono.just(existing));
        when(configs.deleteByTenantId("tenant-a")).thenReturn(Mono.just(1L));

        service.delete(tenantAdmin).block();
        verify(configs).deleteByTenantId("tenant-a");
        verify(resolver).evict("tenant-a");

        reset(configs, resolver);
        when(configs.findByTenantId("tenant-a")).thenReturn(Mono.empty());
        assertThatThrownBy(() -> service.delete(tenantAdmin).block()).isInstanceOf(LlmConfigValidationException.class);
        verifyNoInteractions(resolver);
    }

    @Test
    void missingPresetAndBlankFieldsFailBeforeProbeOrPersistence() {
        assertThatThrownBy(() -> service.save(tenantAdmin,
                new TenantLlmConfigService.ConfigInput(" ", "secret-key")).block())
                .isInstanceOf(LlmConfigValidationException.class);
        assertThatThrownBy(() -> service.save(tenantAdmin,
                new TenantLlmConfigService.ConfigInput("preset-1", " ")).block())
                .isInstanceOf(LlmConfigValidationException.class);
        when(presets.findById("missing")).thenReturn(Mono.empty());
        assertThatThrownBy(() -> service.save(tenantAdmin,
                new TenantLlmConfigService.ConfigInput("missing", "secret-key")).block())
                .isInstanceOf(LlmConfigValidationException.class);
        verify(configs, never()).upsert(any());
        verifyNoInteractions(resolver);
        assertThat(upstream.getRequestCount()).isZero();
    }

    @Test
    void requestDtoRejectsAnyTenantSelectorIncludingExplicitNullAndRedactsApiKey() throws Exception {
        var mapper = new ObjectMapper();
        var absent = mapper.readValue("{\"presetId\":\"preset-1\",\"apiKey\":\"secret-key\"}",
                TenantLlmConfigController.ConfigRequest.class);
        var presentNull = mapper.readValue("{\"presetId\":\"preset-1\",\"apiKey\":\"secret-key\",\"tenantId\":null}",
                TenantLlmConfigController.ConfigRequest.class);
        assertThat(absent.hasTenantId()).isFalse();
        assertThat(presentNull.hasTenantId()).isTrue();
        assertThat(absent.toString()).doesNotContain("secret-key").contains("REDACTED");
    }

    private LlmProviderPresetRow preset(String id) {
        return new LlmProviderPresetRow(id, "Provider", upstream.url("/v1").toString(), "model-a", null,
                "platform-admin", LocalDateTime.parse("2026-10-01T00:00:00"), LocalDateTime.parse("2026-10-01T00:00:00"));
    }

    private static TenantLlmConfigRow config(String tenant, String encryptedKey, String preset) {
        return new TenantLlmConfigRow("row-1", tenant, preset, encryptedKey,
                LocalDateTime.parse("2026-10-01T00:00:00"), LocalDateTime.parse("2026-10-02T00:00:00"));
    }

    private static IdentityContext identity(String subject, String tenant, boolean platformAdmin, boolean tenantAdmin) {
        return new IdentityContext(subject, subject, tenant, null, null, null, Set.of(), Set.of(), Set.of(),
                platformAdmin, tenantAdmin);
    }
}
