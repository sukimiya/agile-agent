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

import io.github.sukimiya.agileagent.auth.AuthorizationService;
import io.github.sukimiya.agileagent.service.TenantLlmClientResolver;
import io.github.sukimiya.agileagent.spi.identity.IdentityContext;
import io.github.sukimiya.agileagent.web.ForbiddenException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@ExtendWith(MockitoExtension.class)
class LlmProviderPresetServiceTest {

    @Mock LlmProviderPresetRepository repository;
    @Mock TenantLlmClientResolver resolver;

    private LlmProviderPresetService service;
    private final IdentityContext platformAdmin = identity("platform-1", true, false);

    @BeforeEach
    void setUp() {
        service = new LlmProviderPresetService(repository, new AuthorizationService(), resolver,
                Clock.fixed(Instant.parse("2026-10-10T01:02:03Z"), ZoneOffset.UTC), false);
    }

    @Test
    void listReturnsRepositoryRowsForAnyAuthenticatedIdentity() {
        var row = row("preset-1", "subject-1", "Provider", "https://api.example.test/v1", "model-a", null);
        when(repository.findAll()).thenReturn(Flux.just(row));

        assertThat(service.list(identity("reader", false, false)).collectList().block())
                .containsExactly(row);
    }

    @Test
    void createUsesServerGeneratedIdAndAuthenticatedCreator() {
        when(repository.insert(any())).thenReturn(Mono.empty());

        var created = service.create(platformAdmin,
                new LlmProviderPresetService.PresetInput("  Acme  ", " https://api.example.test/v1 ", " model-a ", null))
                .block();

        assertThat(created.id()).isNotBlank();
        assertThat(created.id()).isNotEqualTo("client-id");
        assertThat(created.createdBySubject()).isEqualTo("platform-1");
        assertThat(created.name()).isEqualTo("Acme");
        assertThat(created.baseUrl()).isEqualTo("https://api.example.test/v1");
        assertThat(created.model()).isEqualTo("model-a");
        verify(repository).insert(any());
        verifyNoInteractions(resolver);
    }

    @Test
    void writesRequirePlatformAdmin() {
        var tenantAdmin = identity("tenant-admin", false, true);
        var regularUser = identity("reader", false, false);
        var input = new LlmProviderPresetService.PresetInput("Provider", "https://api.example.test", "model", null);

        assertThatThrownBy(() -> service.create(tenantAdmin, input).block()).isInstanceOf(ForbiddenException.class);
        assertThatThrownBy(() -> service.create(regularUser, input).block()).isInstanceOf(ForbiddenException.class);
        verifyNoInteractions(repository, resolver);
    }

    @Test
    void rejectsBlankFieldsMalformedHostlessAndUnsupportedUrls() {
        assertInvalid(new LlmProviderPresetService.PresetInput(" ", "https://api.example.test", "model", null));
        assertInvalid(new LlmProviderPresetService.PresetInput("Provider", " ", "model", null));
        assertInvalid(new LlmProviderPresetService.PresetInput("Provider", "https://api.example.test", "  ", null));
        assertInvalid(new LlmProviderPresetService.PresetInput("Provider", "https://", "model", null));
        assertInvalid(new LlmProviderPresetService.PresetInput("Provider", "ftp://api.example.test", "model", null));
        assertInvalid(new LlmProviderPresetService.PresetInput("Provider", "https://bad host/v1", "model", null));
        assertInvalid(new LlmProviderPresetService.PresetInput("Provider", "https://api.example.test", "model", "https://elsewhere.test/balance"));
        verifyNoInteractions(repository);
    }

    @Test
    void defaultsToHttpsAndAllowsHttpOnlyWhenExplicitlyConfigured() {
        assertInvalid(new LlmProviderPresetService.PresetInput("Provider", "http://api.example.test/v1", "model", null));

        var localMockService = new LlmProviderPresetService(repository, new AuthorizationService(), resolver,
                Clock.fixed(Instant.parse("2026-10-10T01:02:03Z"), ZoneOffset.UTC), true);
        when(repository.insert(any())).thenReturn(Mono.empty());

        var created = localMockService.create(platformAdmin,
                new LlmProviderPresetService.PresetInput("Provider", "http://localhost:8090/v1", "model", null))
                .block();

        assertThat(created.baseUrl()).isEqualTo("http://localhost:8090/v1");
    }

