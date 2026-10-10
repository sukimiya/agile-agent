package io.github.sukimiya.agileagent.knowledge;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.stereotype.Service;

import io.github.sukimiya.agileagent.config.KnowledgeProperties;
import io.github.sukimiya.agileagent.knowledge.KnowledgeAccessPolicy.UploadScope;
import io.github.sukimiya.agileagent.knowledge.KnowledgeDocumentRepository.KnowledgeDocumentRow;
import io.github.sukimiya.agileagent.knowledge.KnowledgeDocumentRepository.KnowledgeDocumentRow.Status;
import io.github.sukimiya.agileagent.service.LlmClient;
import io.github.sukimiya.agileagent.service.TenantLlmClientResolver;
import io.github.sukimiya.agileagent.spi.identity.IdentityContext;
import io.github.sukimiya.agileagent.util.NonBlockingIds;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

@Service
public class KnowledgeDocumentService {

    private static final Logger log = LoggerFactory.getLogger(KnowledgeDocumentService.class);

    private final KnowledgeDocumentRepository repository;
    private final KnowledgeFileStorage storage;
    private final TenantLlmClientResolver llmResolver;
    private final KnowledgeCatalog catalog;
    private final KnowledgeAccessPolicy policy;
    private final KnowledgeProperties properties;

    public KnowledgeDocumentService(KnowledgeDocumentRepository repository, KnowledgeFileStorage storage,
            TenantLlmClientResolver llmResolver, KnowledgeCatalog catalog,
            KnowledgeAccessPolicy policy, KnowledgeProperties properties) {
        this.repository = repository;
        this.storage = storage;
        this.llmResolver = llmResolver;
        this.catalog = catalog;
        this.policy = policy;
        this.properties = properties;
    }

    public enum ActionResult { OK, NOT_FOUND, FORBIDDEN, CONFLICT }

    public Mono<KnowledgeDocumentRow> upload(IdentityContext identity, String title, FilePart file) {
        return Mono.defer(() -> {
            UploadScope scope = policy.deriveUploadScope(identity);
            String id = NonBlockingIds.uuid();
            if (hasText(identity.tenantId())) {
                return llmResolver.resolve(identity.tenantId())
                        .flatMap(llm -> saveAndPersist(id, identity, scope, title, file, llm));
            }
            return saveAndPersist(id, identity, scope, title, file, null);
        });
    }

    private Mono<KnowledgeDocumentRow> saveAndPersist(String id, IdentityContext identity, UploadScope scope,
            String title, FilePart file, LlmClient llm) {
        return Mono.usingWhen(
                storage.saveTemp(id, file),
                saved -> summarize(id, saved.content(), llm)
                        .map(summary -> new KnowledgeDocumentRow(id,
                                title == null || title.isBlank() ? file.filename() : title,
                                file.filename(), saved.tempPath(), scope.initialStatus(), summary,
                                identity.subject(), LocalDateTime.now(), null, null, scope.scopeType(),
                                scope.tenantId(), scope.tenantName(), scope.orgId(), scope.orgName()))
                        .flatMap(row -> repository.insert(row).thenReturn(row)),
                saved -> Mono.empty(),
                (saved, error) -> cleanupTemp(id),
                saved -> cleanupTemp(id))
                .flatMap(row -> scope.autoApprove()
                        ? promoteAndApprove(row, identity.subject(), row.summary())
                        : Mono.just(row));
    }

    private Mono<String> summarize(String id, String content, LlmClient llm) {
        if (llm == null) {
            return Mono.just("");
        }
        return Mono.defer(() -> llm.summarize(content))
                .timeout(properties.getSummaryTimeout())
                .onErrorResume(error -> {
                    log.warn("knowledge summary generation failed for {}", id);
                    return Mono.just("");
                })
                .defaultIfEmpty("");
    }

    public Flux<KnowledgeDocumentRow> list(Status status, IdentityContext identity) {
        return repository.findByStatus(status).filter(row -> policy.canList(identity, row));
    }

    public Mono<KnowledgeDocumentRow> get(String id, IdentityContext identity) {
        return repository.findById(id).filter(row -> policy.canDownload(identity, row));
    }

    public Mono<ActionResult> approve(String id, IdentityContext identity, String summaryOverride) {
        return repository.findById(id)
                .flatMap(existing -> {
                    if (!policy.canApprove(identity, existing)) {
                        return Mono.just(ActionResult.FORBIDDEN);
                    }
                    if (existing.status() != Status.PENDING) {
                        return Mono.just(ActionResult.CONFLICT);
                    }
                    String summary = summaryOverride == null ? existing.summary() : summaryOverride;
                    return promoteAndApprove(existing, identity.subject(), summary)
                            .thenReturn(ActionResult.OK)
                            .onErrorResume(WorkflowConflictException.class,
                                    error -> Mono.just(ActionResult.CONFLICT));
                })
                .defaultIfEmpty(ActionResult.NOT_FOUND);
    }

