package io.github.sukimiya.agileagent.knowledge;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.codec.multipart.FilePart;

import io.github.sukimiya.agileagent.config.KnowledgeProperties;
import io.github.sukimiya.agileagent.knowledge.KnowledgeDocumentService.ActionResult;
import io.github.sukimiya.agileagent.knowledge.KnowledgeDocumentRepository.KnowledgeDocumentRow;
import io.github.sukimiya.agileagent.knowledge.KnowledgeDocumentRepository.KnowledgeDocumentRow.ScopeType;
import io.github.sukimiya.agileagent.knowledge.KnowledgeDocumentRepository.KnowledgeDocumentRow.Status;
import io.github.sukimiya.agileagent.service.LlmClient;
import io.github.sukimiya.agileagent.service.TenantLlmClientResolver;
import io.github.sukimiya.agileagent.spi.identity.IdentityContext;

import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import reactor.test.StepVerifier;

class KnowledgeDocumentServiceTest {

    private static final String TEMP_PATH = "/tmp/knowledge-test/document.md";

    @Test
    void approvalClaimLeaseMustExceedWorkflowAndCompensationBounds() {
        var properties = new KnowledgeProperties();
        properties.setApprovalClaimLease(Duration.ofMinutes(10));

        assertThatThrownBy(() -> new KnowledgeDocumentService(
                mock(KnowledgeDocumentRepository.class), mock(KnowledgeFileStorage.class),
                mock(TenantLlmClientResolver.class), mock(KnowledgeCatalog.class),
                new KnowledgeAccessPolicy(), properties))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("knowledge approval claim lease must exceed workflow and compensation timeouts plus safety margin");
    }

    @Test
    void ordinaryUploadPersistsDerivedOrgScopeAndReturnsPending() {
        var repository = mock(KnowledgeDocumentRepository.class);
        var storage = mock(KnowledgeFileStorage.class);
        var resolver = mock(TenantLlmClientResolver.class);
        var llm = mock(LlmClient.class);
        var file = file("doc.md");
        when(resolver.resolve("tenant-a")).thenReturn(Mono.just(llm));
        when(storage.saveTemp(anyString(), eq(file))).thenReturn(Mono.just(new KnowledgeFileStorage.UploadedFile(TEMP_PATH, "content")));
        when(llm.summarize("content")).thenReturn(Mono.just("summary"));
        when(repository.insert(any())).thenReturn(Mono.empty());
        var service = service(repository, storage, resolver, mock(KnowledgeCatalog.class));

        var result = service.upload(ordinaryUser(), "Document title", file).block();

        assertThat(result.status()).isEqualTo(Status.PENDING);
        assertThat(result.scopeType()).isEqualTo(ScopeType.ORG);
        assertThat(result.tenantId()).isEqualTo("tenant-a");
        assertThat(result.tenantName()).isEqualTo("Tenant A");
        assertThat(result.orgId()).isEqualTo("org-a");
        assertThat(result.orgName()).isEqualTo("Org A");
        assertThat(result.uploaderUserId()).isEqualTo("user-a");
        assertThat(result.summary()).isEqualTo("summary");
        verify(storage, never()).promoteToLib(anyString(), any());
    }

    @Test
    void missingUploadIdentityIsRejectedBeforeResolverOrStorage() {
        var repository = mock(KnowledgeDocumentRepository.class);
        var storage = mock(KnowledgeFileStorage.class);
        var resolver = mock(TenantLlmClientResolver.class);
        var service = service(repository, storage, resolver, mock(KnowledgeCatalog.class));

        StepVerifier.create(service.upload(identity("user-a", null, "org-a", false, false), null, file("doc.md")))
                .expectError(KnowledgeAccessPolicy.UploadIdentityException.class)
                .verify();

        verifyNoInteractions(resolver, storage, repository);
    }

    @Test
    void tenantlessPlatformUploadSkipsTenantResolverAndReturnsApprovedGlobalRowAfterPromotion() {
        var repository = mock(KnowledgeDocumentRepository.class);
        var storage = mock(KnowledgeFileStorage.class);
        var resolver = mock(TenantLlmClientResolver.class);
        var catalog = mock(KnowledgeCatalog.class);
        var inserted = new AtomicReference<KnowledgeDocumentRow>();
        when(storage.saveTemp(anyString(), any())).thenReturn(Mono.just(new KnowledgeFileStorage.UploadedFile(TEMP_PATH, "content")));
        when(repository.insert(any())).thenAnswer(call -> {
            inserted.set(call.getArgument(0));
            return Mono.empty();
        });
        claimSucceeds(repository);
        when(storage.deleteLibraryArtifacts(anyString())).thenReturn(Mono.empty());
        when(storage.promoteToLib(anyString(), any())).thenReturn(Mono.empty());
        when(repository.approveClaimedPending(anyString(), anyString(), anyString(), eq("platform-admin"), any()))
                .thenReturn(Mono.just(1L));
        when(repository.findById(anyString())).thenAnswer(call -> Mono.just(approved(inserted.get(), "")));
        when(storage.deleteTemp(anyString())).thenReturn(Mono.empty());
        var service = service(repository, storage, resolver, catalog);

        var result = service.upload(identity("platform-admin", null, null, true, false), null, file("global.md")).block();

        assertThat(inserted.get().status()).isEqualTo(Status.PENDING);
        assertThat(inserted.get().scopeType()).isEqualTo(ScopeType.GLOBAL);
        assertThat(inserted.get().tenantId()).isNull();
        assertThat(inserted.get().orgId()).isNull();
        assertThat(inserted.get().summary()).isEmpty();
        assertThat(result.status()).isEqualTo(Status.APPROVED);
        assertThat(result.scopeType()).isEqualTo(ScopeType.GLOBAL);
        assertThat(result.reviewedByUserId()).isEqualTo("platform-admin");
        verifyNoInteractions(resolver);

        var order = inOrder(repository, storage, catalog);
        order.verify(repository).insert(any(KnowledgeDocumentRow.class));
        order.verify(repository).claimPendingApproval(anyString(), anyString(), any());
        order.verify(storage).deleteLibraryArtifacts(anyString());
        order.verify(storage).promoteToLib(anyString(), any(KnowledgeFileStorage.LibraryMetadata.class));
        order.verify(repository).approveClaimedPending(anyString(), anyString(), eq(""), eq("platform-admin"), any());
        order.verify(repository).findById(anyString());
        order.verify(catalog).reload();
        order.verify(storage).deleteTemp(anyString());
    }

