package io.github.sukimiya.agileagent.knowledge;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

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

import reactor.core.publisher.Mono;

class KnowledgeFileStorageTest {

    @TempDir Path tempDir;
    @TempDir Path libDir;
    KnowledgeProperties props;
    KnowledgeFileStorage storage;

    @BeforeEach
    void setUp() {
        props = new KnowledgeProperties();
        props.setTempDir(tempDir.toString());
        props.setLibDir(libDir.toString());
        props.setMaxSizeMb(1);
        storage = new KnowledgeFileStorage(props);
    }

    private static FilePart mockFilePart(String filename, String contentType, byte[] bytes) {
        var part = mock(FilePart.class);
        when(part.filename()).thenReturn(filename);
        var headers = new HttpHeaders();
        if (contentType != null) headers.setContentType(MediaType.parseMediaType(contentType));
        when(part.headers()).thenReturn(headers);
        when(part.transferTo(any(Path.class))).thenAnswer(invocation -> {
            Path target = invocation.getArgument(0);
            Files.write(target, bytes);
            return Mono.empty();
        });
        return part;
    }

    @Test
    void savesMarkdownFileAndReturnsContent() {
        var part = mockFilePart("doc.md", "text/markdown",
                "# 标题\n正文内容".getBytes(StandardCharsets.UTF_8));

        var result = storage.saveTemp("k1", part).block();

        assertThat(result.tempPath()).endsWith("k1.md");
        assertThat(Files.exists(Path.of(result.tempPath()))).isTrue();
        assertThat(result.content()).isEqualTo("# 标题\n正文内容");
    }

    @Test
    void acceptsPlainTextContentType() {
        var part = mockFilePart("doc.txt", "text/plain", "hello".getBytes(StandardCharsets.UTF_8));

        var result = storage.saveTemp("k1", part).block();

        assertThat(result.content()).isEqualTo("hello");
    }

    @Test
    void rejectsDisallowedContentType() {
        var part = mockFilePart("doc.pdf", "application/pdf", "fake".getBytes(StandardCharsets.UTF_8));

        assertThatThrownBy(() -> storage.saveTemp("k1", part).block())
                .isInstanceOf(KnowledgeFileStorage.RejectedException.class)
                .satisfies(e -> assertThat(((KnowledgeFileStorage.RejectedException) e).getCode())
                        .isEqualTo("FILE_REJECTED"));
    }

    @Test
    void rejectsFileExceedingSizeLimit() {
        var big = new byte[2 * 1024 * 1024];
        var part = mockFilePart("big.md", "text/markdown", big);

        assertThatThrownBy(() -> storage.saveTemp("k1", part).block())
                .isInstanceOf(KnowledgeFileStorage.RejectedException.class)
                .satisfies(e -> assertThat(((KnowledgeFileStorage.RejectedException) e).getCode())
                        .isEqualTo("FILE_REJECTED"));
    }

    @Test
    void rejectsMissingContentType() {
        var part = mockFilePart("doc.md", null, "content".getBytes(StandardCharsets.UTF_8));

        assertThatThrownBy(() -> storage.saveTemp("k1", part).block())
                .isInstanceOf(KnowledgeFileStorage.RejectedException.class)
                .satisfies(e -> assertThat(((KnowledgeFileStorage.RejectedException) e).getCode())
                        .isEqualTo("FILE_REJECTED"));
    }

    @Test
    void promoteToLibWritesOriginalAndMetadataFiles() throws Exception {
        var tempFile = tempDir.resolve("k1.md");
        Files.writeString(tempFile, "原文内容");
        var approvedAt = LocalDateTime.of(2026, 7, 27, 10, 0);

        storage.promoteToLib("k1", tempFile.toString(), "标题", "摘要内容", "user-1", approvedAt).block();

        var libFile = libDir.resolve("k1.md");
        var metaFile = libDir.resolve("k1.meta.json");
        assertThat(Files.readString(libFile)).isEqualTo("原文内容");
        assertThat(Files.readString(metaFile))
                .contains("\"title\"", "标题", "\"summary\"", "摘要内容", "\"approvedBy\"", "user-1");
    }
}
