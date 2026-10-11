package io.github.sukimiya.agileagent.knowledge;

import static org.mockito.Mockito.when;
import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Comparator;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.web.reactive.function.BodyInserters;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.sukimiya.agileagent.config.KnowledgeProperties;
import io.github.sukimiya.agileagent.service.AuthSessionStore;
import io.github.sukimiya.agileagent.TestAuthUsers;
import io.github.sukimiya.agileagent.service.LlmClient;
import io.github.sukimiya.agileagent.service.TenantLlmClientResolver;
import io.github.sukimiya.agileagent.service.TenantLlmConfigRequiredException;

import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import reactor.core.publisher.Mono;

@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
@TestPropertySource(properties = {
    "app.knowledge.temp-dir=${java.io.tmpdir}/knowledge-test/pending",
    "app.knowledge.lib-dir=${java.io.tmpdir}/knowledge-test/lib"
})
class KnowledgeControllerIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>(DockerImageName.parse("postgres:16-alpine"));

    @Autowired WebTestClient client;
    @Autowired KnowledgeProperties props;
    @Autowired AuthSessionStore sessions;
    @MockBean LlmClient llm;
    @MockBean TenantLlmClientResolver resolver;
    String token;
    String reviewerToken;
    String otherTenantToken;
    String otherOrgToken;
    String restrictedAdminToken;
    String platformToken;
    String missingScopeToken;

    @BeforeEach
    void setUp() {
        when(llm.summarize(org.mockito.ArgumentMatchers.anyString())).thenReturn(Mono.just("自动生成的摘要"));
        when(resolver.resolve(org.mockito.ArgumentMatchers.anyString())).thenReturn(Mono.just(llm));
        token = sessions.create("sl-tok", "refresh-tok", Instant.now().plusSeconds(3600),
                new io.github.sukimiya.agileagent.spi.identity.IdentityContext("knowledge-test-user", "Knowledge Test",
                        "tenant-1", "Tenant", "org-1", "Support", java.util.Set.of(),
                        java.util.Set.of(), java.util.Set.of("org-1"), false, false), Instant.now()).block();
        reviewerToken = sessions.create("reviewer-sl-tok", "reviewer-refresh-tok", Instant.now().plusSeconds(3600),
                new io.github.sukimiya.agileagent.spi.identity.IdentityContext("knowledge-reviewer", "Knowledge Reviewer",
                        "tenant-1", "Tenant", null, null, java.util.Set.of("tenant_admin"),
                        java.util.Set.of("org-1"), java.util.Set.of(), false, true), Instant.now()).block();
        otherTenantToken = sessions.create("other-tenant-sl-tok", "other-tenant-refresh-tok", Instant.now().plusSeconds(3600),
                new io.github.sukimiya.agileagent.spi.identity.IdentityContext("other-tenant-user", "Other Tenant User",
                        "tenant-2", "Other Tenant", "org-1", "Support", java.util.Set.of(),
                        java.util.Set.of(), java.util.Set.of(), false, false), Instant.now()).block();
        otherOrgToken = sessions.create("other-org-sl-tok", "other-org-refresh-tok", Instant.now().plusSeconds(3600),
                new io.github.sukimiya.agileagent.spi.identity.IdentityContext("other-org-user", "Other Org User",
                        "tenant-1", "Tenant", "org-2", "Sales", java.util.Set.of(),
                        java.util.Set.of(), java.util.Set.of(), false, false), Instant.now()).block();
        restrictedAdminToken = sessions.create("restricted-admin-sl-tok", "restricted-admin-refresh-tok",
                Instant.now().plusSeconds(3600),
                new io.github.sukimiya.agileagent.spi.identity.IdentityContext("restricted-admin", "Restricted Admin",
                        "tenant-1", "Tenant", null, null, java.util.Set.of("tenant_admin"),
                        java.util.Set.of(), java.util.Set.of(), false, true), Instant.now()).block();
        platformToken = sessions.create("platform-sl-tok", "platform-refresh-tok", Instant.now().plusSeconds(3600),
                new io.github.sukimiya.agileagent.spi.identity.IdentityContext("platform-admin", "Platform Admin",
                        null, null, null, null, java.util.Set.of("platform_admin"),
                        java.util.Set.of(), java.util.Set.of(), true, false), Instant.now()).block();
        missingScopeToken = sessions.create("missing-scope-sl-tok", "missing-scope-refresh-tok",
                Instant.now().plusSeconds(3600),
                new io.github.sukimiya.agileagent.spi.identity.IdentityContext("missing-scope-user", "Missing Scope User",
                        "tenant-1", "Tenant", null, null, java.util.Set.of(), java.util.Set.of(),
                        java.util.Set.of(), false, false),
                Instant.now()).block();
        deleteRecursively(Path.of(props.getTempDir()));
        deleteRecursively(Path.of(props.getLibDir()));
    }

    @AfterEach
    void tearDown() {
        sessions.delete(token).block();
        sessions.delete(reviewerToken).block();
        sessions.delete(otherTenantToken).block();
        sessions.delete(otherOrgToken).block();
        sessions.delete(restrictedAdminToken).block();
        sessions.delete(platformToken).block();
        sessions.delete(missingScopeToken).block();
        deleteRecursively(Path.of(props.getTempDir()));
        deleteRecursively(Path.of(props.getLibDir()));
    }

    private static void deleteRecursively(Path dir) {
        if (!Files.exists(dir)) return;
        try (var walk = Files.walk(dir)) {
            walk.sorted(Comparator.reverseOrder()).forEach(p -> {
                try { Files.deleteIfExists(p); } catch (Exception ignored) {}
            });
        } catch (Exception ignored) {}
    }

    private static ByteArrayResource textPart(String filename, String content) {
        return new ByteArrayResource(content.getBytes(StandardCharsets.UTF_8)) {
            @Override public String getFilename() { return filename; }
        };
    }

    private static String extractId(byte[] body) {
        try {
            return new ObjectMapper().readTree(body).path("id").asText();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    void fullLifecycle_uploadListApproveDownload() {
        var builder = new MultipartBodyBuilder();
        builder.part("file", textPart("退款流程.md", "# 退款流程\n\n用户申请退款后...")).contentType(MediaType.TEXT_MARKDOWN);

        var uploadBody = client.post().uri("/api/knowledge/upload")
                .header("Authorization", "Bearer " + token)
                .body(BodyInserters.fromMultipartData(builder.build()))
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.status").isEqualTo("PENDING")
                .jsonPath("$.summary").isEqualTo("自动生成的摘要")
                .returnResult().getResponseBody();
        String id = extractId(uploadBody);

        client.get().uri("/api/knowledge?status=PENDING")
                .header("Authorization", "Bearer " + token)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$[?(@.id=='" + id + "')]").exists()
                .jsonPath("$[?(@.id=='" + id + "')].scopeType").isEqualTo("ORG")
                .jsonPath("$[?(@.id=='" + id + "')].scopeName").isEqualTo("Support")
                .jsonPath("$[?(@.id=='" + id + "')].tenantId").isEqualTo("tenant-1")
                .jsonPath("$[?(@.id=='" + id + "')].orgId").isEqualTo("org-1");

        client.post().uri("/api/knowledge/" + id + "/approve")
                .header("Authorization", "Bearer " + reviewerToken)
                .exchange()
                .expectStatus().isOk();

        client.get().uri("/api/knowledge?status=PENDING")
                .header("Authorization", "Bearer " + token)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$[?(@.id=='" + id + "')]").doesNotExist();

        client.get().uri("/api/knowledge/" + id + "/download")
                .header("Authorization", "Bearer " + token)
                .exchange()
                .expectStatus().isOk()
                .expectBody(String.class)
                .isEqualTo("# 退款流程\n\n用户申请退款后...");

        var libFile = Path.of(props.getLibDir(), id + ".v1.md");
        var metaFile = Path.of(props.getLibDir(), id + ".v1.meta.json");
        org.assertj.core.api.Assertions.assertThat(Files.exists(libFile)).isTrue();
        org.assertj.core.api.Assertions.assertThat(Files.exists(metaFile)).isTrue();
    }

    @Test
    void ordinaryUserCanOnlyListAndDownloadOwnDocumentsWithinTenant() {
        String ownId = uploadWithUntrustedScope(token, "own.md");
        String otherOrgId = upload(otherOrgToken, "other-org.md");
        String otherTenantId = upload(otherTenantToken, "other-tenant.md");

        client.get().uri("/api/knowledge?status=PENDING")
                .header("Authorization", "Bearer " + token)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$[?(@.id=='" + ownId + "')]").exists()
                .jsonPath("$[?(@.id=='" + otherOrgId + "')]").doesNotExist()
                .jsonPath("$[?(@.id=='" + otherTenantId + "')]").doesNotExist();

        client.get().uri("/api/knowledge/" + otherOrgId + "/download")
                .header("Authorization", "Bearer " + token)
                .exchange().expectStatus().isNotFound()
                .expectBody().json("{\"code\":\"KNOWLEDGE_DOCUMENT_NOT_FOUND\",\"message\":\"Knowledge document not found\"}");
        client.get().uri("/api/knowledge/" + otherTenantId + "/download")
                .header("Authorization", "Bearer " + token)
                .exchange().expectStatus().isNotFound()
                .expectBody().json("{\"code\":\"KNOWLEDGE_DOCUMENT_NOT_FOUND\",\"message\":\"Knowledge document not found\"}");
    }

    @Test
    void ordinaryUserCannotApproveOrRejectAnExistingDocument() {
        String id = upload(otherOrgToken, "managed-by-someone-else.md");

        client.post().uri("/api/knowledge/" + id + "/approve")
                .header("Authorization", "Bearer " + token)
                .exchange().expectStatus().isForbidden();
        client.post().uri("/api/knowledge/" + id + "/reject")
                .header("Authorization", "Bearer " + token)
                .exchange().expectStatus().isForbidden();
    }

    @Test
    void tenantAdminCanManageSameTenantDocumentsButNotOtherTenantDocuments() {
        String sameTenantId = upload(token, "same-tenant.md");
        String otherTenantId = upload(otherTenantToken, "other-tenant.md");

        client.get().uri("/api/knowledge?status=PENDING")
                .header("Authorization", "Bearer " + reviewerToken)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$[?(@.id=='" + sameTenantId + "')]").exists()
                .jsonPath("$[?(@.id=='" + otherTenantId + "')]").doesNotExist();

        client.get().uri("/api/knowledge/" + otherTenantId + "/download")
                .header("Authorization", "Bearer " + reviewerToken)
                .exchange().expectStatus().isNotFound();
        client.post().uri("/api/knowledge/" + otherTenantId + "/approve")
                .header("Authorization", "Bearer " + reviewerToken)
                .exchange().expectStatus().isForbidden();
        client.post().uri("/api/knowledge/" + otherTenantId + "/reject")
                .header("Authorization", "Bearer " + reviewerToken)
                .exchange().expectStatus().isForbidden();
    }

    @Test
    void tenantAdminCannotManageUnmanageableOrgButCanManageTenantScope() {
        String orgId = upload(token, "org-document.md");
        String tenantId = upload(reviewerToken, "tenant-document.md");

        client.get().uri("/api/knowledge?status=PENDING")
                .header("Authorization", "Bearer " + restrictedAdminToken)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$[?(@.id=='" + orgId + "')]").doesNotExist();
        client.get().uri("/api/knowledge?status=APPROVED")
                .header("Authorization", "Bearer " + restrictedAdminToken)
                .exchange()
                .expectStatus().isOk()
                .expectBody().jsonPath("$[?(@.id=='" + tenantId + "')]").exists();

        client.get().uri("/api/knowledge/" + orgId + "/download")
                .header("Authorization", "Bearer " + restrictedAdminToken)
                .exchange().expectStatus().isNotFound();
        client.post().uri("/api/knowledge/" + orgId + "/approve")
                .header("Authorization", "Bearer " + restrictedAdminToken)
                .exchange().expectStatus().isForbidden();
        client.post().uri("/api/knowledge/" + orgId + "/reject")
                .header("Authorization", "Bearer " + restrictedAdminToken)
                .exchange().expectStatus().isForbidden();
        client.get().uri("/api/knowledge/" + tenantId + "/download")
                .header("Authorization", "Bearer " + restrictedAdminToken)
                .exchange().expectStatus().isOk();
        client.post().uri("/api/knowledge/" + tenantId + "/approve")
                .header("Authorization", "Bearer " + restrictedAdminToken)
                .exchange().expectStatus().isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void platformAdminCanListAndDownloadAcrossAllScopesAndTenants() {
        String orgId = upload(token, "org-document.md");
        String otherTenantOrgId = upload(otherTenantToken, "other-tenant-org.md");
        String tenantId = upload(reviewerToken, "tenant-document.md");
        String globalId = upload(platformToken, "global-document.md");

        client.get().uri("/api/knowledge?status=PENDING")
                .header("Authorization", "Bearer " + platformToken)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$[?(@.id=='" + orgId + "')]").exists()
                .jsonPath("$[?(@.id=='" + otherTenantOrgId + "')]").exists();
        client.get().uri("/api/knowledge?status=APPROVED")
                .header("Authorization", "Bearer " + platformToken)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$[?(@.id=='" + tenantId + "')]").exists()
                .jsonPath("$[?(@.id=='" + tenantId + "')].scopeType").isEqualTo("TENANT")
                .jsonPath("$[?(@.id=='" + tenantId + "')].scopeName").isEqualTo("Tenant")
                .jsonPath("$[?(@.id=='" + tenantId + "')].tenantId").isEqualTo("tenant-1")
                .jsonPath("$[?(@.id=='" + globalId + "')]").exists();

        for (String id : java.util.List.of(orgId, otherTenantOrgId, tenantId, globalId)) {
            client.get().uri("/api/knowledge/" + id + "/download")
                    .header("Authorization", "Bearer " + platformToken)
                    .exchange().expectStatus().isOk();
        }
        client.post().uri("/api/knowledge/" + orgId + "/approve")
                .header("Authorization", "Bearer " + platformToken)
                .exchange().expectStatus().isOk();
        client.post().uri("/api/knowledge/" + otherTenantOrgId + "/reject")
                .header("Authorization", "Bearer " + platformToken)
                .exchange().expectStatus().isOk();
    }

    @Test
    void uploadRequiresTrustedTenantAndOrganizationForOrdinaryUser() {
        var builder = new MultipartBodyBuilder();
        builder.part("file", textPart("missing-scope.md", "content")).contentType(MediaType.TEXT_MARKDOWN);

        client.post().uri("/api/knowledge/upload")
                .header("Authorization", "Bearer " + missingScopeToken)
                .body(BodyInserters.fromMultipartData(builder.build()))
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody().json("{\"code\":\"IDENTITY_SCOPE_REQUIRED\",\"message\":\"tenant and organization identity are required for this upload\"}");
    }

    private String upload(String accessToken, String filename) {
        var builder = new MultipartBodyBuilder();
        builder.part("file", textPart(filename, "content for " + filename)).contentType(MediaType.TEXT_MARKDOWN);
        var body = client.post().uri("/api/knowledge/upload")
                .header("Authorization", "Bearer " + accessToken)
                .body(BodyInserters.fromMultipartData(builder.build()))
                .exchange()
                .expectStatus().isOk()
                .expectBody().returnResult().getResponseBody();
        return extractId(body);
    }

    private String uploadWithUntrustedScope(String accessToken, String filename) {
        var builder = new MultipartBodyBuilder();
        builder.part("file", textPart(filename, "content for " + filename)).contentType(MediaType.TEXT_MARKDOWN);
        builder.part("tenantId", "attacker-tenant");
        builder.part("orgId", "attacker-org");
        var body = client.post().uri("/api/knowledge/upload")
                .header("Authorization", "Bearer " + accessToken)
                .body(BodyInserters.fromMultipartData(builder.build()))
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.tenantId").isEqualTo("tenant-1")
                .jsonPath("$.orgId").isEqualTo("org-1")
                .jsonPath("$.scopeType").isEqualTo("ORG")
                .jsonPath("$.scopeName").isEqualTo("Support")
                .returnResult().getResponseBody();
        return extractId(body);
    }

    @Test
    void uploadResolvesTenantLlmBeforeWritingTemporaryFile() {
        when(resolver.resolve(org.mockito.ArgumentMatchers.anyString()))
                .thenReturn(Mono.error(new TenantLlmConfigRequiredException()));
        var builder = new MultipartBodyBuilder();
        builder.part("file", textPart("unconfigured.md", "content")).contentType(MediaType.TEXT_PLAIN);

        client.post().uri("/api/knowledge/upload")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .body(BodyInserters.fromMultipartData(builder.build()))
                .exchange()
                .expectStatus().isEqualTo(428)
                .expectBody().json("{\"code\":\"LLM_CONFIG_REQUIRED\",\"message\":\"Current tenant has no LLM configuration\"}");

        try (var files = Files.list(Path.of(props.getTempDir()))) {
            assertThat(files).isEmpty();
        } catch (java.nio.file.NoSuchFileException expected) {
            // No temporary directory was created, so the upload did not start.
        } catch (Exception e) {
            throw new AssertionError(e);
        }
    }

    @Test
    void rejectRemovesDocumentFromPendingListButKeepsItInRejectedHistory() {
        var builder = new MultipartBodyBuilder();
        builder.part("file", textPart("faq.md", "常见问题解答")).contentType(MediaType.TEXT_MARKDOWN);

        var uploadBody = client.post().uri("/api/knowledge/upload")
                .header("Authorization", "Bearer " + token)
                .body(BodyInserters.fromMultipartData(builder.build()))
                .exchange()
                .expectStatus().isOk()
                .expectBody().returnResult().getResponseBody();
        String id = extractId(uploadBody);

        client.post().uri("/api/knowledge/" + id + "/reject")
                .header("Authorization", "Bearer " + reviewerToken)
                .exchange()
                .expectStatus().isOk();

        client.get().uri("/api/knowledge?status=PENDING")
                .header("Authorization", "Bearer " + token)
                .exchange()
                .expectBody()
                .jsonPath("$[?(@.id=='" + id + "')]").doesNotExist();

        client.get().uri("/api/knowledge?status=REJECTED")
                .header("Authorization", "Bearer " + token)
                .exchange()
                .expectBody()
                .jsonPath("$[?(@.id=='" + id + "')]").exists();
    }

    @Test
    void approvingSameDocumentTwiceReturnsConflictOnSecondCall() {
        var builder = new MultipartBodyBuilder();
        builder.part("file", textPart("doc.md", "内容")).contentType(MediaType.TEXT_MARKDOWN);
        var uploadBody = client.post().uri("/api/knowledge/upload")
                .header("Authorization", "Bearer " + token)
                .body(BodyInserters.fromMultipartData(builder.build()))
                .exchange()
                .expectBody().returnResult().getResponseBody();
        String id = extractId(uploadBody);

        client.post().uri("/api/knowledge/" + id + "/approve")
                .header("Authorization", "Bearer " + reviewerToken).exchange().expectStatus().isOk();

        client.post().uri("/api/knowledge/" + id + "/approve")
                .header("Authorization", "Bearer " + reviewerToken).exchange()
                .expectStatus().isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void uploadRejectsNonTextContentType() {
        var builder = new MultipartBodyBuilder();
        builder.part("file", textPart("doc.bin", "binary-ish")).contentType(MediaType.APPLICATION_OCTET_STREAM);

        client.post().uri("/api/knowledge/upload")
                .header("Authorization", "Bearer " + token)
                .body(BodyInserters.fromMultipartData(builder.build()))
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody()
                .jsonPath("$.code").isEqualTo("FILE_REJECTED");
    }
}