    @Test
    void tenantAdminResolvesBeforeSaveAndPromotionCarriesTrustedScopeSnapshots() {
        var repository = mock(KnowledgeDocumentRepository.class);
        var storage = mock(KnowledgeFileStorage.class);
        var resolver = mock(TenantLlmClientResolver.class);
        var llm = mock(LlmClient.class);
        var inserted = new AtomicReference<KnowledgeDocumentRow>();
        when(resolver.resolve("tenant-a")).thenReturn(Mono.just(llm));
        when(llm.summarize("content")).thenReturn(Mono.just("summary"));
        when(storage.saveTemp(anyString(), any())).thenReturn(Mono.just(new KnowledgeFileStorage.UploadedFile(TEMP_PATH, "content")));
        when(repository.insert(any())).thenAnswer(call -> {
            inserted.set(call.getArgument(0));
            return Mono.empty();
        });
        claimSucceeds(repository);
        when(storage.deleteLibraryArtifacts(anyString())).thenReturn(Mono.empty());
        when(storage.promoteToLib(anyString(), any())).thenReturn(Mono.empty());
        when(repository.approveClaimedPending(anyString(), anyString(), anyString(), eq("tenant-admin"), any()))
                .thenReturn(Mono.just(1L));
        when(repository.findById(anyString())).thenAnswer(call -> Mono.just(approved(inserted.get(), "summary")));
        when(storage.deleteTemp(anyString())).thenReturn(Mono.empty());
        var service = service(repository, storage, resolver, mock(KnowledgeCatalog.class));

        var result = service.upload(identity("tenant-admin", "tenant-a", null, false, true), "Tenant doc", file("doc.md")).block();

        assertThat(result.status()).isEqualTo(Status.APPROVED);
        assertThat(result.scopeType()).isEqualTo(ScopeType.TENANT);
        assertThat(result.tenantId()).isEqualTo("tenant-a");
        assertThat(result.tenantName()).isEqualTo("Tenant A");
        assertThat(result.orgId()).isNull();
        var metadata = ArgumentCaptor.forClass(KnowledgeFileStorage.LibraryMetadata.class);
        verify(storage).promoteToLib(eq(inserted.get().id()), metadata.capture());
        assertThat(metadata.getValue().scopeType()).isEqualTo(ScopeType.TENANT);
        assertThat(metadata.getValue().tenantId()).isEqualTo("tenant-a");
        assertThat(metadata.getValue().tenantName()).isEqualTo("Tenant A");
        assertThat(metadata.getValue().orgId()).isNull();

        var order = inOrder(resolver, storage, repository);
        order.verify(resolver).resolve("tenant-a");
        order.verify(storage).saveTemp(anyString(), any());
        order.verify(repository).insert(any(KnowledgeDocumentRow.class));
        order.verify(repository).claimPendingApproval(anyString(), anyString(), any());
        order.verify(storage).deleteLibraryArtifacts(anyString());
        order.verify(storage).promoteToLib(anyString(), any(KnowledgeFileStorage.LibraryMetadata.class));
        order.verify(repository).approveClaimedPending(anyString(), anyString(), eq("summary"), eq("tenant-admin"), any());
    }

    @Test
    void promotionFailureLeavesAutoApprovedUploadPendingWithRecoverableTempFile() {
        var repository = mock(KnowledgeDocumentRepository.class);
        var storage = mock(KnowledgeFileStorage.class);
        var resolver = resolverReturningSummary("summary");
        var inserted = new AtomicReference<KnowledgeDocumentRow>();
        when(storage.saveTemp(anyString(), any()))
                .thenReturn(Mono.just(new KnowledgeFileStorage.UploadedFile(TEMP_PATH, "content")));
        when(repository.insert(any())).thenAnswer(call -> {
            inserted.set(call.getArgument(0));
            return Mono.empty();
        });
        claimSucceeds(repository);
        when(repository.findById(anyString())).thenAnswer(call -> Mono.just(inserted.get()));
        when(storage.promoteToLib(anyString(), any()))
                .thenReturn(Mono.error(new KnowledgeFileStorage.StorageFailureException()));
        when(storage.deleteLibraryArtifacts(anyString())).thenReturn(Mono.empty());
        var service = service(repository, storage, resolver, mock(KnowledgeCatalog.class));

        assertThatThrownBy(() -> service.upload(
                identity("tenant-admin", "tenant-a", null, false, true), null, file("doc.md")).block())
                .isInstanceOf(KnowledgeFileStorage.StorageFailureException.class);

        assertThat(inserted.get().status()).isEqualTo(Status.PENDING);
        assertThat(inserted.get().scopeType()).isEqualTo(ScopeType.TENANT);
        verify(repository, never()).approveClaimedPending(anyString(), anyString(), anyString(), anyString(), any());
        verify(storage, never()).deleteTemp(anyString());
        verify(storage, times(2)).deleteLibraryArtifacts(anyString());
        verify(repository).releasePendingApprovalClaim(anyString(), anyString());
    }

