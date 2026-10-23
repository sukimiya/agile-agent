package io.github.sukimiya.agileagent.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.Test;

import io.github.sukimiya.agileagent.memory.MemoryException;
import io.github.sukimiya.agileagent.memory.MemoryScope;
import io.github.sukimiya.agileagent.repository.SessionRepository;
import io.github.sukimiya.agileagent.repository.SessionRepository.SessionRow;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

class SessionServiceTest {

    private static final MemoryScope ALICE = new MemoryScope("tenant-1", "alice");

    private static SessionRow row(String tenant, String user, LocalDateTime deletedAt) {
        var now = LocalDateTime.now();
        return new SessionRow("s1", tenant, user, "标题", 0, now, now, deletedAt);
    }

    @Test
    void createOwnedInsertsNewSessionWithTruncatedTitle() {
        var repository = mock(SessionRepository.class);
        when(repository.findScoped("s1", ALICE)).thenReturn(Mono.empty(), Mono.just(row("tenant-1", "alice", null)));
        when(repository.insertScoped(any(), any(), any())).thenReturn(Mono.empty());
        var service = new SessionService(repository);

        service.createOwned("s1", ALICE, "这是一条非常长的用户消息用来测试标题截断逻辑是否按预期工作而不是原样保留全部文字").block();

        verify(repository).insertScoped(eq("s1"), eq(ALICE), argThat(title -> title.endsWith("…") && title.length() == 25));
    }

    @Test
    void createOwnedRejectsAnIdThatBelongsToSomeoneElse() {
        var repository = mock(SessionRepository.class);
        when(repository.findScoped("s1", ALICE)).thenReturn(Mono.empty());
        when(repository.insertScoped(any(), any(), any()))
                .thenReturn(Mono.error(new org.springframework.dao.DuplicateKeyException("dup")));
        var service = new SessionService(repository);

        assertThatThrownBy(() -> service.createOwned("s1", ALICE, "hi").block())
                .isInstanceOf(MemoryException.class)
                .extracting(e -> ((MemoryException) e).code())
                .isEqualTo(MemoryException.ErrorCode.CONVERSATION_NOT_FOUND);
    }

    @Test
    void verifyAccessAllowsUnusedAndOwnedIds() {
        var repository = mock(SessionRepository.class);
        when(repository.findById("s1")).thenReturn(Mono.empty(), Mono.just(row("tenant-1", "alice", null)));
        var service = new SessionService(repository);

        service.verifyAccess("s1", ALICE).block();
        service.verifyAccess("s1", ALICE).block();

        verify(repository, never()).insertScoped(any(), any(), any());
    }

    @Test
    void verifyAccessHidesForeignLegacyAndDeletedSessionsAsNotFound() {
        for (var existing : List.of(row("tenant-2", "alice", null), row("tenant-1", "Alice", null),
                row(null, "alice", null), row("tenant-1", "alice", LocalDateTime.now()))) {
            var repository = mock(SessionRepository.class);
            when(repository.findById("s1")).thenReturn(Mono.just(existing));
            var service = new SessionService(repository);

            assertThatThrownBy(() -> service.verifyAccess("s1", ALICE).block())
                    .isInstanceOf(MemoryException.class)
                    .extracting(e -> ((MemoryException) e).code())
                    .isEqualTo(MemoryException.ErrorCode.CONVERSATION_NOT_FOUND);
        }
    }

    @Test
    void findOwnedAndListForUserAreScoped() {
        var repository = mock(SessionRepository.class);
        var rows = List.of(row("tenant-1", "alice", null));
        when(repository.findScoped("s1", ALICE)).thenReturn(Mono.just(rows.getFirst()));
        when(repository.findByScope(ALICE)).thenReturn(Flux.fromIterable(rows));
        var service = new SessionService(repository);

        assertThat(service.findOwned("s1", ALICE).block()).isEqualTo(rows.getFirst());
        assertThat(service.listForUser(ALICE).collectList().block()).isEqualTo(rows);
    }

    @Test
    void emptyMessageProducesDefaultTitle() {
        assertThat(SessionService.title("   ")).isEqualTo("新对话");
        assertThat(SessionService.title(null)).isEqualTo("新对话");
    }
}
