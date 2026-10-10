package io.github.sukimiya.agileagent.knowledge;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

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
import reactor.util.retry.Retry;

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
        this.properties.validateApprovalClaimSettings();
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
        var insertAttempt = new PendingInsertAttempt();
        return Mono.usingWhen(
                storage.saveTemp(id, file),
                saved -> summarize(id, saved.content(), llm)
                        .map(summary -> new KnowledgeDocumentRow(id,
                                title == null || title.isBlank() ? file.filename() : title,
                                file.filename(), saved.tempPath(), scope.initialStatus(), summary,
                                identity.subject(), LocalDateTime.now(), null, null, scope.scopeType(),
                                scope.tenantId(), scope.tenantName(), scope.orgId(), scope.orgName()))
                        .flatMap(row -> insertAttempt.persist(() -> repository.insert(row)).thenReturn(row)),
                saved -> Mono.empty(),
                (saved, error) -> cleanupTempAfterInsertOutcome(id, insertAttempt),
                saved -> cleanupTempAfterInsertOutcome(id, insertAttempt))
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
                    return rejectPending(existing, identity.subject());
                })
                .defaultIfEmpty(ActionResult.NOT_FOUND);
    }

    private Mono<ActionResult> rejectPending(KnowledgeDocumentRow existing, String reviewer) {
        String claimToken = NonBlockingIds.uuid();
        var attempt = new ApprovalAttempt();
        Mono<ActionResult> workflow = repository.claimPendingApproval(
                        existing.id(), claimToken, properties.getApprovalClaimLease()).cache()
                .defaultIfEmpty(0L)
                .flatMap(claimed -> {
                    if (claimed == 0) return Mono.just(ActionResult.CONFLICT);
                    attempt.claimed.set(true);
                    return trackedStorage(attempt, storage.deleteLibraryArtifacts(existing.id()))
                            .then(Mono.defer(() -> trackedStorage(attempt, reloadCatalog())))
                            .then(Mono.defer(() -> trackedPersistence(attempt,
                                    repository.rejectClaimedPending(existing.id(), claimToken, reviewer, reviewTime())))
                                    .defaultIfEmpty(0L))
                            .flatMap(updated -> updated == 0
                                    ? Mono.error(new WorkflowConflictException())
                                    : Mono.just(ActionResult.OK));
                })
                .timeout(properties.getApprovalWorkflowTimeout())
                .onErrorResume(error -> {
                    if (error instanceof WorkflowConflictException && !attempt.claimed.get()) {
                        return Mono.just(ActionResult.CONFLICT);
                    }
                    return compensateOwnedAttempt(existing, claimToken, attempt).cache()
                            .timeout(properties.getApprovalCompensationTimeout())
                            .onErrorResume(compensationError -> {
                                log.warn("knowledge rejection compensation could not be confirmed for {}", existing.id());
                                return Mono.empty();
                            })
                            .then(Mono.error(sanitizeWorkflowFailure(error)));
                })
                .onErrorResume(WorkflowConflictException.class, error -> Mono.just(ActionResult.CONFLICT));
        return workflow.cache();
    }

    private Mono<KnowledgeDocumentRow> promoteAndApprove(
            KnowledgeDocumentRow existing, String reviewer, String summary) {
        String claimToken = NonBlockingIds.uuid();
        var attempt = new ApprovalAttempt();
        var workflow = repository.claimPendingApproval(
                        existing.id(), claimToken, properties.getApprovalClaimLease()).cache()
                .defaultIfEmpty(0L)
                .flatMap(claimed -> {
                    if (claimed == 0) {
                        return Mono.error(new WorkflowConflictException());
                    }
                    attempt.claimed.set(true);
                    return trackedStorage(attempt, storage.deleteLibraryArtifacts(existing.id()))
                            .then(Mono.defer(() -> promoteClaimedDocument(
                                    existing, reviewer, summary, claimToken, attempt)));
                })
                .timeout(properties.getApprovalWorkflowTimeout())
                .onErrorResume(error -> {
                    if (error instanceof WorkflowConflictException && !attempt.claimed.get()) {
                        return Mono.error(error);
                    }
                    return compensateOwnedAttempt(existing, claimToken, attempt).cache()
                            .timeout(properties.getApprovalCompensationTimeout())
                            .onErrorResume(compensationError -> {
                                log.warn("knowledge approval compensation could not be confirmed for {}", existing.id());
                                return Mono.empty();
                            })
                            .then(Mono.error(sanitizeWorkflowFailure(error)));
                });

        // Once a claim or filesystem/database side effect begins, client cancellation must not
        // interrupt the ownership protocol halfway through.
        return workflow.cache();
    }

    private Mono<KnowledgeDocumentRow> promoteClaimedDocument(
            KnowledgeDocumentRow existing, String reviewer, String summary, String claimToken,
            ApprovalAttempt attempt) {
        LocalDateTime reviewedAt = reviewTime();
        var metadata = new KnowledgeFileStorage.LibraryMetadata(existing.title(), summary, reviewer, reviewedAt,
                existing.scopeType(), existing.tenantId(), existing.tenantName(),
                existing.orgId(), existing.orgName());
        return trackedStorage(attempt, storage.promoteToLib(existing.id(), metadata))
                .then(Mono.defer(() -> trackedPersistence(attempt, repository.approveClaimedPending(
                        existing.id(), claimToken, summary, reviewer, reviewedAt)).defaultIfEmpty(0L)))
                .flatMap(updated -> updated == 0
                        ? Mono.error(new WorkflowConflictException())
                        : repository.findById(existing.id())
                                .defaultIfEmpty(approvedSnapshot(existing, summary, reviewer, reviewedAt)))
                .flatMap(approved -> {
                    if (approved.status() != Status.APPROVED) {
                        return Mono.error(new WorkflowConflictException());
                    }
                    return trackedStorage(attempt, reloadCatalog())
                            .then(Mono.defer(() -> cleanupTemp(existing.id())))
                            .then(Mono.defer(() -> trackedPersistence(attempt,
                                    repository.releasePendingApprovalClaim(existing.id(), claimToken))
                                    .defaultIfEmpty(0L)))
                            .flatMap(released -> released == 0
                                    ? Mono.error(new WorkflowConflictException())
                                    : Mono.just(approved));
                });
    }

    private Mono<Void> compensateOwnedAttempt(
            KnowledgeDocumentRow existing, String claimToken, ApprovalAttempt attempt) {
        return awaitInFlight(attempt)
                .then(repository.recoverPendingApprovalClaim(existing.id(), claimToken, existing.summary())
                        .defaultIfEmpty(0L)
                        .onErrorResume(recoveryError -> repository.statusForApprovalClaim(existing.id(), claimToken)
                                .filter(status -> status == Status.PENDING)
                                .map(status -> 1L)
                                .defaultIfEmpty(0L)))
                .flatMap(recovered -> recovered == 0
                        ? Mono.empty()
                        : trackedStorage(attempt, storage.deleteLibraryArtifacts(existing.id()))
                                .then(Mono.defer(() -> trackedStorage(attempt, reloadCatalog())
                                        .onErrorResume(error -> Mono.empty())))
                                .then(releaseClaimWithRetry(existing.id(), claimToken)));
    }

    private Mono<Void> releaseClaimWithRetry(String id, String claimToken) {
        int retries = properties.getApprovalClaimReleaseMaxAttempts() - 1;
        return Mono.defer(() -> repository.releasePendingApprovalClaim(id, claimToken))
                .retryWhen(Retry.fixedDelay(retries, properties.getApprovalClaimReleaseRetryDelay()))
                .then();
    }

    private static Mono<Void> awaitInFlight(ApprovalAttempt attempt) {
        Mono<Void> storageOperation = attempt.storageOperation.get();
        Mono<Long> persistenceOperation = attempt.persistenceOperation.get();
        Mono<Void> waitStorage = storageOperation == null ? Mono.empty()
                : storageOperation.onErrorResume(error -> Mono.empty());
        Mono<Void> waitPersistence = persistenceOperation == null ? Mono.empty()
                : persistenceOperation.onErrorResume(error -> Mono.empty()).then();
        return waitStorage.then(waitPersistence);
    }

    private static Mono<Void> trackedStorage(ApprovalAttempt attempt, Mono<Void> operation) {
        Mono<Void> shielded = operation.cache();
        attempt.storageOperation.set(shielded);
        return shielded;
    }

    private static Mono<Long> trackedPersistence(ApprovalAttempt attempt, Mono<Long> operation) {
        Mono<Long> shielded = operation.cache();
        attempt.persistenceOperation.set(shielded);
        return shielded;
    }

    private Mono<Void> reloadCatalog() {
        return Mono.fromRunnable(catalog::reload)
                .subscribeOn(Schedulers.boundedElastic())
                .onErrorMap(error -> new WorkflowFailureException())
                .then();
    }

    private Mono<Void> cleanupTemp(String id) {
        return storage.deleteTemp(id).onErrorResume(error -> {
            log.warn("knowledge temporary file cleanup failed for {}", id);
            return Mono.empty();
        });
    }

    private Mono<Void> cleanupTempAfterInsertOutcome(String id, PendingInsertAttempt attempt) {
        Mono<Void> insert = attempt.operation();
        if (insert == null) {
            return cleanupTemp(id);
        }
        return insert.onErrorResume(error -> Mono.empty())
                .then(repository.findById(id).hasElement()
                        .flatMap(referenced -> referenced ? Mono.empty() : cleanupTemp(id)))
                .onErrorResume(error -> {
                    // If the persistence outcome cannot be verified, retaining temp content is safer
                    // than deleting a file a committed row may reference.
                    log.warn("knowledge temporary file cleanup deferred for {}", id);
                    return Mono.empty();
                });
    }

    private static RuntimeException sanitizeWorkflowFailure(Throwable error) {
        if (error instanceof WorkflowConflictException
                || error instanceof KnowledgeFileStorage.RejectedException
                || error instanceof KnowledgeFileStorage.StorageFailureException
                || error instanceof WorkflowFailureException) {
            return (RuntimeException) error;
        }
        return new WorkflowFailureException();
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

    private static final class ApprovalAttempt {
        private final AtomicBoolean claimed = new AtomicBoolean();
        private final AtomicReference<Mono<Void>> storageOperation = new AtomicReference<>();
        private final AtomicReference<Mono<Long>> persistenceOperation = new AtomicReference<>();
    }

    private static final class WorkflowFailureException extends RuntimeException {
        private WorkflowFailureException() {
            super("knowledge approval workflow failed");
        }
    }

    private static final class PendingInsertAttempt {
        private final AtomicReference<Mono<Void>> operation = new AtomicReference<>();

        private Mono<Void> persist(Supplier<Mono<Void>> insert) {
            return operation.updateAndGet(existing -> existing == null
                    ? Mono.defer(insert).cache()
                    : existing);
        }

        private Mono<Void> operation() {
            return operation.get();
        }
    }
}