    @Test
    void summaryTimeoutFallsBackToEmptySummary() {
        var repository = mock(KnowledgeDocumentRepository.class);
        var storage = mock(KnowledgeFileStorage.class);
        var resolver = mock(TenantLlmClientResolver.class);
        var llm = mock(LlmClient.class);
        when(resolver.resolve("tenant-a")).thenReturn(Mono.just(llm));
        when(llm.summarize(anyString())).thenReturn(Mono.never());
        when(storage.saveTemp(anyString(), any())).thenReturn(Mono.just(new KnowledgeFileStorage.UploadedFile(TEMP_PATH, "content")));
        when(repository.insert(any())).thenReturn(Mono.empty());
        when(repository.findById(anyString())).thenReturn(Mono.empty());
        var service = service(repository, storage, resolver, mock(KnowledgeCatalog.class), Duration.ofMillis(20));

        var result = service.upload(ordinaryUser(), null, file("doc.md")).block(Duration.ofSeconds(2));

        assertThat(result.status()).isEqualTo(Status.PENDING);
        assertThat(result.summary()).isEmpty();
    }

    @Test
    void insertFailureDeletesTheUnreferencedTempFile() {
        var repository = mock(KnowledgeDocumentRepository.class);
        var storage = mock(KnowledgeFileStorage.class);
        var resolver = resolverReturningSummary("summary");
        when(storage.saveTemp(anyString(), any())).thenReturn(Mono.just(new KnowledgeFileStorage.UploadedFile(TEMP_PATH, "content")));
        when(repository.insert(any())).thenReturn(Mono.error(new IllegalStateException("db unavailable")));
        when(repository.findById(anyString())).thenReturn(Mono.empty());
        when(storage.deleteTemp(anyString())).thenReturn(Mono.empty());
        var service = service(repository, storage, resolver, mock(KnowledgeCatalog.class));

        assertThatThrownBy(() -> service.upload(ordinaryUser(), null, file("doc.md")).block())
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("db unavailable");

        verify(storage).deleteTemp(anyString());
    }

    @Test
    void cancellationAfterTempSaveDeletesTheUnreferencedTempFile() {
        var repository = mock(KnowledgeDocumentRepository.class);
        var storage = mock(KnowledgeFileStorage.class);
        var resolver = mock(TenantLlmClientResolver.class);
        var llm = mock(LlmClient.class);
        when(resolver.resolve("tenant-a")).thenReturn(Mono.just(llm));
        when(llm.summarize(anyString())).thenReturn(Mono.never());
        when(storage.saveTemp(anyString(), any())).thenReturn(Mono.just(new KnowledgeFileStorage.UploadedFile(TEMP_PATH, "content")));
        when(storage.deleteTemp(anyString())).thenReturn(Mono.empty());
        var service = service(repository, storage, resolver, mock(KnowledgeCatalog.class));

        StepVerifier.create(service.upload(ordinaryUser(), null, file("doc.md"))).thenCancel().verify();

        verify(storage).deleteTemp(anyString());
        verify(repository, never()).insert(any());
    }

    @Test
    void cancellationAfterPromotionPublicationCannotLeaveLibraryVisibleForPendingRow() throws InterruptedException {
        var repository = mock(KnowledgeDocumentRepository.class);
        var storage = mock(KnowledgeFileStorage.class);
        var catalog = mock(KnowledgeCatalog.class);
        var pending = row("doc-1", ScopeType.ORG, "tenant-a", "org-a", "uploader", Status.PENDING);
        var persistedStatus = new AtomicReference<>(Status.PENDING);
        var libraryVisible = new AtomicBoolean();
        var published = new CountDownLatch(1);
        var approvalPersisted = new CountDownLatch(1);
        var releasePromotion = new CountDownLatch(1);
        claimSucceeds(repository);
        when(repository.findById("doc-1")).thenAnswer(call -> Mono.just(
                persistedStatus.get() == Status.PENDING ? pending : approved(pending, "uploader-summary")));
        when(storage.promoteToLib(eq("doc-1"), any())).thenReturn(Mono.<Void>create(sink -> {
            libraryVisible.set(true);
            published.countDown();
            awaitLatch(releasePromotion);
            sink.success();
        }).subscribeOn(Schedulers.boundedElastic()));
        when(repository.approveClaimedPending(eq("doc-1"), anyString(), anyString(), eq("platform-admin"), any()))
                .thenAnswer(call -> {
                    persistedStatus.set(Status.APPROVED);
                    approvalPersisted.countDown();
                    return Mono.just(1L);
                });
        when(storage.deleteLibraryArtifacts("doc-1")).thenAnswer(call -> Mono.fromRunnable(() -> libraryVisible.set(false)));
        when(storage.deleteTemp("doc-1")).thenReturn(Mono.empty());
        doAnswer(call -> {
            return null;
        }).when(catalog).reload();
        var service = service(repository, storage, mock(TenantLlmClientResolver.class), catalog);

        var subscription = service.approve("doc-1", identity("platform-admin", null, null, true, false), null)
                .subscribe();
        try {
            assertThat(published.await(2, TimeUnit.SECONDS)).isTrue();
            subscription.dispose();
        } finally {
            releasePromotion.countDown();
        }

        assertThat(approvalPersisted.await(2, TimeUnit.SECONDS))
                .as("a published library entry must either be removed or finish approval after cancellation")
                .isTrue();
        assertThat(persistedStatus.get() == Status.PENDING && libraryVisible.get())
                .as("cancellation must never leave a pending row with a visible library entry")
                .isFalse();
        assertThat(persistedStatus.get()).isEqualTo(Status.APPROVED);
        assertThat(libraryVisible.get()).isTrue();
    }

