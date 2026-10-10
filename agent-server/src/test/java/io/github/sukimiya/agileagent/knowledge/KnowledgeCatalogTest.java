package io.github.sukimiya.agileagent.knowledge;

import static org.assertj.core.api.Assertions.*;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import io.github.sukimiya.agileagent.config.KnowledgeProperties;
import io.github.sukimiya.agileagent.knowledge.KnowledgeDocumentRepository.ApprovedArtifactPointer;
import io.github.sukimiya.agileagent.knowledge.KnowledgeDocumentRepository.KnowledgeDocumentRow.ScopeType;
import io.github.sukimiya.agileagent.spi.identity.IdentityContext;
import reactor.core.publisher.Flux;

class KnowledgeCatalogTest {

    private static final String ID = "00000000-0000-0000-0000-000000000001";
    private static final String OWNER = "reviewer-1";
    private static final long VERSION = 3L;
    private static final IdentityContext IDENTITY = identity("tenant-1", Set.of());

    private KnowledgeProperties propsFor(Path libDir) {
        var props = new KnowledgeProperties();
        props.setLibDir(libDir.toString());
        return props;
    }

    private KnowledgeCatalog catalog(Path libDir, List<ApprovedArtifactPointer> pointers) {
        var repository = mock(KnowledgeDocumentRepository.class);
        when(repository.findApprovedArtifactPointers()).thenReturn(Flux.fromIterable(pointers));
        return new KnowledgeCatalog(propsFor(libDir), repository, new KnowledgeAccessPolicy());
    }

    private void writeDoc(Path libDir, String title, String summary, String content) throws Exception {
        Files.writeString(libDir.resolve(ID + ".v" + VERSION + ".md"), content, StandardCharsets.UTF_8);
        Files.writeString(libDir.resolve(ID + ".v" + VERSION + ".meta.json"), """
                {"title":"%s","summary":"%s","approvedBy":"%s","approvedAt":"2026-07-28T10:00:00",
                 "scopeType":"GLOBAL","tenantId":null,"tenantName":null,"orgId":null,"orgName":null}
                """.formatted(title, summary, OWNER), StandardCharsets.UTF_8);
    }

    private void writeMetadata(Path libDir, ApprovedArtifactPointer pointer, String title, String summary)
            throws Exception {
        Files.writeString(libDir.resolve(pointer.id() + ".v" + pointer.version() + ".meta.json"), """
                {"title":"%s","summary":"%s","approvedBy":"%s","approvedAt":"2026-07-28T10:00:00",
                 "scopeType":"%s","tenantId":%s,"tenantName":null,"orgId":%s,"orgName":null}
                """.formatted(title, summary, OWNER, pointer.scopeType(), jsonString(pointer.tenantId()),
                        jsonString(pointer.orgId())), StandardCharsets.UTF_8);
    }

    private static String jsonString(String value) {
        return value == null ? "null" : "\"" + value + "\"";
    }

    @Test
    void compactIndexListsTitleAndSummaryPerEntry(@TempDir Path libDir) throws Exception {
        writeDoc(libDir, "Stage API 说明", "介绍低代码 stage 对象用法", "# 正文");

        var catalog = catalog(libDir, List.of(globalPointer(ID, OWNER, VERSION)));

        assertThat(catalog.compactIndex(IDENTITY).block()).isEqualTo(ID + " | Stage API 说明 | 介绍低代码 stage 对象用法\n");
    }

    @Test
    void contentReturnsFullMarkdownForKnownId(@TempDir Path libDir) throws Exception {
        writeDoc(libDir, "标题", "摘要", "# 正文内容\n详情");

        var catalog = catalog(libDir, List.of(globalPointer(ID, OWNER, VERSION)));

        assertThat(catalog.content(ID, IDENTITY).block()).isEqualTo("# 正文内容\n详情");
    }

    @Test
    void contentReturnsEmptyForUnknownId(@TempDir Path libDir) throws Exception {
        writeDoc(libDir, "标题", "摘要", "正文");

        var catalog = catalog(libDir, List.of(globalPointer(ID, OWNER, VERSION)));

        assertThat(catalog.content("no-such-id", IDENTITY).block()).isEmpty();
    }

