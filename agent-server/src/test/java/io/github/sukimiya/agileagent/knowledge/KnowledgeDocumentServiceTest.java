package io.github.sukimiya.agileagent.knowledge;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.time.LocalDateTime;

import org.junit.jupiter.api.Test;
import org.springframework.http.codec.multipart.FilePart;

import io.github.sukimiya.agileagent.knowledge.KnowledgeDocumentRepository.KnowledgeDocumentRow;
import io.github.sukimiya.agileagent.knowledge.KnowledgeDocumentRepository.KnowledgeDocumentRow.Status;
import io.github.sukimiya.agileagent.knowledge.KnowledgeFileStorage.UploadedFile;
import io.github.sukimiya.agileagent.service.LlmClient;

import reactor.core.publisher.Mono;

class KnowledgeDocumentServiceTest {

    @Test
    void uploadSavesFileGeneratesSummaryAndInsertsPendingRow() {
        var repository = mock(KnowledgeDocumentRepository.class);
        var storage = mock(KnowledgeFileStorage.class);
        var llm = mock(LlmClient.class);
        var file = mock(FilePart.class);
        when(file.filename()).thenReturn("doc.md");
        when(storage.saveTemp(any(), eq(file))).thenReturn(Mono.just(new UploadedFile("/tmp/x.md", "文档内容")));
        when(llm.summarize("文档内容")).thenReturn(Mono.just("这是摘要"));
        when(repository.insert(any())).thenReturn(Mono.empty());
        var service = new KnowledgeDocumentService(repository, storage, llm, mock(KnowledgeCatalog.class));

        var row = service.upload("user-1", "自定义标题", file).block();

        assertThat(row.title()).isEqualTo("自定义标题");
        assertThat(row.originalFilename()).isEqualTo("doc.md");
        assertThat(row.status()).isEqualTo(Status.PENDING);
        assertThat(row.summary()).isEqualTo("这是摘要");
        assertThat(row.uploaderUserId()).isEqualTo("user-1");
        verify(repository).insert(row);
    }

    @Test
    void uploadFallsBackToFilenameWhenTitleBlank() {
        var repository = mock(KnowledgeDocumentRepository.class);
        var storage = mock(KnowledgeFileStorage.class);
        var llm = mock(LlmClient.class);
        var file = mock(FilePart.class);
        when(file.filename()).thenReturn("doc.md");
        when(storage.saveTemp(any(), eq(file))).thenReturn(Mono.just(new UploadedFile("/tmp/x.md", "内容")));
        when(llm.summarize(any())).thenReturn(Mono.just("摘要"));
        when(repository.insert(any())).thenReturn(Mono.empty());
        var service = new KnowledgeDocumentService(repository, storage, llm, mock(KnowledgeCatalog.class));

        var row = service.upload("user-1", "  ", file).block();

        assertThat(row.title()).isEqualTo("doc.md");
    }

    @Test
    void uploadDegradesToEmptySummaryWhenLlmFails() {
        var repository = mock(KnowledgeDocumentRepository.class);
        var storage = mock(KnowledgeFileStorage.class);
        var llm = mock(LlmClient.class);
        var file = mock(FilePart.class);
        when(file.filename()).thenReturn("doc.md");
        when(storage.saveTemp(any(), eq(file))).thenReturn(Mono.just(new UploadedFile("/tmp/x.md", "内容")));
        when(llm.summarize(any())).thenReturn(Mono.error(new RuntimeException("LLM 超时")));
        when(repository.insert(any())).thenReturn(Mono.empty());
        var service = new KnowledgeDocumentService(repository, storage, llm, mock(KnowledgeCatalog.class));

        var row = service.upload("user-1", "标题", file).block();

        assertThat(row.summary()).isEmpty();
    }