    @Test
    void cancellationAfterInsertSideEffectKeepsTheReferencedTempFile() throws InterruptedException {
        var repository = mock(KnowledgeDocumentRepository.class);
        var storage = mock(KnowledgeFileStorage.class);
        var resolver = resolverReturningSummary("summary");
        var inserted = new AtomicReference<KnowledgeDocumentRow>();
        var tempExists = new AtomicBoolean(true);
        var insertSideEffect = new CountDownLatch(1);
        var releaseInsertAck = new CountDownLatch(1);
        var sourceCancelled = new AtomicBoolean();
        var lookupStarted = new CountDownLatch(1);
        when(storage.saveTemp(anyString(), any()))
                .thenReturn(Mono.just(new KnowledgeFileStorage.UploadedFile(TEMP_PATH, "content")));
        when(repository.insert(any())).thenAnswer(call -> {
            var row = call.getArgument(0, KnowledgeDocumentRow.class);
            return Mono.<Void>create(sink -> {
                inserted.set(row);
                insertSideEffect.countDown();
                sink.onCancel(() -> sourceCancelled.set(true));
                awaitLatch(releaseInsertAck);
                sink.success();
            }).subscribeOn(Schedulers.boundedElastic());
        });
        when(repository.findById(anyString())).thenAnswer(call -> {
            lookupStarted.countDown();
            return Mono.justOrEmpty(inserted.get());
        });
        when(storage.deleteTemp(anyString())).thenAnswer(call -> Mono.fromRunnable(() -> tempExists.set(false)));
        var service = service(repository, storage, resolver, mock(KnowledgeCatalog.class));

        var subscription = service.upload(ordinaryUser(), null, file("doc.md")).subscribe();
        try {
            assertThat(insertSideEffect.await(2, TimeUnit.SECONDS)).isTrue();
            subscription.dispose();
            assertThat(sourceCancelled.get())
                    .as("a persistence operation whose side effect began must be shielded from downstream cancellation")
                    .isFalse();
        } finally {
            releaseInsertAck.countDown();
        }

        assertThat(lookupStarted.await(2, TimeUnit.SECONDS))
                .as("cancellation cleanup must verify whether the row references the temp file")
                .isTrue();
        assertThat(inserted.get()).isNotNull();
        assertThat(inserted.get().tempPath()).isEqualTo(TEMP_PATH);
        assertThat(tempExists.get()).isTrue();
    }

    @Test
    void concurrentApprovalsPromoteOnlyForTheSinglePendingOwner() throws InterruptedException {
        var repository = mock(KnowledgeDocumentRepository.class);
        var storage = mock(KnowledgeFileStorage.class);
        var catalog = mock(KnowledgeCatalog.class);
        var pending = row("doc-1", ScopeType.ORG, "tenant-a", "org-a", "uploader", Status.PENDING);
        var persistedStatus = new AtomicReference<>(Status.PENDING);
        var initialReads = new AtomicInteger();
        var bothInitialReads = new CountDownLatch(2);
        var promotionCount = new AtomicInteger();
        var firstPromotionStarted = new CountDownLatch(1);
        var releasePromotion = new CountDownLatch(1);
        var casCount = new AtomicInteger();
        var completion = new CountDownLatch(2);
        var claimAvailable = new AtomicBoolean();
        when(repository.findById("doc-1")).thenAnswer(call -> {
            if (initialReads.incrementAndGet() <= 2) {
                bothInitialReads.countDown();
                return Mono.just(pending);
            }
            return Mono.just(persistedStatus.get() == Status.PENDING
                    ? pending : approved(pending, "uploader-summary"));
        });
        when(storage.promoteToLib(eq("doc-1"), any())).thenAnswer(call -> Mono.<Void>create(sink -> {
            promotionCount.incrementAndGet();
            firstPromotionStarted.countDown();
            awaitLatch(releasePromotion);
            sink.success();
        }).subscribeOn(Schedulers.boundedElastic()));
        when(repository.claimPendingApproval(eq("doc-1"), anyString(), any()))
                .thenAnswer(call -> Mono.just(claimAvailable.compareAndSet(false, true) ? 1L : 0L));
        when(repository.approveClaimedPending(eq("doc-1"), anyString(), anyString(), eq("platform-admin"), any()))
                .thenAnswer(call -> {
                    var updated = persistedStatus.compareAndSet(Status.PENDING, Status.APPROVED);
                    casCount.incrementAndGet();
                    return Mono.just(updated ? 1L : 0L);
                });
        when(repository.recoverPendingApprovalClaim(anyString(), anyString(), anyString())).thenReturn(Mono.just(1L));
        when(repository.releasePendingApprovalClaim(anyString(), anyString())).thenReturn(Mono.just(1L));
        when(storage.deleteLibraryArtifacts("doc-1")).thenReturn(Mono.empty());
        when(storage.deleteTemp("doc-1")).thenReturn(Mono.empty());
        doAnswer(call -> null).when(catalog).reload();
        var service = service(repository, storage, mock(TenantLlmClientResolver.class), catalog);
        var outcome = new AtomicReference<ActionResults>();

        var first = service.approve("doc-1", identity("platform-admin", null, null, true, false), null)
                .doOnNext(result -> {
                    synchronized (outcome) {
                        if (outcome.get() == null) outcome.set(new ActionResults());
                        outcome.get().add(result);
                    }
                    completion.countDown();
                }).subscribe();
        try {
            assertThat(firstPromotionStarted.await(2, TimeUnit.SECONDS)).isTrue();
            var second = service.approve("doc-1", identity("platform-admin", null, null, true, false), null)
                    .doOnNext(result -> {
                        synchronized (outcome) {
                            if (outcome.get() == null) outcome.set(new ActionResults());
                            outcome.get().add(result);
                        }
                        completion.countDown();
                    }).subscribe();
            assertThat(bothInitialReads.await(2, TimeUnit.SECONDS)).isTrue();
            releasePromotion.countDown();
            assertThat(completion.await(3, TimeUnit.SECONDS)).isTrue();
            second.dispose();
        } finally {
            releasePromotion.countDown();
            first.dispose();
        }

        assertThat(promotionCount.get()).isEqualTo(1);
        assertThat(casCount.get()).isEqualTo(1);
        assertThat(outcome.get().values()).containsExactlyInAnyOrder(ActionResult.OK, ActionResult.CONFLICT);
        verify(storage).deleteLibraryArtifacts("doc-1");
    }

