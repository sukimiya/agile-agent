package io.github.sukimiya.agileagent.service;

import org.springframework.stereotype.Service;

import io.github.sukimiya.agileagent.repository.SessionRepository;
import io.github.sukimiya.agileagent.repository.SessionRepository.SessionRow;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Service
public class SessionService {

    private static final int TITLE_MAX_LENGTH = 24;

    private final SessionRepository repository;

    public SessionService(SessionRepository repository) {
        this.repository = repository;
    }

    public enum AccessResult { OWNED, FORBIDDEN }

    public Mono<AccessResult> touchOrCreate(String sessionId, String userId, String firstMessage) {
        return repository.findById(sessionId)
                .flatMap(existing -> existing.userId().equals(userId)
                        ? repository.touch(sessionId).thenReturn(AccessResult.OWNED)
                        : Mono.just(AccessResult.FORBIDDEN))
                .switchIfEmpty(Mono.defer(() ->
                        repository.insert(sessionId, userId, title(firstMessage)).thenReturn(AccessResult.OWNED)));
    }

    public Mono<SessionRow> findOwned(String sessionId, String userId) {
        return repository.findById(sessionId).filter(s -> s.userId().equals(userId));
    }

    public Flux<SessionRow> listForUser(String userId) {
        return repository.findByUserId(userId);
    }

    static String title(String message) {
        String trimmed = message == null ? "" : message.trim();
        if (trimmed.isEmpty()) return "新对话";
        return trimmed.length() <= TITLE_MAX_LENGTH ? trimmed : trimmed.substring(0, TITLE_MAX_LENGTH) + "…";
    }
}