    @Test
    void approveOnUnknownIdReturnsNotFound() {
        var repository = mock(KnowledgeDocumentRepository.class);
        var storage = mock(KnowledgeFileStorage.class);
        var llm = mock(LlmClient.class);
        when(repository.findById("missing")).thenReturn(Mono.empty());
        var service = new KnowledgeDocumentService(repository, storage, llm, mock(KnowledgeCatalog.class));

        var result = service.approve("missing", "user-1", null).block();

        assertThat(result).isEqualTo(KnowledgeDocumentService.ActionResult.NOT_FOUND);
    }

    @Test
    void approveOnPendingRowPromotesToLibAndReturnsOk() {
        var repository = mock(KnowledgeDocumentRepository.class);
        var storage = mock(KnowledgeFileStorage.class);
        var llm = mock(LlmClient.class);
        var existing = new KnowledgeDocumentRow("k1", "标题", "doc.md", "/tmp/k1.md",
                Status.PENDING, "原摘要", "user-1", LocalDateTime.now(), null, null);
        when(repository.findById("k1")).thenReturn(Mono.just(existing));
        when(storage.promoteToLib(eq("k1"), eq("/tmp/k1.md"), eq("标题"), eq("原摘要"), eq("admin-1"), any()))
                .thenReturn(Mono.empty());
        when(repository.approveIfPending(eq("k1"), eq("原摘要"), eq("admin-1"), any())).thenReturn(Mono.just(1L));
        var service = new KnowledgeDocumentService(repository, storage, llm, mock(KnowledgeCatalog.class));

        var result = service.approve("k1", "admin-1", null).block();

        assertThat(result).isEqualTo(KnowledgeDocumentService.ActionResult.OK);
        verify(storage).promoteToLib(eq("k1"), eq("/tmp/k1.md"), eq("标题"), eq("原摘要"), eq("admin-1"), any());
        verify(repository).approveIfPending(eq("k1"), eq("原摘要"), eq("admin-1"), any());
    }

    @Test
    void approveReloadsKnowledgeCatalogSoNewDocumentIsImmediatelySearchable() {
        var repository = mock(KnowledgeDocumentRepository.class);
        var storage = mock(KnowledgeFileStorage.class);
        var llm = mock(LlmClient.class);
        var catalog = mock(KnowledgeCatalog.class);
        var existing = new KnowledgeDocumentRow("k1", "标题", "doc.md", "/tmp/k1.md",
                Status.PENDING, "原摘要", "user-1", LocalDateTime.now(), null, null);
        when(repository.findById("k1")).thenReturn(Mono.just(existing));
        when(storage.promoteToLib(any(), any(), any(), any(), any(), any())).thenReturn(Mono.empty());
        when(repository.approveIfPending(any(), any(), any(), any())).thenReturn(Mono.just(1L));
        var service = new KnowledgeDocumentService(repository, storage, llm, catalog);

        service.approve("k1", "admin-1", null).block();

        verify(catalog).reload();
    }

    @Test
    void approveDoesNotReloadCatalogWhenAlreadyProcessed() {
        var repository = mock(KnowledgeDocumentRepository.class);
        var storage = mock(KnowledgeFileStorage.class);
        var llm = mock(LlmClient.class);
        var catalog = mock(KnowledgeCatalog.class);
        var existing = new KnowledgeDocumentRow("k1", "标题", "doc.md", "/tmp/k1.md",
                Status.APPROVED, "摘要", "user-1", LocalDateTime.now(), "admin-0", LocalDateTime.now());
        when(repository.findById("k1")).thenReturn(Mono.just(existing));
        var service = new KnowledgeDocumentService(repository, storage, llm, catalog);

        service.approve("k1", "admin-1", null).block();

        verifyNoInteractions(catalog);
    }