    public Mono<ActionResult> reject(String id, IdentityContext identity) {
        return repository.findById(id)
                .flatMap(existing -> {
                    if (!policy.canApprove(identity, existing)) {
                        return Mono.just(ActionResult.FORBIDDEN);
                    }
                    if (existing.status() != Status.PENDING) {
                        return Mono.just(ActionResult.CONFLICT);
                    }
                    return repository.rejectIfPending(id, identity.subject(), reviewTime())
                            .map(updated -> updated == 0 ? ActionResult.CONFLICT : ActionResult.OK);
                })
                .defaultIfEmpty(ActionResult.NOT_FOUND);
    }

    private Mono<KnowledgeDocumentRow> promoteAndApprove(
            KnowledgeDocumentRow existing, String reviewer, String summary) {
        LocalDateTime reviewedAt = reviewTime();
        var metadata = new KnowledgeFileStorage.LibraryMetadata(existing.title(), summary, reviewer, reviewedAt,
                existing.scopeType(), existing.tenantId(), existing.tenantName(),
                existing.orgId(), existing.orgName());
        var promotionCompleted = new AtomicBoolean();
        var casAttempted = new AtomicBoolean();
        var casResultKnown = new AtomicBoolean();
        var approvalApplied = new AtomicBoolean();

        return Mono.usingWhen(
                Mono.just(existing.id()),
                id -> storage.promoteToLib(id, metadata)
                        .doOnSuccess(ignored -> promotionCompleted.set(true))
                        .then(Mono.defer(() -> {
                            casAttempted.set(true);
                            return repository.approveIfPending(id, summary, reviewer, reviewedAt)
                                    .defaultIfEmpty(0L)
                                    .doOnNext(updated -> {
                                        casResultKnown.set(true);
                                        approvalApplied.set(updated > 0);
                                    });
                        }))
                        .flatMap(updated -> updated == 0
                                ? Mono.error(new WorkflowConflictException())
                                : repository.findById(id).defaultIfEmpty(approvedSnapshot(existing, summary, reviewer, reviewedAt)))
                        .flatMap(approved -> {
                            if (approved.status() != Status.APPROVED) {
                                return Mono.error(new WorkflowConflictException());
                            }
                            return reloadCatalog().thenReturn(approved);
                        }),
                id -> Mono.empty(),
                (id, error) -> promotionCompleted.get()
                        ? compensatePromotion(existing, reviewer, reviewedAt,
                                casAttempted.get() && (!casResultKnown.get() || approvalApplied.get()))
                        : Mono.empty(),
                id -> promotionCompleted.get()
                        ? compensatePromotion(existing, reviewer, reviewedAt,
                                casAttempted.get() && (!casResultKnown.get() || approvalApplied.get()))
                        : Mono.empty())
                .flatMap(approved -> cleanupTemp(existing.id()).thenReturn(approved));
    }

    private Mono<Void> compensatePromotion(KnowledgeDocumentRow existing, String reviewer,
            LocalDateTime reviewedAt, boolean approvalMayHaveApplied) {
        Mono<Long> rollback = approvalMayHaveApplied
                ? repository.revertApprovalIfMatching(existing.id(), existing.summary(), reviewer, reviewedAt)
                : Mono.just(0L);
        return rollback
                .flatMap(reverted -> reverted > 0
                        ? deleteLibraryArtifacts(existing.id())
                        : repository.findById(existing.id())
                                .map(current -> current.status() != Status.APPROVED)
                                .defaultIfEmpty(true)
                                .flatMap(shouldDelete -> shouldDelete
                                        ? deleteLibraryArtifacts(existing.id())
                                        : Mono.empty()))
                .onErrorResume(error -> {
                    log.warn("knowledge approval compensation could not be confirmed for {}", existing.id());
                    return Mono.empty();
                });
    }

    private Mono<Void> reloadCatalog() {
        return Mono.fromRunnable(catalog::reload).subscribeOn(Schedulers.boundedElastic()).then();
    }

    private Mono<Void> cleanupTemp(String id) {
        return storage.deleteTemp(id).onErrorResume(error -> {
            log.warn("knowledge temporary file cleanup failed for {}", id);
            return Mono.empty();
        });
    }

    private Mono<Void> deleteLibraryArtifacts(String id) {
        return storage.deleteLibraryArtifacts(id).onErrorResume(error -> {
            log.warn("knowledge library cleanup failed for {}", id);
            return Mono.empty();
        });
    }

    private static KnowledgeDocumentRow approvedSnapshot(
            KnowledgeDocumentRow row, String summary, String reviewer, LocalDateTime reviewedAt) {
        return new KnowledgeDocumentRow(row.id(), row.title(), row.originalFilename(), row.tempPath(),
                Status.APPROVED, summary, row.uploaderUserId(), row.uploadedAt(), reviewer, reviewedAt,
                row.scopeType(), row.tenantId(), row.tenantName(), row.orgId(), row.orgName());
    }

    private static LocalDateTime reviewTime() {
        return LocalDateTime.now().truncatedTo(ChronoUnit.MICROS);
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private static final class WorkflowConflictException extends RuntimeException {}
}
