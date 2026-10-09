package io.github.sukimiya.agileagent.knowledge;

import java.time.LocalDateTime;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.stereotype.Service;

import io.github.sukimiya.agileagent.knowledge.KnowledgeDocumentRepository.KnowledgeDocumentRow;
import io.github.sukimiya.agileagent.knowledge.KnowledgeDocumentRepository.KnowledgeDocumentRow.Status;
import io.github.sukimiya.agileagent.service.LlmClient;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

@Service
public class KnowledgeDocumentService {

    private static final Logger log = LoggerFactory.getLogger(KnowledgeDocumentService.class);

    private final KnowledgeDocumentRepository repository;
    private final KnowledgeFileStorage storage;
    private final LlmClient llm;
    private final KnowledgeCatalog catalog;

    public KnowledgeDocumentService(KnowledgeDocumentRepository repository, KnowledgeFileStorage storage,
                                     LlmClient llm, KnowledgeCatalog catalog) {
        this.repository = repository;
        this.storage = storage;
        this.llm = llm;
        this.catalog = catalog;
    }

    public enum ActionResult { OK, NOT_FOUND, CONFLICT }

    public Mono<KnowledgeDocumentRow> upload(String uploaderUserId, String title, FilePart file) {
        String id = UUID.randomUUID().toString();
        return storage.saveTemp(id, file)
                .flatMap(saved -> llm.summarize(saved.content())
                        .onErrorResume(e -> {
                            log.warn("knowledge summary generation failed for {}: {}", id, e.getMessage());
                            return Mono.just("");
                        })
                        .defaultIfEmpty("")
                        .map(summary -> new KnowledgeDocumentRow(id,
                                title == null || title.isBlank() ? file.filename() : title,
                                file.filename(), saved.tempPath(), Status.PENDING, summary,
                                uploaderUserId, LocalDateTime.now(), null, null)))
                .flatMap(row -> repository.insert(row).thenReturn(row));
    }

    public Flux<KnowledgeDocumentRow> list(Status status) {
        return repository.findByStatus(status);
    }

    public Mono<KnowledgeDocumentRow> get(String id) {
        return repository.findById(id);
    }

    public Mono<ActionResult> approve(String id, String userId, String summaryOverride) {
        return repository.findById(id)
                .flatMap(existing -> {
                    if (existing.status() != Status.PENDING) {
                        return Mono.just(ActionResult.CONFLICT);
                    }
                    String summary = summaryOverride != null ? summaryOverride : existing.summary();
                    LocalDateTime now = LocalDateTime.now();
                    return storage.promoteToLib(id, existing.tempPath(), existing.title(), summary, userId, now)
                            .then(repository.approveIfPending(id, summary, userId, now))
                            .map(updated -> updated == 0 ? ActionResult.CONFLICT : ActionResult.OK)
                            .flatMap(result -> result == ActionResult.OK
                                    ? Mono.fromRunnable(catalog::reload).subscribeOn(Schedulers.boundedElastic()).thenReturn(result)
                                    : Mono.just(result));
                })
                .defaultIfEmpty(ActionResult.NOT_FOUND);
    }

    public Mono<ActionResult> reject(String id, String userId) {
        return repository.findById(id)
                .flatMap(existing -> repository.rejectIfPending(id, userId, LocalDateTime.now())
                        .map(updated -> updated == 0 ? ActionResult.CONFLICT : ActionResult.OK))
                .defaultIfEmpty(ActionResult.NOT_FOUND);
    }
}