    @Test
    void contentDoesNotFollowApprovedArtifactSymlinks(@TempDir Path tempDir) throws Exception {
        Path libDir = Files.createDirectory(tempDir.resolve("lib"));
        Path secret = Files.writeString(tempDir.resolve("outside.md"), "not a knowledge document");
        Files.createSymbolicLink(libDir.resolve(ID + ".v" + VERSION + ".md"), secret);
        Files.writeString(libDir.resolve(ID + ".v" + VERSION + ".meta.json"), "{}",
                StandardCharsets.UTF_8);
        var catalog = catalog(libDir, List.of(globalPointer(ID, OWNER, VERSION)));

        assertThat(catalog.content(ID, IDENTITY).block()).isEmpty();
    }

    @Test
    void emptyLibDirYieldsEmptyIndexWithoutThrowing(@TempDir Path libDir) {
        var catalog = catalog(libDir, List.of());

        assertThat(catalog.compactIndex(IDENTITY).block()).isEmpty();
    }

    @Test
    void missingLibDirYieldsEmptyIndexWithoutThrowing() {
        var props = new KnowledgeProperties();
        props.setLibDir("/no/such/dir/at/all");

        var repository = mock(KnowledgeDocumentRepository.class);
        when(repository.findApprovedArtifactPointers()).thenReturn(Flux.empty());
        var catalog = new KnowledgeCatalog(props, repository, new KnowledgeAccessPolicy());

        assertThat(catalog.compactIndex(IDENTITY).block()).isEmpty();
    }

    @Test
    void constructionDoesNotQueryDatabaseBeforeMigrationIsAvailable() {
        var repository = mock(KnowledgeDocumentRepository.class);
        when(repository.findApprovedArtifactPointers())
                .thenThrow(new IllegalStateException("database schema is not ready"));

        assertThatCode(() -> new KnowledgeCatalog(propsFor(Path.of("/tmp/lib")), repository,
                new KnowledgeAccessPolicy()))
                .doesNotThrowAnyException();
        verifyNoInteractions(repository);
    }

    @Test
    void reloadPicksUpNewlyApprovedDocuments(@TempDir Path libDir) throws Exception {
        var pointers = new AtomicReference<List<ApprovedArtifactPointer>>(List.of());
        var repository = mock(KnowledgeDocumentRepository.class);
        when(repository.findApprovedArtifactPointers()).thenAnswer(call -> Flux.fromIterable(pointers.get()));
        var catalog = new KnowledgeCatalog(propsFor(libDir), repository, new KnowledgeAccessPolicy());
        assertThat(catalog.compactIndex(IDENTITY).block()).isEmpty();

        writeDoc(libDir, "新文档", "新摘要", "内容");
        pointers.set(List.of(globalPointer(ID, OWNER, VERSION)));
        catalog.reload();

        assertThat(catalog.compactIndex(IDENTITY).block()).contains(ID + " | 新文档 | 新摘要");
    }

    @Test
    void filesystemArtifactsAreInvisibleWithoutAnApprovedDatabasePointer(@TempDir Path libDir) throws Exception {
        writeDoc(libDir, "Unapproved", "not visible", "content");
        var catalog = catalog(libDir, List.of());

        assertThat(catalog.compactIndex(IDENTITY).block()).isEmpty();
        assertThat(catalog.content(ID, IDENTITY).block()).isEmpty();
    }

    @Test
    void malformedMetadataDoesNotExposeCauseOrContent(@TempDir Path libDir) throws Exception {
        Files.writeString(libDir.resolve(ID + ".v" + VERSION + ".md"), "content", StandardCharsets.UTF_8);
        Files.writeString(libDir.resolve(ID + ".v" + VERSION + ".meta.json"), "{bad json",
                StandardCharsets.UTF_8);
        var catalog = catalog(libDir, List.of(globalPointer(ID, OWNER, VERSION)));

        assertThat(catalog.compactIndex(IDENTITY).block()).isEmpty();
        assertThat(catalog.content(ID, IDENTITY).block()).isEmpty();
    }

