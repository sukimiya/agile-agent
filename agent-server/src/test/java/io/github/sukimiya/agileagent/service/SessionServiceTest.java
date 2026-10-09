package io.github.sukimiya.agileagent.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.Test;

import io.github.sukimiya.agileagent.repository.SessionRepository;
import io.github.sukimiya.agileagent.repository.SessionRepository.SessionRow;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

class SessionServiceTest {

    @Test
    void touchOrCreateInsertsNewSessionWithTruncatedTitle() {
        var repository = mock(SessionRepository.class);
        when(repository.findById("s1")).thenReturn(Mono.empty());
        when(repository.insert(any(), any(), any())).thenReturn(Mono.empty());
        var service = new SessionService(repository);

        var result = service.touchOrCreate("s1", "user-1",
                "这是一条非常长的用户消息用来测试标题截断逻辑是否按预期工作而不是原样保留全部文字").block();

        assertThat(result).isEqualTo(SessionService.AccessResult.OWNED);
        verify(repository).insert(eq("s1"), eq("user-1"), argThat(title -> title.endsWith("…") && title.length() == 25));
    }

    @Test
    void touchOrCreateTouchesExistingOwnedSession() {
        var repository = mock(SessionRepository.class);
        when(repository.findById("s1")).thenReturn(Mono.just(
                new SessionRow("s1", "user-1", "标题", LocalDateTime.now(), LocalDateTime.now())));
        when(repository.touch("s1")).thenReturn(Mono.empty());
        var service = new SessionService(repository);

        var result = service.touchOrCreate("s1", "user-1", "second message").block();

        assertThat(result).isEqualTo(SessionService.AccessResult.OWNED);
        verify(repository).touch("s1");
        verify(repository, never()).insert(any(), any(), any());
    }

    @Test
    void touchOrCreateForbidsAccessToAnotherUsersSession() {
        var repository = mock(SessionRepository.class);
        when(repository.findById("s1")).thenReturn(Mono.just(
                new SessionRow("s1", "user-2", "标题", LocalDateTime.now(), LocalDateTime.now())));
        var service = new SessionService(repository);

        var result = service.touchOrCreate("s1", "user-1", "message").block();

        assertThat(result).isEqualTo(SessionService.AccessResult.FORBIDDEN);
        verify(repository, never()).touch(any());
        verify(repository, never()).insert(any(), any(), any());
    }

    @Test
    void findOwnedReturnsEmptyWhenSessionBelongsToAnotherUser() {
        var repository = mock(SessionRepository.class);
        when(repository.findById("s1")).thenReturn(Mono.just(
                new SessionRow("s1", "user-2", "标题", LocalDateTime.now(), LocalDateTime.now())));
        var service = new SessionService(repository);

        assertThat(service.findOwned("s1", "user-1").blockOptional()).isEmpty();
    }

    @Test
    void listForUserDelegatesToRepository() {
        var repository = mock(SessionRepository.class);
        var rows = List.of(new SessionRow("s1", "user-1", "标题", LocalDateTime.now(), LocalDateTime.now()));
        when(repository.findByUserId("user-1")).thenReturn(Flux.fromIterable(rows));
        var service = new SessionService(repository);

        assertThat(service.listForUser("user-1").collectList().block()).isEqualTo(rows);
    }

    @Test
    void emptyMessageProducesDefaultTitle() {
        var repository = mock(SessionRepository.class);
        when(repository.findById("s1")).thenReturn(Mono.empty());
        when(repository.insert(any(), any(), any())).thenReturn(Mono.empty());
        var service = new SessionService(repository);

        service.touchOrCreate("s1", "user-1", "   ").block();

        verify(repository).insert("s1", "user-1", "新对话");
    }
}
