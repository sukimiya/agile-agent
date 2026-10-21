package io.github.sukimiya.agileagent.service;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import io.github.sukimiya.agileagent.memory.MemoryException;
import io.github.sukimiya.agileagent.memory.MemoryScope;
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

    // Legacy String-userId methods below are kept only until Task 4 switches ChatController and its tests to
    // the MemoryScope methods and deletes them in the same commit. They do not see tenant ownership.

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

    /**
     * Live session owned by {@code scope}; empty when unknown, owned by another tenant/subject, legacy (no
     * tenant) or deleted, so callers answer 404 without revealing which.
     */
    public Mono<SessionRow> findOwned(String conversationId, MemoryScope scope) {
        return repository.findScoped(conversationId, scope);
    }

    public Flux<SessionRow> listForUser(MemoryScope scope) {
        return repository.findByScope(scope);
    }

    /**
     * Returns the caller's live session, creating it when the id is unused. An id that already belongs to
     * someone else, to a legacy row or to a deleted session fails with
     * {@link MemoryException.ErrorCode#CONVERSATION_NOT_FOUND}; a concurrent create by the same owner returns
     * the winner's row.
     */
    public Mono<SessionRow> createOwned(String conversationId, MemoryScope scope, String title) {
        return repository.findScoped(conversationId, scope)
                .switchIfEmpty(Mono.defer(() -> repository.insertScoped(conversationId, scope, title(title))
                        .onErrorResume(DuplicateKeyException.class, e -> Mono.empty())
                        .then(repository.findScoped(conversationId, scope))))
                .switchIfEmpty(Mono.error(() -> new MemoryException(MemoryException.ErrorCode.CONVERSATION_NOT_FOUND)));
    }

    static String title(String message) {
        String trimmed = message == null ? "" : message.trim();
        if (trimmed.isEmpty()) return "新对话";
        return trimmed.length() <= TITLE_MAX_LENGTH ? trimmed : trimmed.substring(0, TITLE_MAX_LENGTH) + "…";
    }
}