    @Test
    void catalogReloadFailureDoesNotExposeFilesystemPathOrCause() {
        var repository = mock(KnowledgeDocumentRepository.class);
        var storage = mock(KnowledgeFileStorage.class);
        var catalog = mock(KnowledgeCatalog.class);
        var pending = row("doc-1", ScopeType.ORG, "tenant-a", "org-a", "uploader", Status.PENDING);
        claimSucceeds(repository);
        when(repository.findById("doc-1")).thenReturn(Mono.just(pending), Mono.just(approved(pending, "summary")));
        when(storage.promoteToLib(eq("doc-1"), any())).thenReturn(Mono.empty());
        when(repository.approveClaimedPending(eq("doc-1"), anyString(), anyString(), eq("platform-admin"), any()))
                .thenReturn(Mono.just(1L));
        when(storage.deleteLibraryArtifacts("doc-1")).thenReturn(Mono.empty());
        doThrow(new UncheckedIOException("failed to list knowledge lib dir /private/configured/path",
                new IOException("/private/configured/path"))).when(catalog).reload();
        var service = service(repository, storage, mock(TenantLlmClientResolver.class), catalog);

        assertThatThrownBy(() -> service.approve("doc-1",
                identity("platform-admin", null, null, true, false), null).block())
                .hasMessage("knowledge approval workflow failed")
                .hasNoCause();
    }

    @Test
    void unauthorizedCrossTenantAdminCannotApproveOrRejectEvenWithSameOrgId() {
        var repository = mock(KnowledgeDocumentRepository.class);
        var storage = mock(KnowledgeFileStorage.class);
        var row = row("doc-1", ScopeType.ORG, "tenant-a", "shared-org", "uploader", Status.PENDING);
        when(repository.findById("doc-1")).thenReturn(Mono.just(row));
        var service = service(repository, storage, mock(TenantLlmClientResolver.class), mock(KnowledgeCatalog.class));
        var otherTenantAdmin = identity("admin-b", "tenant-b", null, false, true);

        assertThat(service.approve("doc-1", otherTenantAdmin, null).block())
                .isEqualTo(KnowledgeDocumentService.ActionResult.FORBIDDEN);
        assertThat(service.reject("doc-1", otherTenantAdmin).block())
                .isEqualTo(KnowledgeDocumentService.ActionResult.FORBIDDEN);

        verifyNoInteractions(storage);
        verify(repository, never()).claimPendingApproval(anyString(), anyString(), any());
        verify(repository, never()).approveClaimedPending(anyString(), anyString(), anyString(), anyString(), any());
        verify(repository, never()).rejectIfPending(anyString(), anyString(), any());
    }

    @Test
    void listAndGetApplyTheScopedManagementPolicy() {
        var repository = mock(KnowledgeDocumentRepository.class);
        var own = row("own", ScopeType.ORG, "tenant-a", "org-a", "user-a", Status.PENDING);
        var anotherUsers = row("other-user", ScopeType.ORG, "tenant-a", "org-a", "user-b", Status.PENDING);
        var otherTenant = row("other-tenant", ScopeType.ORG, "tenant-b", "org-a", "user-a", Status.PENDING);
        when(repository.findByStatus(Status.PENDING)).thenReturn(reactor.core.publisher.Flux.just(own, anotherUsers, otherTenant));
        when(repository.findById("own")).thenReturn(Mono.just(own));
        when(repository.findById("other-user")).thenReturn(Mono.just(anotherUsers));
        var service = service(repository, mock(KnowledgeFileStorage.class),
                mock(TenantLlmClientResolver.class), mock(KnowledgeCatalog.class));

        assertThat(service.list(Status.PENDING, ordinaryUser()).collectList().block())
                .extracting(KnowledgeDocumentRow::id).containsExactly("own");
        assertThat(service.get("own", ordinaryUser()).block()).isEqualTo(own);
        assertThat(service.get("other-user", ordinaryUser()).blockOptional()).isEmpty();
    }

