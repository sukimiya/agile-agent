package io.github.sukimiya.agileagent.knowledge;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.codec.multipart.FilePart;

import io.github.sukimiya.agileagent.config.KnowledgeProperties;
import io.github.sukimiya.agileagent.knowledge.KnowledgeDocumentRepository.KnowledgeDocumentRow.ScopeType;

import reactor.core.publisher.Mono;

class KnowledgeFileStorageTest {

    private static final String ID = "00000000-0000-0000-0000-000000000001";

    @TempDir Path tempDir;
    @TempDir Path libDir;
    private KnowledgeFileStorage storage;

    @BeforeEach
    void setUp() {
        var props = new KnowledgeProperties();
        props.setTempDir(tempDir.toString());
        props.setLibDir(libDir.toString());
        props.setMaxSizeMb(1);
        storage = new KnowledgeFileStorage(props);
    }

    @Test
    void savesMarkdownFileAndReturnsContent() throws Exception {
        var part = mockFilePart("doc.md", "text/markdown", "# title\nbody".getBytes(StandardCharsets.UTF_8));

        var result = storage.saveTemp(ID, part).block();

        assertThat(result.tempPath()).isEqualTo(tempDir.toRealPath().resolve(ID + ".md").toString());
        assertThat(Files.exists(Path.of(result.tempPath()))).isTrue();
        assertThat(result.content()).isEqualTo("# title\nbody");
    }

    @Test
    void tempTargetPreparationFailureIsSanitized() throws Exception {
        var blockingFile = tempDir.resolve("not-a-directory");
        Files.writeString(blockingFile, "not a directory");
        var props = new KnowledgeProperties();
        props.setTempDir(blockingFile.resolve("child").toString());
        props.setLibDir(libDir.toString());
        var storageWithInvalidTempRoot = new KnowledgeFileStorage(props);
        var part = mockFilePart("doc.md", "text/markdown", "content".getBytes(StandardCharsets.UTF_8));

        assertThatThrownBy(() -> storageWithInvalidTempRoot.saveTemp(ID, part).block())
                .isInstanceOf(KnowledgeFileStorage.StorageFailureException.class)
                .hasMessage("knowledge storage operation failed");
    }

    @Test
    void transferFailureRemovesPartiallyWrittenTempFile() {
        var part = mockFilePart("doc.md", "text/markdown", new byte[0]);
        when(part.transferTo(any(Path.class))).thenAnswer(call -> {
            Files.writeString(call.getArgument(0), "partial");
            return Mono.error(new IllegalStateException("transfer failed"));
        });

        assertThatThrownBy(() -> storage.saveTemp(ID, part).block())
                .isInstanceOf(KnowledgeFileStorage.StorageFailureException.class)
                .hasMessage("knowledge storage operation failed");

        assertThat(tempDir.resolve(ID + ".md")).doesNotExist();
    }

    @Test
    void oversizedFileIsRemovedAfterFinalizationRejectsIt() {
        var part = mockFilePart("large.md", "text/markdown", new byte[2 * 1024 * 1024]);

        assertThatThrownBy(() -> storage.saveTemp(ID, part).block())
                .isInstanceOf(KnowledgeFileStorage.RejectedException.class)
                .satisfies(error -> assertThat(((KnowledgeFileStorage.RejectedException) error).getCode())
                        .isEqualTo("FILE_REJECTED"));

        assertThat(tempDir.resolve(ID + ".md")).doesNotExist();
    }

    @Test
    void promotionWritesContentAndCompleteScopeMetadata() throws Exception {
        Files.writeString(tempDir.resolve(ID + ".md"), "original content");
        var metadata = new KnowledgeFileStorage.LibraryMetadata("Title", "Summary", "admin-1",
                LocalDateTime.of(2026, 10, 10, 10, 0), ScopeType.ORG,
                "tenant-a", "Tenant A", "org-a", "Org A");

        storage.promoteToLib(ID, metadata).block();

        assertThat(Files.readString(libDir.resolve(ID + ".md"))).isEqualTo("original content");
        assertThat(Files.readString(libDir.resolve(ID + ".meta.json")))
                .contains("\"title\":\"Title\"", "\"summary\":\"Summary\"", "\"approvedBy\":\"admin-1\"",
                        "\"scopeType\":\"ORG\"", "\"tenantId\":\"tenant-a\"", "\"tenantName\":\"Tenant A\"",
                        "\"orgId\":\"org-a\"", "\"orgName\":\"Org A\"");
        try (var files = Files.list(libDir)) {
            assertThat(files.map(path -> path.getFileName().toString()).toList())
                    .containsExactlyInAnyOrder(ID + ".md", ID + ".meta.json");
        }
    }

    @Test
    void failedPromotionRemovesPublishedAndStagingArtifacts() throws Exception {
        Files.writeString(tempDir.resolve(ID + ".md"), "original content");
        var conflictingMetadataPath = libDir.resolve(ID + ".meta.json");
        Files.createDirectory(conflictingMetadataPath);
        Files.writeString(conflictingMetadataPath.resolve("keep.txt"), "unrelated");
        var metadata = new KnowledgeFileStorage.LibraryMetadata("Title", "Summary", "admin-1",
                LocalDateTime.of(2026, 10, 10, 10, 0), ScopeType.GLOBAL, null, null, null, null);

        assertThatThrownBy(() -> storage.promoteToLib(ID, metadata).block()).isInstanceOf(RuntimeException.class);

        assertThat(libDir.resolve(ID + ".md")).doesNotExist();
        assertThat(conflictingMetadataPath.resolve("keep.txt")).hasContent("unrelated");
        try (var files = Files.list(libDir)) {
            assertThat(files.map(path -> path.getFileName().toString()).toList())
                    .containsExactly(ID + ".meta.json");
        }
    }

    @Test
    void cleanupAcceptsOnlyServerGeneratedIdsAndNeverDeletesOutsideConfiguredRoots() throws Exception {
        var outside = tempDir.getParent().resolve("outside.md");
        Files.writeString(outside, "keep");

        assertThatThrownBy(() -> storage.deleteTemp("../../outside").block())
                .isInstanceOf(KnowledgeFileStorage.RejectedException.class);

        assertThat(outside).hasContent("keep");
    }

    private static FilePart mockFilePart(String filename, String contentType, byte[] bytes) {
        var part = mock(FilePart.class);
        when(part.filename()).thenReturn(filename);
        var headers = new HttpHeaders();
        if (contentType != null) headers.setContentType(MediaType.parseMediaType(contentType));
        when(part.headers()).thenReturn(headers);
        when(part.transferTo(any(Path.class))).thenAnswer(call -> {
            Files.write(call.getArgument(0), bytes);
            return Mono.empty();
        });
        return part;
    }
}
