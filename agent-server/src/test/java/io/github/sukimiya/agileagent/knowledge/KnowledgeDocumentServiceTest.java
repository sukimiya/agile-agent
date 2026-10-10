package io.github.sukimiya.agileagent.knowledge;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.codec.multipart.FilePart;

import io.github.sukimiya.agileagent.config.KnowledgeProperties;
import io.github.sukimiya.agileagent.knowledge.KnowledgeDocumentRepository.KnowledgeDocumentRow;
import io.github.sukimiya.agileagent.knowledge.KnowledgeDocumentRepository.KnowledgeDocumentRow.ScopeType;
import io.github.sukimiya.agileagent.knowledge.KnowledgeDocumentRepository.KnowledgeDocumentRow.Status;
import io.github.sukimiya.agileagent.service.LlmClient;
import io.github.sukimiya.agileagent.service.TenantLlmClientResolver;
import io.github.sukimiya.agileagent.spi.identity.IdentityContext;

import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

class KnowledgeDocumentServiceTest {

    private static final String TEMP_PATH = "/tmp/knowledge-test/document.md";

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
        when(storage.promoteToLib(anyString(), any())).thenReturn(Mono.empty());
        when(repository.approveIfPending(anyString(), anyString(), eq("platform-admin"), any())).thenReturn(Mono.just(1L));
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
        order.verify(storage).promoteToLib(anyString(), any(KnowledgeFileStorage.LibraryMetadata.class));
        order.verify(repository).approveIfPending(anyString(), eq(""), eq("platform-admin"), any());
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
        when(storage.promoteToLib(anyString(), any())).thenReturn(Mono.empty());
        when(repository.approveIfPending(anyString(), anyString(), eq("tenant-admin"), any())).thenReturn(Mono.just(1L));
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
        order.verify(storage).promoteToLib(anyString(), any(KnowledgeFileStorage.LibraryMetadata.class));
        order.verify(repository).approveIfPending(anyString(), eq("summary"), eq("tenant-admin"), any());
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
        when(repository.findById(anyString())).thenAnswer(call -> Mono.just(inserted.get()));
        when(repository.revertApprovalIfMatching(anyString(), anyString(), anyString(), any()))
                .thenReturn(Mono.just(0L));
        when(storage.promoteToLib(anyString(), any()))
                .thenReturn(Mono.error(new KnowledgeFileStorage.StorageFailureException()));
        when(storage.deleteLibraryArtifacts(anyString())).thenReturn(Mono.empty());
        var service = service(repository, storage, resolver, mock(KnowledgeCatalog.class));

        assertThatThrownBy(() -> service.upload(
                identity("tenant-admin", "tenant-a", null, false, true), null, file("doc.md")).block())
                .isInstanceOf(KnowledgeFileStorage.StorageFailureException.class);

        assertThat(inserted.get().status()).isEqualTo(Status.PENDING);
        assertThat(inserted.get().scopeType()).isEqualTo(ScopeType.TENANT);
        verify(repository, never()).approveIfPending(anyString(), anyString(), anyString(), any());
        verify(storage, never()).deleteTemp(anyString());
        verify(storage, never()).deleteLibraryArtifacts(anyString());
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
    void unauthorizedCrossTenantAdminCannotApproveOrRejectEvenWithSameOrgId() {
        var repository = mock(KnowledgeDocumentRepository.class);
        var storage = mock(KnowledgeFileStorage.class);
        var row = row("doc-1", ScopeType.ORG, "tenant-a", "shared-org", "uploader", Status.PENDING);
        when(repository.findById("doc-1")).thenReturn(Mono.just(row));
        when(repository.revertApprovalIfMatching(anyString(), anyString(), anyString(), any())).thenReturn(Mono.just(0L));
        var service = service(repository, storage, mock(TenantLlmClientResolver.class), mock(KnowledgeCatalog.class));
        var otherTenantAdmin = identity("admin-b", "tenant-b", null, false, true);

        assertThat(service.approve("doc-1", otherTenantAdmin, null).block())
                .isEqualTo(KnowledgeDocumentService.ActionResult.FORBIDDEN);
        assertThat(service.reject("doc-1", otherTenantAdmin).block())
                .isEqualTo(KnowledgeDocumentService.ActionResult.FORBIDDEN);

        verifyNoInteractions(storage);
        verify(repository, never()).approveIfPending(anyString(), anyString(), anyString(), any());
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
        when(repository.rejectIfPending(eq("doc-1"), eq("tenant-admin"), any())).thenReturn(Mono.just(1L));
        var service = service(repository, mock(KnowledgeFileStorage.class),
                mock(TenantLlmClientResolver.class), mock(KnowledgeCatalog.class));

        var result = service.reject("doc-1", identity("tenant-admin", "tenant-a", null, false, true)).block();

        assertThat(result).isEqualTo(KnowledgeDocumentService.ActionResult.OK);
        verify(repository).rejectIfPending(eq("doc-1"), eq("tenant-admin"), any());
    }

