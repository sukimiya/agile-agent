package io.github.sukimiya.agileagent.hint;

import java.time.LocalDateTime;

import io.github.sukimiya.agileagent.auth.AuthorizationService;
import io.github.sukimiya.agileagent.hint.HintRepository.HintRow;
import io.github.sukimiya.agileagent.spi.identity.IdentityContext;
import io.github.sukimiya.agileagent.util.NonBlockingIds;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Service
public class HintService {

    private final HintRepository repository;
    private final AuthorizationService authorization;

    public HintService(HintRepository repository, AuthorizationService authorization) {
        this.repository = repository;
        this.authorization = authorization;
    }

    public Flux<HintSummary> list(IdentityContext identity) {
        if (identity == null) throw new IllegalArgumentException("authenticated identity required");
        return repository.findAll().map(HintService::toSummary);
    }

    public Mono<HintSummary> create(IdentityContext identity, HintInput input) {
        authorization.requirePlatformAdmin(identity);
        String content = validatedContent(input);
        var row = new HintRow(NonBlockingIds.uuid(), content, identity.subject(), LocalDateTime.now());
        return repository.insert(row).thenReturn(toSummary(row));
    }

    public Mono<HintSummary> update(IdentityContext identity, String id, HintInput input) {
        authorization.requirePlatformAdmin(identity);
        String content = validatedContent(input);
        return repository.findById(id)
                .switchIfEmpty(Mono.error(new HintNotFoundException()))
                .flatMap(existing -> repository.update(id, content).flatMap(rows -> rows == 1
                        ? Mono.just(new HintSummary(existing.id(), content, existing.createdBySubject(), existing.createdAt()))
                        : Mono.error(new HintNotFoundException())));
    }

    public Mono<Void> delete(IdentityContext identity, String id) {
        authorization.requirePlatformAdmin(identity);
        return repository.deleteById(id).flatMap(rows -> rows == 1
                ? Mono.empty()
                : Mono.error(new HintNotFoundException()));
    }

    private static String validatedContent(HintInput input) {
        if (input == null || input.content() == null || input.content().isBlank()) {
            throw new HintValidationException();
        }
        String content = input.content().trim();
        if (content.isEmpty()) throw new HintValidationException();
        return content;
    }

    private static HintSummary toSummary(HintRow row) {
        return new HintSummary(row.id(), row.content(), row.createdBySubject(), row.createdAt());
    }

    public record HintInput(String content) { }

    public record HintSummary(String id, String content, String createdBySubject, LocalDateTime createdAt) { }
}