    @Test
    void cachedApprovalCannotBeReadAfterDatabasePointerIsRemoved(@TempDir Path libDir) throws Exception {
        writeDoc(libDir, "Approved", "summary", "private content");
        var pointers = new AtomicReference<List<ApprovedArtifactPointer>>(
                List.of(globalPointer(ID, OWNER, VERSION)));
        var repository = mock(KnowledgeDocumentRepository.class);
        when(repository.findApprovedArtifactPointers()).thenAnswer(call -> Flux.fromIterable(pointers.get()));
        var catalog = new KnowledgeCatalog(propsFor(libDir), repository, new KnowledgeAccessPolicy());
        assertThat(catalog.content(ID, IDENTITY).block()).isEqualTo("private content");

        pointers.set(List.of());

        assertThat(catalog.content(ID, IDENTITY).block()).isEmpty();
        assertThat(catalog.compactIndex(IDENTITY).block()).isEmpty();
    }

    @Test
    void indexAndDirectReadsUseTheSameScopePolicy(@TempDir Path libDir) throws Exception {
        Files.createDirectories(libDir);
        var pointers = List.of(
                document("00000000-0000-0000-0000-000000000010", ScopeType.GLOBAL, null, null),
                document("00000000-0000-0000-0000-000000000011", ScopeType.TENANT, "tenant-1", null),
                document("00000000-0000-0000-0000-000000000012", ScopeType.TENANT, "tenant-2", null),
                document("00000000-0000-0000-0000-000000000013", ScopeType.ORG, "tenant-1", "org-shared"),
                document("00000000-0000-0000-0000-000000000014", ScopeType.ORG, "tenant-1", "org-secret"),
                document("00000000-0000-0000-0000-000000000015", ScopeType.ORG, "tenant-2", "org-shared"),
                document("00000000-0000-0000-0000-000000000016", ScopeType.TENANT, "tenant-1", null));
        for (var pointer : pointers) {
            Files.writeString(libDir.resolve(pointer.id() + ".v" + pointer.version() + ".md"),
                    pointer.id().endsWith("016") ? "" : "content-" + pointer.id(), StandardCharsets.UTF_8);
            writeMetadata(libDir, pointer, pointer.id(), "summary");
        }
        var catalog = catalog(libDir, pointers);
        var identity = identity("tenant-1", Set.of("org-shared"));

        String index = catalog.compactIndex(identity).block();
        for (var pointer : pointers) {
            boolean indexed = index.lines().anyMatch(line -> line.startsWith(pointer.id() + " |"));
            boolean readable = !catalog.content(pointer.id(), identity).block().isEmpty();
            assertThat(indexed).as("index/read parity for %s", pointer.id()).isEqualTo(readable);
        }
        assertThat(index).contains(pointers.get(0).id() + " |", pointers.get(1).id() + " |", pointers.get(3).id() + " |")
                .doesNotContain(pointers.get(2).id() + " |", pointers.get(4).id() + " |", pointers.get(5).id() + " |",
                        pointers.get(6).id() + " |");
        assertThat(catalog.content(pointers.get(4).id(), identity).block()).isEmpty();
    }

    @Test
    void refreshedIdentityChangesIndexAndDirectReadWithoutReload(@TempDir Path libDir) throws Exception {
        Files.createDirectories(libDir);
        var pointer = document("00000000-0000-0000-0000-000000000020", ScopeType.TENANT, "tenant-1", null);
        Files.writeString(libDir.resolve(pointer.id() + ".v" + pointer.version() + ".md"), "tenant content");
        writeMetadata(libDir, pointer, "Tenant", "summary");
        var catalog = catalog(libDir, List.of(pointer));

        assertThat(catalog.compactIndex(identity("tenant-1", Set.of())).block()).contains(pointer.id() + " |");
        assertThat(catalog.compactIndex(identity("tenant-2", Set.of())).block()).doesNotContain(pointer.id() + " |");
        assertThat(catalog.content(pointer.id(), identity("tenant-2", Set.of())).block()).isEmpty();
    }

