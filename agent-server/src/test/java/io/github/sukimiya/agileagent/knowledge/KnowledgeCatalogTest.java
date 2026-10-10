package io.github.sukimiya.agileagent.knowledge;

import static org.assertj.core.api.Assertions.*;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import io.github.sukimiya.agileagent.config.KnowledgeProperties;
import io.github.sukimiya.agileagent.knowledge.KnowledgeDocumentRepository.ApprovedArtifactPointer;
import reactor.core.publisher.Flux;

class KnowledgeCatalogTest {

    private static final String ID = "00000000-0000-0000-0000-000000000001";
    private static final String OWNER = "reviewer-1";
    private static final long VERSION = 3L;

    private KnowledgeProperties propsFor(Path libDir) {
        var props = new KnowledgeProperties();
        props.setLibDir(libDir.toString());
        return props;
    }

    private KnowledgeCatalog catalog(Path libDir, List<ApprovedArtifactPointer> pointers) {
        var repository = mock(KnowledgeDocumentRepository.class);
        when(repository.findApprovedArtifactPointers()).thenReturn(Flux.fromIterable(pointers));
        return new KnowledgeCatalog(propsFor(libDir), repository);
    }

    private void writeDoc(Path libDir, String title, String summary, String content) throws Exception {
        Files.writeString(libDir.resolve(ID + ".v" + VERSION + ".md"), content, StandardCharsets.UTF_8);
        Files.writeString(libDir.resolve(ID + ".v" + VERSION + ".meta.json"), """
                {"title":"%s","summary":"%s","approvedBy":"u1","approvedAt":"2026-07-28T10:00:00"}
                """.formatted(title, summary), StandardCharsets.UTF_8);
    }

    @Test
    void compactIndexListsTitleAndSummaryPerEntry(@TempDir Path libDir) throws Exception {
        writeDoc(libDir, "Stage API 说明", "介绍低代码 stage 对象用法", "# 正文");

        var catalog = catalog(libDir, List.of(new ApprovedArtifactPointer(ID, OWNER, VERSION)));

        assertThat(catalog.compactIndex().block()).isEqualTo(ID + " | Stage API 说明 | 介绍低代码 stage 对象用法\n");
    }

    @Test
    void contentReturnsFullMarkdownForKnownId(@TempDir Path libDir) throws Exception {
        writeDoc(libDir, "标题", "摘要", "# 正文内容\n详情");

        var catalog = catalog(libDir, List.of(new ApprovedArtifactPointer(ID, OWNER, VERSION)));

        assertThat(catalog.content(ID).block()).isEqualTo("# 正文内容\n详情");
    }

    @Test
    void contentReturnsEmptyForUnknownId(@TempDir Path libDir) throws Exception {
        writeDoc(libDir, "标题", "摘要", "正文");

        var catalog = catalog(libDir, List.of(new ApprovedArtifactPointer(ID, OWNER, VERSION)));

        assertThat(catalog.content("no-such-id").block()).isEmpty();
    }

    @Test
    void contentDoesNotFollowApprovedArtifactSymlinks(@TempDir Path tempDir) throws Exception {
        Path libDir = Files.createDirectory(tempDir.resolve("lib"));
        Path secret = Files.writeString(tempDir.resolve("outside.md"), "not a knowledge document");
        Files.createSymbolicLink(libDir.resolve(ID + ".v" + VERSION + ".md"), secret);
        Files.writeString(libDir.resolve(ID + ".v" + VERSION + ".meta.json"), "{}",
                StandardCharsets.UTF_8);
        var catalog = catalog(libDir, List.of(new ApprovedArtifactPointer(ID, OWNER, VERSION)));

        assertThat(catalog.content(ID).block()).isEmpty();
    }

    @Test
    void emptyLibDirYieldsEmptyIndexWithoutThrowing(@TempDir Path libDir) {
        var catalog = catalog(libDir, List.of());

        assertThat(catalog.compactIndex().block()).isEmpty();
    }