    @Test
    void tenantAdminCanRejectOnlyManagedOrgPendingRows() {
        var repository = mock(KnowledgeDocumentRepository.class);
        var pending = row("doc-1", ScopeType.ORG, "tenant-a", "org-a", "uploader", Status.PENDING);
        when(repository.findById("doc-1")).thenReturn(Mono.just(pending));
        when(repository.claimPendingApproval(eq("doc-1"), anyString(), any())).thenReturn(Mono.just(1L));
        when(repository.rejectClaimedPending(eq("doc-1"), anyString(), eq("tenant-admin"), any()))
                .thenReturn(Mono.just(1L));
        when(repository.recoverPendingApprovalClaim(anyString(), anyString(), anyString())).thenReturn(Mono.just(1L));
        when(repository.releasePendingApprovalClaim(anyString(), anyString())).thenReturn(Mono.just(1L));
        var storage = mock(KnowledgeFileStorage.class);
        when(storage.deleteLibraryArtifacts("doc-1")).thenReturn(Mono.empty());
        var service = service(repository, storage,
                mock(TenantLlmClientResolver.class), mock(KnowledgeCatalog.class));

        var result = service.reject("doc-1", identity("tenant-admin", "tenant-a", null, false, true)).block();

        assertThat(result).isEqualTo(KnowledgeDocumentService.ActionResult.OK);
        verify(repository).rejectClaimedPending(eq("doc-1"), anyString(), eq("tenant-admin"), any());
    }

    @Test
    void rejectClaimsBeforeRemovingAnyLibraryArtifactsAndChangingState() {
        var repository = mock(KnowledgeDocumentRepository.class);
        var storage = mock(KnowledgeFileStorage.class);
        var pending = row("doc-1", ScopeType.ORG, "tenant-a", "org-a", "uploader", Status.PENDING);
        when(repository.findById("doc-1")).thenReturn(Mono.just(pending));
        when(repository.claimPendingApproval(eq("doc-1"), anyString(), any())).thenReturn(Mono.just(1L));
        when(storage.deleteLibraryArtifacts("doc-1")).thenReturn(Mono.empty());
        when(repository.rejectClaimedPending(eq("doc-1"), anyString(), eq("tenant-admin"), any()))
                .thenReturn(Mono.just(1L));
        var service = service(repository, storage, mock(TenantLlmClientResolver.class), mock(KnowledgeCatalog.class));

        assertThat(service.reject("doc-1", identity("tenant-admin", "tenant-a", null, false, true)).block())
                .isEqualTo(ActionResult.OK);

        var order = inOrder(repository, storage);
        order.verify(repository).claimPendingApproval(eq("doc-1"), anyString(), any());
        order.verify(storage).deleteLibraryArtifacts("doc-1");
        order.verify(repository).rejectClaimedPending(eq("doc-1"), anyString(), eq("tenant-admin"), any());
    }

    @Test
    void manualApprovalPromotesBeforeCasAndCleansTempAfterApprovedState() {
        var repository = mock(KnowledgeDocumentRepository.class);
        var storage = mock(KnowledgeFileStorage.class);
        var catalog = mock(KnowledgeCatalog.class);
        var pending = row("doc-1", ScopeType.ORG, "tenant-a", "org-a", "uploader", Status.PENDING);
        var approved = approved(pending, "approved summary");
        claimSucceeds(repository);
        when(repository.findById("doc-1")).thenReturn(Mono.just(pending), Mono.just(approved));
        when(storage.deleteLibraryArtifacts("doc-1")).thenReturn(Mono.empty());
        when(storage.promoteToLib(eq("doc-1"), any())).thenReturn(Mono.empty());
        when(repository.approveClaimedPending(eq("doc-1"), anyString(), eq("approved summary"),
                eq("platform-admin"), any()))
                .thenReturn(Mono.just(1L));
        when(storage.deleteTemp("doc-1")).thenReturn(Mono.empty());
        var service = service(repository, storage, mock(TenantLlmClientResolver.class), catalog);

        var result = service.approve("doc-1", identity("platform-admin", null, null, true, false), "approved summary").block();

        assertThat(result).isEqualTo(KnowledgeDocumentService.ActionResult.OK);
        var order = inOrder(repository, storage, catalog);
        order.verify(repository).claimPendingApproval(eq("doc-1"), anyString(), any());
        order.verify(storage).deleteLibraryArtifacts("doc-1");
        order.verify(storage).promoteToLib(eq("doc-1"), any(KnowledgeFileStorage.LibraryMetadata.class));
        order.verify(repository).approveClaimedPending(eq("doc-1"), anyString(), eq("approved summary"),
                eq("platform-admin"), any());
        order.verify(repository).findById("doc-1");
        order.verify(catalog).reload();
        order.verify(storage).deleteTemp("doc-1");
    }

    @Test
    void casConflictCompensatesPromotedFilesAndKeepsPendingTempForRecovery() {
        var repository = mock(KnowledgeDocumentRepository.class);
        var storage = mock(KnowledgeFileStorage.class);
        var pending = row("doc-1", ScopeType.ORG, "tenant-a", "org-a", "uploader", Status.PENDING);
        claimSucceeds(repository);
        when(repository.findById("doc-1")).thenReturn(Mono.just(pending), Mono.just(pending));
        when(storage.promoteToLib(eq("doc-1"), any())).thenReturn(Mono.empty());
        when(repository.approveClaimedPending(eq("doc-1"), anyString(), anyString(), eq("platform-admin"), any()))
                .thenReturn(Mono.just(0L));
        when(storage.deleteLibraryArtifacts("doc-1")).thenReturn(Mono.empty());
        var service = service(repository, storage, mock(TenantLlmClientResolver.class), mock(KnowledgeCatalog.class));

        var result = service.approve("doc-1", identity("platform-admin", null, null, true, false), null).block();

        assertThat(result).isEqualTo(KnowledgeDocumentService.ActionResult.CONFLICT);
        verify(storage, times(2)).deleteLibraryArtifacts("doc-1");
        verify(storage, never()).deleteTemp("doc-1");
        verify(repository).recoverPendingApprovalClaim(eq("doc-1"), anyString(), eq("uploader-summary"));
        verify(repository).releasePendingApprovalClaim(eq("doc-1"), anyString());
    }

