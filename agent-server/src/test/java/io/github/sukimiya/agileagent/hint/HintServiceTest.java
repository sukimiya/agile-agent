package io.github.sukimiya.agileagent.hint;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.time.LocalDateTime;
import java.util.Set;

import io.github.sukimiya.agileagent.auth.AuthorizationService;
import io.github.sukimiya.agileagent.spi.identity.IdentityContext;
import io.github.sukimiya.agileagent.web.ForbiddenException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

class HintServiceTest {

    private HintRepository repository;
    private HintService service;
    private final IdentityContext platformAdmin = identity("platform-user", true, false);

    @BeforeEach
    void setUp() {
        repository = mock(HintRepository.class);
        service = new HintService(repository, new AuthorizationService());
    }

    @Test
    void listReturnsRepositoryCreationTimeAndIdOrderWithoutRequiringTenant() {
        var first = row("a", "First", "creator", LocalDateTime.parse("2026-10-01T00:00:00"));
        var second = row("b", "Second", "creator", LocalDateTime.parse("2026-10-01T00:00:00"));
        when(repository.findAll()).thenReturn(Flux.just(first, second));

        assertThat(service.list(identity("reader", false, false)).collectList().block())
                .containsExactly(new HintService.HintSummary("a", "First", "creator", first.createdAt()),
                        new HintService.HintSummary("b", "Second", "creator", second.createdAt()));
    }

    @Test
    void createNormalizesContentAndUsesServerOwnedIdentityAndMetadata() {
        when(repository.insert(any())).thenReturn(Mono.empty());

        var created = service.create(platformAdmin, new HintService.HintInput("  Try the map view  ")).block();

        assertThat(created.content()).isEqualTo("Try the map view");
        assertThat(created.id()).isNotBlank();
        assertThat(created.createdBySubject()).isEqualTo("platform-user");
        assertThat(created.createdAt()).isNotNull();
        var rowCaptor = org.mockito.ArgumentCaptor.forClass(HintRepository.HintRow.class);
        verify(repository).insert(rowCaptor.capture());
        assertThat(rowCaptor.getValue()).isEqualTo(new HintRepository.HintRow(
                created.id(), "Try the map view", "platform-user", created.createdAt()));
    }

    @Test
    void updateChangesOnlyContentAndPreservesCreationMetadata() {
        var createdAt = LocalDateTime.parse("2026-10-01T00:00:00");
        var existing = row("hint-1", "Old", "original-creator", createdAt);
        when(repository.findById("hint-1")).thenReturn(Mono.just(existing));
        when(repository.update("hint-1", "New content")).thenReturn(Mono.just(1L));

        assertThat(service.update(platformAdmin, "hint-1", new HintService.HintInput(" New content ")).block())
                .isEqualTo(new HintService.HintSummary("hint-1", "New content", "original-creator", createdAt));
        verify(repository).update("hint-1", "New content");
    }

    @Test
    void blankContentIsRejectedBeforeRepositoryMutation() {
        for (String content : new String[] {null, "", " \t\n "}) {
            assertThatThrownBy(() -> service.create(platformAdmin, new HintService.HintInput(content)))
                    .isInstanceOf(HintValidationException.class);
        }
        verifyNoInteractions(repository);
    }

    @Test
    void nonPlatformAdministratorsCannotWriteGlobalHints() {
        assertThatThrownBy(() -> service.create(identity("tenant-admin", false, true),
                new HintService.HintInput("content"))).isInstanceOf(ForbiddenException.class);
        assertThatThrownBy(() -> service.update(identity("regular", false, false), "hint-1",
                new HintService.HintInput("content"))).isInstanceOf(ForbiddenException.class);
        assertThatThrownBy(() -> service.delete(identity("tenant-admin", false, true), "hint-1"))
                .isInstanceOf(ForbiddenException.class);
        verifyNoInteractions(repository);
    }

    @Test
    void missingUpdateAndDeleteAreNotFoundAndDeleteIsPhysical() {
        when(repository.findById("missing")).thenReturn(Mono.empty());
        when(repository.deleteById("missing")).thenReturn(Mono.just(0L));
        when(repository.deleteById("hint-1")).thenReturn(Mono.just(1L));

        assertThatThrownBy(() -> service.update(platformAdmin, "missing", new HintService.HintInput("new")).block())
                .isInstanceOf(HintNotFoundException.class);
        assertThatThrownBy(() -> service.delete(platformAdmin, "missing").block())
                .isInstanceOf(HintNotFoundException.class);
        service.delete(platformAdmin, "hint-1").block();
        verify(repository).deleteById("hint-1");
    }

    private static IdentityContext identity(String subject, boolean platformAdmin, boolean tenantAdmin) {
        return new IdentityContext(subject, subject, null, null, null, null, Set.of(), Set.of(), Set.of(),
                platformAdmin, tenantAdmin);
    }

    private static HintRepository.HintRow row(String id, String content, String creator, LocalDateTime createdAt) {
        return new HintRepository.HintRow(id, content, creator, createdAt);
    }
}