    @Test
    void missingLibDirYieldsEmptyIndexWithoutThrowing() {
        var props = new KnowledgeProperties();
        props.setLibDir("/no/such/dir/at/all");

        var repository = mock(KnowledgeDocumentRepository.class);
        when(repository.findApprovedArtifactPointers()).thenReturn(Flux.empty());
        var catalog = new KnowledgeCatalog(props, repository);

        assertThat(catalog.compactIndex().block()).isEmpty();
    }

    @Test
    void constructionDoesNotQueryDatabaseBeforeMigrationIsAvailable() {
        var repository = mock(KnowledgeDocumentRepository.class);
        when(repository.findApprovedArtifactPointers())
                .thenThrow(new IllegalStateException("database schema is not ready"));

        assertThatCode(() -> new KnowledgeCatalog(propsFor(Path.of("/tmp/lib")), repository))
                .doesNotThrowAnyException();
        verifyNoInteractions(repository);
    }

    @Test
    void reloadPicksUpNewlyApprovedDocuments(@TempDir Path libDir) throws Exception {
        var pointers = new AtomicReference<List<ApprovedArtifactPointer>>(List.of());
        var repository = mock(KnowledgeDocumentRepository.class);
        when(repository.findApprovedArtifactPointers()).thenAnswer(call -> Flux.fromIterable(pointers.get()));
        var catalog = new KnowledgeCatalog(propsFor(libDir), repository);
        assertThat(catalog.compactIndex().block()).isEmpty();

        writeDoc(libDir, "新文档", "新摘要", "内容");
        pointers.set(List.of(new ApprovedArtifactPointer(ID, OWNER, VERSION)));
        catalog.reload();

        assertThat(catalog.compactIndex().block()).contains(ID + " | 新文档 | 新摘要");
    }

    @Test
    void filesystemArtifactsAreInvisibleWithoutAnApprovedDatabasePointer(@TempDir Path libDir) throws Exception {
        writeDoc(libDir, "Unapproved", "not visible", "content");
        var catalog = catalog(libDir, List.of());

        assertThat(catalog.compactIndex().block()).isEmpty();
        assertThat(catalog.content(ID).block()).isEmpty();
    }

    @Test
    void compactIndexDoesNotExposeMetadataPathWhenApprovedMetadataIsMalformed(@TempDir Path libDir) throws Exception {
        Files.writeString(libDir.resolve(ID + ".v" + VERSION + ".md"), "content", StandardCharsets.UTF_8);
        Files.writeString(libDir.resolve(ID + ".v" + VERSION + ".meta.json"), "{bad json",
                StandardCharsets.UTF_8);
        var catalog = catalog(libDir, List.of(new ApprovedArtifactPointer(ID, OWNER, VERSION)));

        assertThatThrownBy(() -> catalog.compactIndex().block()).satisfies(error -> {
            assertThat(error.getMessage()).isEqualTo("failed to read approved knowledge metadata");
            assertThat(error.getMessage()).doesNotContain(libDir.toString());
        });
    }

    @Test
    void cachedApprovalCannotBeReadAfterDatabasePointerIsRemoved(@TempDir Path libDir) throws Exception {
        writeDoc(libDir, "Approved", "summary", "private content");
        var pointers = new AtomicReference<List<ApprovedArtifactPointer>>(
                List.of(new ApprovedArtifactPointer(ID, OWNER, VERSION)));
        var repository = mock(KnowledgeDocumentRepository.class);
        when(repository.findApprovedArtifactPointers()).thenAnswer(call -> Flux.fromIterable(pointers.get()));
        var catalog = new KnowledgeCatalog(propsFor(libDir), repository);
        assertThat(catalog.content(ID).block()).isEqualTo("private content");

        pointers.set(List.of());

        assertThat(catalog.content(ID).block()).isEmpty();
        assertThat(catalog.compactIndex().block()).isEmpty();
    }
}