    @Test
    void transientClaimReleaseFailureIsRetriedAfterCompensation() {
        var repository = mock(KnowledgeDocumentRepository.class);
        var storage = mock(KnowledgeFileStorage.class);
        var pending = row("doc-1", ScopeType.ORG, "tenant-a", "org-a", "uploader", Status.PENDING);
        var releaseAttempts = new AtomicInteger();
        claimSucceeds(repository);
        when(repository.findById("doc-1")).thenReturn(Mono.just(pending));
        when(storage.promoteToLib(eq("doc-1"), any())).thenReturn(Mono.empty());
        when(repository.approveClaimedPending(eq("doc-1"), anyString(), anyString(), eq("platform-admin"), any()))
                .thenReturn(Mono.just(0L));
        when(storage.deleteLibraryArtifacts("doc-1")).thenReturn(Mono.empty());
        when(repository.releasePendingApprovalClaim(eq("doc-1"), anyString())).thenAnswer(call ->
                releaseAttempts.incrementAndGet() == 1
                        ? Mono.error(new IllegalStateException("temporary database outage"))
                        : Mono.just(1L));
        var service = service(repository, storage, mock(TenantLlmClientResolver.class), mock(KnowledgeCatalog.class));

        assertThat(service.approve("doc-1", identity("platform-admin", null, null, true, false), null).block())
                .isEqualTo(ActionResult.CONFLICT);

        assertThat(releaseAttempts.get()).isEqualTo(2);
    }

    @Test
    void persistentClaimReleaseFailureUsesBoundedRetriesForLaterStaleRecovery() {
        var repository = mock(KnowledgeDocumentRepository.class);
        var storage = mock(KnowledgeFileStorage.class);
        var pending = row("doc-1", ScopeType.ORG, "tenant-a", "org-a", "uploader", Status.PENDING);
        var releaseAttempts = new AtomicInteger();
        var currentClaim = new AtomicReference<String>();
        var leaseExpired = new AtomicBoolean();
        when(repository.claimPendingApproval(eq("doc-1"), anyString(), any())).thenAnswer(call -> {
            String token = call.getArgument(1, String.class);
            if (currentClaim.get() == null || leaseExpired.get()) {
                currentClaim.set(token);
                return Mono.just(1L);
            }
            return Mono.just(0L);
        });
        when(repository.recoverPendingApprovalClaim(anyString(), anyString(), anyString())).thenReturn(Mono.just(1L));
        when(repository.findById("doc-1")).thenReturn(Mono.just(pending));
        when(storage.promoteToLib(eq("doc-1"), any())).thenReturn(Mono.empty());
        when(repository.approveClaimedPending(eq("doc-1"), anyString(), anyString(), eq("platform-admin"), any()))
                .thenReturn(Mono.just(0L));
        when(storage.deleteLibraryArtifacts("doc-1")).thenReturn(Mono.empty());
        when(repository.releasePendingApprovalClaim(eq("doc-1"), anyString())).thenAnswer(call -> {
            if (!call.getArgument(1, String.class).equals(currentClaim.get())) return Mono.just(0L);
            releaseAttempts.incrementAndGet();
            return Mono.error(new IllegalStateException("database remains unavailable"));
        });
        var service = service(repository, storage, mock(TenantLlmClientResolver.class), mock(KnowledgeCatalog.class));

        assertThat(service.approve("doc-1", identity("platform-admin", null, null, true, false), null).block())
                .isEqualTo(ActionResult.CONFLICT);

        assertThat(releaseAttempts.get()).isEqualTo(3);
        String failedAttempt = currentClaim.get();
        assertThat(repository.claimPendingApproval("doc-1", "attempt-before-expiry", Duration.ofMinutes(15)).block())
                .isZero();
        assertThat(currentClaim.get()).isEqualTo(failedAttempt);

        leaseExpired.set(true);
        assertThat(repository.claimPendingApproval("doc-1", "attempt-after-expiry", Duration.ofMinutes(15)).block())
                .isEqualTo(1L);
        assertThat(currentClaim.get()).isEqualTo("attempt-after-expiry");
        assertThat(repository.releasePendingApprovalClaim("doc-1", failedAttempt).block()).isZero();
        assertThat(currentClaim.get()).isEqualTo("attempt-after-expiry");
        verify(repository).recoverPendingApprovalClaim(eq("doc-1"), anyString(), eq("uploader-summary"));
        verify(storage, times(2)).deleteLibraryArtifacts("doc-1");
    }

