package io.github.sukimiya.agileagent.knowledge;

import static org.assertj.core.api.Assertions.*;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import io.github.sukimiya.agileagent.config.KnowledgeProperties;

class KnowledgeCatalogTest {

    private KnowledgeProperties propsFor(Path libDir) {
        var props = new KnowledgeProperties();
        props.setLibDir(libDir.toString());
        return props;
    }

    private void writeDoc(Path libDir, String id, String title, String summary, String content) throws Exception {
        Files.writeString(libDir.resolve(id + ".md"), content, StandardCharsets.UTF_8);
        Files.writeString(libDir.resolve(id + ".meta.json"), """
                {"title":"%s","summary":"%s","approvedBy":"u1","approvedAt":"2026-07-28T10:00:00"}
                """.formatted(title, summary), StandardCharsets.UTF_8);
    }

    @Test
    void compactIndexListsTitleAndSummaryPerEntry(@TempDir Path libDir) throws Exception {
        writeDoc(libDir, "doc-1", "Stage API 说明", "介绍低代码 stage 对象用法", "# 正文");

        var catalog = new KnowledgeCatalog(propsFor(libDir));

        assertThat(catalog.compactIndex()).isEqualTo("doc-1 | Stage API 说明 | 介绍低代码 stage 对象用法\n");
    }

    @Test
    void contentReturnsFullMarkdownForKnownId(@TempDir Path libDir) throws Exception {
        writeDoc(libDir, "doc-1", "标题", "摘要", "# 正文内容\n详情");

        var catalog = new KnowledgeCatalog(propsFor(libDir));

        assertThat(catalog.content("doc-1").block()).isEqualTo("# 正文内容\n详情");
    }

    @Test
    void contentReturnsEmptyForUnknownId(@TempDir Path libDir) throws Exception {
        writeDoc(libDir, "doc-1", "标题", "摘要", "正文");

        var catalog = new KnowledgeCatalog(propsFor(libDir));

        assertThat(catalog.content("no-such-id").block()).isEmpty();
    }

    @Test
    void emptyLibDirYieldsEmptyIndexWithoutThrowing(@TempDir Path libDir) {
        var catalog = new KnowledgeCatalog(propsFor(libDir));

        assertThat(catalog.compactIndex()).isEmpty();
    }

    @Test
    void missingLibDirYieldsEmptyIndexWithoutThrowing() {
        var props = new KnowledgeProperties();
        props.setLibDir("/no/such/dir/at/all");

        var catalog = new KnowledgeCatalog(props);

        assertThat(catalog.compactIndex()).isEmpty();
    }

    @Test
    void reloadPicksUpNewlyApprovedDocuments(@TempDir Path libDir) throws Exception {
        var catalog = new KnowledgeCatalog(propsFor(libDir));
        assertThat(catalog.compactIndex()).isEmpty();

        writeDoc(libDir, "doc-2", "新文档", "新摘要", "内容");
        catalog.reload();

        assertThat(catalog.compactIndex()).contains("doc-2 | 新文档 | 新摘要");
    }
}