    @Test
    void updatePreservesCreatorAndCreationTimeAndEvictsOnlyAfterSuccessfulPersistence() {
        var existing = row("preset-1", "original-creator", "Old", "https://old.example.test", "old-model", null);
        when(repository.findById("preset-1")).thenReturn(Mono.just(existing));
        when(repository.update(any())).thenReturn(Mono.just(1L));

        var updated = service.update(platformAdmin, "preset-1",
                new LlmProviderPresetService.PresetInput("New", "https://new.example.test/v1", "new-model", "/balance"))
                .block();

        assertThat(updated.createdBySubject()).isEqualTo("original-creator");
        assertThat(updated.createdAt()).isEqualTo(existing.createdAt());
        assertThat(updated.name()).isEqualTo("New");
        var saved = ArgumentCaptor.forClass(LlmProviderPresetRepository.LlmProviderPresetRow.class);
        verify(repository).update(saved.capture());
        assertThat(saved.getValue().createdBySubject()).isEqualTo("original-creator");
        verify(resolver).evictAll();
    }

    @Test
    void missingUpdateIsNotFoundAndDoesNotEvict() {
        when(repository.findById("missing")).thenReturn(Mono.empty());

        assertThatThrownBy(() -> service.update(platformAdmin, "missing",
                new LlmProviderPresetService.PresetInput("New", "https://new.example.test", "model", null)).block())
                .isInstanceOf(LlmProviderPresetNotFoundException.class);
        verify(resolver, never()).evictAll();
    }

    @Test
    void failedUpdateDoesNotEvict() {
        when(repository.findById("preset-1")).thenReturn(Mono.just(
                row("preset-1", "creator", "Old", "https://old.example.test", "model", null)));
        when(repository.update(any())).thenReturn(Mono.error(new IllegalStateException("database secret")));

        assertThatThrownBy(() -> service.update(platformAdmin, "preset-1",
                new LlmProviderPresetService.PresetInput("New", "https://new.example.test", "model", null)).block())
                .hasMessageContaining("database secret");
        verify(resolver, never()).evictAll();
    }

    @Test
    void deleteEvictsOnlyAfterSuccessfulDeleteAndMissingDeleteIsNotFound() {
        when(repository.deleteById("preset-1")).thenReturn(Mono.just(1L));

        service.delete(platformAdmin, "preset-1").block();

        verify(repository).deleteById("preset-1");
        verify(resolver).evictAll();

        reset(resolver);
        when(repository.deleteById("missing")).thenReturn(Mono.just(0L));
        assertThatThrownBy(() -> service.delete(platformAdmin, "missing").block())
                .isInstanceOf(LlmProviderPresetNotFoundException.class);
        verifyNoInteractions(resolver);
    }

    @Test
    void failedDeleteDoesNotEvict() {
        when(repository.deleteById("preset-1")).thenReturn(Mono.error(new IllegalStateException("database failure")));

        assertThatThrownBy(() -> service.delete(platformAdmin, "preset-1").block())
                .hasMessageContaining("database failure");
        verifyNoInteractions(resolver);
    }

    @Test
    void modelWindowIsOptionalPositiveAndLeavesRoomForInput() {
        when(repository.insert(any())).thenReturn(Mono.empty());
        var url = "https://api.example.test/v1";

        var sized = service.create(platformAdmin,
                new LlmProviderPresetService.PresetInput("P", url, "m", null, 64_000, 8_000)).block();
        assertThat(sized.contextWindowTokens()).isEqualTo(64_000);
        assertThat(sized.maxOutputTokens()).isEqualTo(8_000);
        var unsized = service.create(platformAdmin, new LlmProviderPresetService.PresetInput("P", url, "m", null)).block();
        assertThat(unsized.contextWindowTokens()).isNull();
        assertThat(unsized.maxOutputTokens()).isNull();

        assertInvalid(new LlmProviderPresetService.PresetInput("P", url, "m", null, 0, null));
        assertInvalid(new LlmProviderPresetService.PresetInput("P", url, "m", null, 512, null));
        assertInvalid(new LlmProviderPresetService.PresetInput("P", url, "m", null, null, 0));
        assertInvalid(new LlmProviderPresetService.PresetInput("P", url, "m", null, 8_000, 8_000));
    }

    private void assertInvalid(LlmProviderPresetService.PresetInput input) {
        assertThatThrownBy(() -> service.create(platformAdmin, input).block())
                .isInstanceOf(LlmProviderPresetValidationException.class);
    }

    private static IdentityContext identity(String subject, boolean platformAdmin, boolean tenantAdmin) {
        return new IdentityContext(subject, subject, "tenant-1", null, null, null,
                Set.of(), Set.of(), Set.of(), platformAdmin, tenantAdmin);
    }

    private static LlmProviderPresetRepository.LlmProviderPresetRow row(
            String id, String creator, String name, String baseUrl, String model, String balancePath) {
        var createdAt = LocalDateTime.parse("2026-10-09T10:00:00");
        return new LlmProviderPresetRepository.LlmProviderPresetRow(
                id, name, baseUrl, model, balancePath, creator, createdAt, createdAt);
    }
}