    @Test
    void manualApprovalPromotesBeforeCasAndCleansTempAfterApprovedState() {
        var repository = mock(KnowledgeDocumentRepository.class);
        var storage = mock(KnowledgeFileStorage.class);
        var catalog = mock(KnowledgeCatalog.class);
        var pending = row("doc-1", ScopeType.ORG, "tenant-a", "org-a", "uploader", Status.PENDING);
        var approved = approved(pending, "approved summary");
        when(repository.findById("doc-1")).thenReturn(Mono.just(pending), Mono.just(approved));
        when(storage.promoteToLib(eq("doc-1"), any())).thenReturn(Mono.empty());
        when(repository.approveIfPending(eq("doc-1"), eq("approved summary"), eq("platform-admin"), any()))
                .thenReturn(Mono.just(1L));
        when(repository.revertApprovalIfMatching(anyString(), anyString(), anyString(), any())).thenReturn(Mono.just(0L));
        when(storage.deleteTemp("doc-1")).thenReturn(Mono.empty());
        var service = service(repository, storage, mock(TenantLlmClientResolver.class), catalog);

        var result = service.approve("doc-1", identity("platform-admin", null, null, true, false), "approved summary").block();

        assertThat(result).isEqualTo(KnowledgeDocumentService.ActionResult.OK);
        var order = inOrder(storage, repository, catalog);
        order.verify(storage).promoteToLib(eq("doc-1"), any(KnowledgeFileStorage.LibraryMetadata.class));
        order.verify(repository).approveIfPending(eq("doc-1"), eq("approved summary"), eq("platform-admin"), any());
        order.verify(repository).findById("doc-1");
        order.verify(catalog).reload();
        order.verify(storage).deleteTemp("doc-1");
    }

    @Test
    void casConflictCompensatesPromotedFilesAndKeepsPendingTempForRecovery() {
        var repository = mock(KnowledgeDocumentRepository.class);
        var storage = mock(KnowledgeFileStorage.class);
        var pending = row("doc-1", ScopeType.ORG, "tenant-a", "org-a", "uploader", Status.PENDING);
        when(repository.findById("doc-1")).thenReturn(Mono.just(pending), Mono.just(pending));
        when(storage.promoteToLib(eq("doc-1"), any())).thenReturn(Mono.empty());
        when(repository.approveIfPending(eq("doc-1"), anyString(), eq("platform-admin"), any())).thenReturn(Mono.just(0L));
        when(repository.revertApprovalIfMatching(anyString(), anyString(), anyString(), any())).thenReturn(Mono.just(0L));
        when(storage.deleteLibraryArtifacts("doc-1")).thenReturn(Mono.empty());
        var service = service(repository, storage, mock(TenantLlmClientResolver.class), mock(KnowledgeCatalog.class));

        var result = service.approve("doc-1", identity("platform-admin", null, null, true, false), null).block();

        assertThat(result).isEqualTo(KnowledgeDocumentService.ActionResult.CONFLICT);
        verify(storage).deleteLibraryArtifacts("doc-1");
        verify(storage, never()).deleteTemp("doc-1");
        verify(repository, never()).revertApprovalIfMatching(anyString(), anyString(), anyString(), any());
    }

    @Test
    void casErrorRollsBackAnAppliedApprovalBeforeRemovingLibraryArtifacts() {
        var repository = mock(KnowledgeDocumentRepository.class);
        var storage = mock(KnowledgeFileStorage.class);
        var pending = row("doc-1", ScopeType.ORG, "tenant-a", "org-a", "uploader", Status.PENDING);
        when(repository.findById("doc-1")).thenReturn(Mono.just(pending), Mono.just(approved(pending, "summary")));
        when(storage.promoteToLib(eq("doc-1"), any())).thenReturn(Mono.empty());
        when(repository.approveIfPending(eq("doc-1"), anyString(), eq("platform-admin"), any()))
                .thenReturn(Mono.error(new IllegalStateException("write acknowledgment lost")));
        when(repository.revertApprovalIfMatching(eq("doc-1"), eq("uploader-summary"), eq("platform-admin"), any()))
                .thenReturn(Mono.just(1L));
        when(storage.deleteLibraryArtifacts("doc-1")).thenReturn(Mono.empty());
        var service = service(repository, storage, mock(TenantLlmClientResolver.class), mock(KnowledgeCatalog.class));

        StepVerifier.create(service.approve("doc-1", identity("platform-admin", null, null, true, false), null))
                .expectError(IllegalStateException.class)
                .verify();

        var order = inOrder(repository, storage);
        order.verify(repository).revertApprovalIfMatching(eq("doc-1"), eq("uploader-summary"),
                eq("platform-admin"), any());
        order.verify(storage).deleteLibraryArtifacts("doc-1");
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
}