    @Test
    void approveUsesOverrideSummaryWhenProvided() {
        var repository = mock(KnowledgeDocumentRepository.class);
        var storage = mock(KnowledgeFileStorage.class);
        var llm = mock(LlmClient.class);
        var existing = new KnowledgeDocumentRow("k1", "标题", "doc.md", "/tmp/k1.md",
                Status.PENDING, "原摘要", "user-1", LocalDateTime.now(), null, null);
        when(repository.findById("k1")).thenReturn(Mono.just(existing));
        when(storage.promoteToLib(any(), any(), any(), eq("修改后的摘要"), any(), any())).thenReturn(Mono.empty());
        when(repository.approveIfPending(eq("k1"), eq("修改后的摘要"), eq("admin-1"), any())).thenReturn(Mono.just(1L));
        var service = new KnowledgeDocumentService(repository, storage, llm, mock(KnowledgeCatalog.class));

        service.approve("k1", "admin-1", "修改后的摘要").block();

        verify(storage).promoteToLib(any(), any(), any(), eq("修改后的摘要"), any(), any());
        verify(repository).approveIfPending(eq("k1"), eq("修改后的摘要"), eq("admin-1"), any());
    }

    @Test
    void approveOnAlreadyProcessedRowReturnsConflict() {
        var repository = mock(KnowledgeDocumentRepository.class);
        var storage = mock(KnowledgeFileStorage.class);
        var llm = mock(LlmClient.class);
        var existing = new KnowledgeDocumentRow("k1", "标题", "doc.md", "/tmp/k1.md",
                Status.APPROVED, "摘要", "user-1", LocalDateTime.now(), "admin-0", LocalDateTime.now());
        when(repository.findById("k1")).thenReturn(Mono.just(existing));
        var service = new KnowledgeDocumentService(repository, storage, llm, mock(KnowledgeCatalog.class));

        var result = service.approve("k1", "admin-1", null).block();

        assertThat(result).isEqualTo(KnowledgeDocumentService.ActionResult.CONFLICT);
        verifyNoInteractions(storage);
        verify(repository, never()).approveIfPending(any(), any(), any(), any());
    }

    @Test
    void rejectOnUnknownIdReturnsNotFound() {
        var repository = mock(KnowledgeDocumentRepository.class);
        var storage = mock(KnowledgeFileStorage.class);
        var llm = mock(LlmClient.class);
        when(repository.findById("missing")).thenReturn(Mono.empty());
        var service = new KnowledgeDocumentService(repository, storage, llm, mock(KnowledgeCatalog.class));

        var result = service.reject("missing", "user-1").block();

        assertThat(result).isEqualTo(KnowledgeDocumentService.ActionResult.NOT_FOUND);
    }

    @Test
    void rejectOnPendingRowReturnsOk() {
        var repository = mock(KnowledgeDocumentRepository.class);
        var storage = mock(KnowledgeFileStorage.class);
        var llm = mock(LlmClient.class);
        var existing = new KnowledgeDocumentRow("k1", "标题", "doc.md", "/tmp/k1.md",
                Status.PENDING, "摘要", "user-1", LocalDateTime.now(), null, null);
        when(repository.findById("k1")).thenReturn(Mono.just(existing));
        when(repository.rejectIfPending(eq("k1"), eq("admin-1"), any())).thenReturn(Mono.just(1L));
        var service = new KnowledgeDocumentService(repository, storage, llm, mock(KnowledgeCatalog.class));

        var result = service.reject("k1", "admin-1").block();

        assertThat(result).isEqualTo(KnowledgeDocumentService.ActionResult.OK);
    }

    @Test
    void rejectOnAlreadyProcessedRowReturnsConflict() {
        var repository = mock(KnowledgeDocumentRepository.class);
        var storage = mock(KnowledgeFileStorage.class);
        var llm = mock(LlmClient.class);
        var existing = new KnowledgeDocumentRow("k1", "标题", "doc.md", "/tmp/k1.md",
                Status.REJECTED, "摘要", "user-1", LocalDateTime.now(), "admin-0", LocalDateTime.now());
        when(repository.findById("k1")).thenReturn(Mono.just(existing));
        when(repository.rejectIfPending(any(), any(), any())).thenReturn(Mono.just(0L));
        var service = new KnowledgeDocumentService(repository, storage, llm, mock(KnowledgeCatalog.class));

        var result = service.reject("k1", "admin-1").block();

        assertThat(result).isEqualTo(KnowledgeDocumentService.ActionResult.CONFLICT);
    }
}