    @Test
    void casErrorRollsBackAnAppliedApprovalBeforeRemovingLibraryArtifacts() {
        var repository = mock(KnowledgeDocumentRepository.class);
        var storage = mock(KnowledgeFileStorage.class);
        var pending = row("doc-1", ScopeType.ORG, "tenant-a", "org-a", "uploader", Status.PENDING);
        var claimToken = new AtomicReference<String>();
        var persistedStatus = new AtomicReference<>(Status.PENDING);
        var libraryVisible = new AtomicBoolean();
        when(repository.claimPendingApproval(eq("doc-1"), anyString(), any())).thenAnswer(call -> {
            claimToken.set(call.getArgument(1, String.class));
            return Mono.just(1L);
        });
        when(repository.findById("doc-1")).thenAnswer(call -> Mono.just(
                persistedStatus.get() == Status.APPROVED ? approved(pending, "summary") : pending));
        when(storage.promoteToLib(eq("doc-1"), any()))
                .thenReturn(Mono.fromRunnable(() -> libraryVisible.set(true)));
        when(repository.approveClaimedPending(eq("doc-1"), anyString(), anyString(), eq("platform-admin"), any()))
                .thenAnswer(call -> Mono.defer(() -> {
                    persistedStatus.set(Status.APPROVED);
                    return Mono.error(new IllegalStateException("write acknowledgment lost"));
                }));
        when(repository.recoverPendingApprovalClaim(eq("doc-1"), anyString(), eq("uploader-summary")))
                .thenAnswer(call -> {
                    if (!call.getArgument(1, String.class).equals(claimToken.get())) return Mono.just(0L);
                    persistedStatus.set(Status.PENDING);
                    return Mono.just(1L);
                });
        when(storage.deleteLibraryArtifacts("doc-1"))
                .thenReturn(Mono.fromRunnable(() -> libraryVisible.set(false)));
        when(repository.releasePendingApprovalClaim(eq("doc-1"), anyString())).thenAnswer(call -> {
            if (!call.getArgument(1, String.class).equals(claimToken.get())) return Mono.just(0L);
            claimToken.set(null);
            return Mono.just(1L);
        });
        var service = service(repository, storage, mock(TenantLlmClientResolver.class), mock(KnowledgeCatalog.class));

        StepVerifier.create(service.approve("doc-1", identity("platform-admin", null, null, true, false), null))
                .expectErrorMessage("knowledge approval workflow failed")
                .verify();

        var order = inOrder(repository, storage);
        order.verify(repository).recoverPendingApprovalClaim(eq("doc-1"), anyString(), eq("uploader-summary"));
        order.verify(storage).deleteLibraryArtifacts("doc-1");
        order.verify(repository).releasePendingApprovalClaim(eq("doc-1"), anyString());
        assertThat(persistedStatus.get()).isEqualTo(Status.PENDING);
        assertThat(libraryVisible.get()).isFalse();
        assertThat(claimToken.get()).isNull();
    }

    private static KnowledgeDocumentService service(KnowledgeDocumentRepository repository,
            KnowledgeFileStorage storage, TenantLlmClientResolver resolver, KnowledgeCatalog catalog) {
        return service(repository, storage, resolver, catalog, Duration.ofSeconds(30));
    }

    private static KnowledgeDocumentService service(KnowledgeDocumentRepository repository,
            KnowledgeFileStorage storage, TenantLlmClientResolver resolver, KnowledgeCatalog catalog,
            Duration summaryTimeout) {
        var properties = new KnowledgeProperties();
        properties.setSummaryTimeout(summaryTimeout);
        return new KnowledgeDocumentService(repository, storage, resolver, catalog,
                new KnowledgeAccessPolicy(), properties);
    }

    private static void claimSucceeds(KnowledgeDocumentRepository repository) {
        when(repository.claimPendingApproval(anyString(), anyString(), any())).thenReturn(Mono.just(1L));
        when(repository.recoverPendingApprovalClaim(anyString(), anyString(), anyString())).thenReturn(Mono.just(1L));
        when(repository.releasePendingApprovalClaim(anyString(), anyString())).thenReturn(Mono.just(1L));
    }

    private static TenantLlmClientResolver resolverReturningSummary(String summary) {
        var resolver = mock(TenantLlmClientResolver.class);
        var llm = mock(LlmClient.class);
        when(resolver.resolve("tenant-a")).thenReturn(Mono.just(llm));
        when(llm.summarize(anyString())).thenReturn(Mono.just(summary));
        return resolver;
    }

    private static IdentityContext ordinaryUser() {
        return identity("user-a", "tenant-a", "org-a", false, false);
    }

    private static IdentityContext identity(String subject, String tenant, String org,
            boolean platformAdmin, boolean tenantAdmin) {
        return new IdentityContext(subject, subject, tenant, tenant == null ? null : "Tenant A", org,
                org == null ? null : "Org A", java.util.Set.of(), java.util.Set.of("org-a"),
                java.util.Set.of("org-a"), platformAdmin, tenantAdmin);
    }

    private static FilePart file(String filename) {
        var file = mock(FilePart.class);
        when(file.filename()).thenReturn(filename);
        return file;
    }

    private static KnowledgeDocumentRow row(String id, ScopeType scope, String tenant, String org,
            String uploader, Status status) {
        return new KnowledgeDocumentRow(id, "title", "doc.md", TEMP_PATH, status, "uploader-summary", uploader,
                LocalDateTime.of(2026, 10, 10, 12, 0), null, null, scope, tenant, "Tenant A", org, "Org A");
    }

    private static KnowledgeDocumentRow approved(KnowledgeDocumentRow row, String summary) {
        return new KnowledgeDocumentRow(row.id(), row.title(), row.originalFilename(), row.tempPath(), Status.APPROVED,
                summary, row.uploaderUserId(), row.uploadedAt(), "platform-admin", LocalDateTime.of(2026, 10, 10, 12, 1),
                row.scopeType(), row.tenantId(), row.tenantName(), row.orgId(), row.orgName());
    }

    private static void awaitLatch(CountDownLatch latch) {
        try {
            if (!latch.await(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException("test latch timed out");
            }
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(error);
        }
    }

    private static final class ActionResults {
        private final java.util.List<ActionResult> values = new java.util.ArrayList<>();
        synchronized void add(ActionResult result) { values.add(result); }
        synchronized java.util.List<ActionResult> values() { return java.util.List.copyOf(values); }
    }
}