    @Test
    void directReadHidesApprovedArtifactThatCannotAppearInIndex(@TempDir Path libDir) throws Exception {
        Files.createDirectories(libDir);
        var pointer = document(ID, ScopeType.GLOBAL, null, null);
        Files.writeString(libDir.resolve(pointer.id() + ".v" + pointer.version() + ".md"), "content");
        var catalog = catalog(libDir, List.of(pointer));

        assertThat(catalog.compactIndex(IDENTITY).block()).isEmpty();
        assertThat(catalog.content(pointer.id(), IDENTITY).block()).isEmpty();
    }

    @Test
    void malformedMetadataFailsClosedForIndexAndDirectReads(@TempDir Path libDir) throws Exception {
        Files.createDirectories(libDir);
        String validMetadata = """
                {"title":"Title","summary":"Summary","approvedBy":"reviewer-1",
                 "approvedAt":"2026-07-28T10:00:00","scopeType":"GLOBAL",
                 "tenantId":null,"tenantName":null,"orgId":null,"orgName":null}
                """;
        var invalidMetadata = List.of(
                "{bad json",
                "{}",
                "[]",
                validMetadata.replace("\"title\":\"Title\",", ""),
                validMetadata.replace("\"summary\":\"Summary\",", ""),
                validMetadata.replace("\"approvedBy\":\"reviewer-1\",", ""),
                validMetadata.replace("\"approvedAt\":\"2026-07-28T10:00:00\",", ""),
                validMetadata.replace("\"scopeType\":\"GLOBAL\",", ""),
                validMetadata.replace("\"tenantId\":null,", ""),
                validMetadata.replace("\"tenantName\":null,", ""),
                validMetadata.replace("\"orgId\":null,", ""),
                validMetadata.replace(",\"orgName\":null", ""));
        var pointers = new java.util.ArrayList<ApprovedArtifactPointer>();
        for (int i = 0; i < invalidMetadata.size(); i++) {
            String id = "00000000-0000-0000-0000-%012d".formatted(100 + i);
            var pointer = globalPointer(id, OWNER, VERSION);
            pointers.add(pointer);
            Files.writeString(libDir.resolve(id + ".v" + VERSION + ".md"), "content-" + i);
            Files.writeString(libDir.resolve(id + ".v" + VERSION + ".meta.json"), invalidMetadata.get(i));
        }
        String validId = "00000000-0000-0000-0000-000000000199";
        var validPointer = globalPointer(validId, OWNER, VERSION);
        pointers.add(validPointer);
        Files.writeString(libDir.resolve(validId + ".v" + VERSION + ".md"), "valid content");
        Files.writeString(libDir.resolve(validId + ".v" + VERSION + ".meta.json"), validMetadata);
        var catalog = catalog(libDir, pointers);

        String index = catalog.compactIndex(IDENTITY).block();

        assertThat(index).contains(validId + " |");
        for (var pointer : pointers.subList(0, invalidMetadata.size())) {
            assertThat(index).doesNotContain(pointer.id() + " |");
            assertThat(catalog.content(pointer.id(), IDENTITY).block()).isEmpty();
        }
        assertThat(catalog.content(validId, IDENTITY).block()).isEqualTo("valid content");
    }

    private static ApprovedArtifactPointer document(
            String id, ScopeType scopeType, String tenantId, String orgId) {
        return new ApprovedArtifactPointer(id, OWNER, VERSION, scopeType, tenantId, orgId);
    }

    private static ApprovedArtifactPointer globalPointer(String id, String owner, long version) {
        return new ApprovedArtifactPointer(id, owner, version, ScopeType.GLOBAL, null, null);
    }

    private static IdentityContext identity(String tenantId, Set<String> readableOrgIds) {
        return new IdentityContext("user", "User", tenantId, "Tenant", null, null,
                Set.of("member"), Set.of(), readableOrgIds, false, false);
    }
}
