package io.github.sukimiya.agileagent.knowledge;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static io.github.sukimiya.agileagent.TestAuthUsers.user;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDateTime;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.WebFluxTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.web.reactive.function.BodyInserters;

import io.github.sukimiya.agileagent.config.KnowledgeProperties;
import io.github.sukimiya.agileagent.knowledge.KnowledgeDocumentRepository.KnowledgeDocumentRow;
import io.github.sukimiya.agileagent.knowledge.KnowledgeDocumentRepository.KnowledgeDocumentRow.Status;
import io.github.sukimiya.agileagent.service.AuthSessionStore;
import io.github.sukimiya.agileagent.service.OAuth2Client;
import io.github.sukimiya.agileagent.spi.identity.IdentityContext;
import io.github.sukimiya.agileagent.web.AuthExceptionHandler;
import io.github.sukimiya.agileagent.web.CurrentUserArgumentResolver;
import io.github.sukimiya.agileagent.web.WebConfig;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@WebFluxTest(KnowledgeController.class)
@Import({WebConfig.class, AuthExceptionHandler.class, CurrentUserArgumentResolver.class})
class KnowledgeControllerTest {

    @Autowired WebTestClient client;
    @MockBean KnowledgeDocumentService service;
    @MockBean KnowledgeDocumentRepository repository;
    @MockBean KnowledgeProperties props;
    @MockBean AuthSessionStore sessions;
    @MockBean OAuth2Client oauth2;
    @MockBean org.springframework.data.redis.core.ReactiveStringRedisTemplate redis;

    private void asUser1() {
        when(sessions.resolve("test-token")).thenReturn(Mono.just(user("user-1", "Test User", "sl-tok",
                "refresh-tok", Instant.now().plusSeconds(3600), identity1(), Instant.now())));
    }

    private static IdentityContext identity1() {
        return new IdentityContext("user-1", "Test User", "tenant-1", "Tenant", null, null,
                java.util.Set.of("tenant_admin"), java.util.Set.of(), java.util.Set.of(), false, true);
    }

    private static ByteArrayResource textPart(String filename, String content) {
        return new ByteArrayResource(content.getBytes(StandardCharsets.UTF_8)) {
            @Override public String getFilename() { return filename; }
        };
    }

    @Test
    void uploadRequiresAuthentication() {
        var builder = new MultipartBodyBuilder();
        builder.part("file", textPart("doc.md", "内容")).contentType(MediaType.TEXT_MARKDOWN);

        client.post().uri("/api/knowledge/upload")
                .body(BodyInserters.fromMultipartData(builder.build()))
                .exchange()
                .expectStatus().isUnauthorized()
                .expectBody().json("{\"code\":\"UNAUTHORIZED\",\"message\":\"Authentication required\"}");
    }

    @Test
    void uploadReturnsCreatedSummary() {
        asUser1();
        var now = LocalDateTime.now();
        var row = new KnowledgeDocumentRow("k1", "标题", "doc.md", "/tmp/k1.md",
                Status.PENDING, "摘要", "user-1", now, null, null);
        when(service.upload(eq(identity1()), any(), any())).thenReturn(Mono.just(row));
        var builder = new MultipartBodyBuilder();
        builder.part("file", textPart("doc.md", "内容")).contentType(MediaType.TEXT_MARKDOWN);

        client.post().uri("/api/knowledge/upload")
                .header("Authorization", "Bearer test-token")
                .body(BodyInserters.fromMultipartData(builder.build()))
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.id").isEqualTo("k1")
                .jsonPath("$.title").isEqualTo("标题")
                .jsonPath("$.summary").isEqualTo("摘要")
                .jsonPath("$.downloadUrl").isEqualTo("/api/knowledge/k1/download");
    }

    @Test
    void uploadReturns400WhenFileRejected() {
        asUser1();
        when(service.upload(eq(identity1()), any(), any())).thenReturn(
                Mono.error(new KnowledgeFileStorage.RejectedException("FILE_REJECTED", "content-type application/pdf not allowed")));
        var builder = new MultipartBodyBuilder();
        builder.part("file", textPart("doc.pdf", "内容")).contentType(MediaType.APPLICATION_PDF);

        client.post().uri("/api/knowledge/upload")
                .header("Authorization", "Bearer test-token")
                .body(BodyInserters.fromMultipartData(builder.build()))
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody()
                .jsonPath("$.code").isEqualTo("FILE_REJECTED");
    }

    @Test
    void uploadReturns400WhenTrustedIdentityCannotProduceAScope() {
        asUser1();
        when(service.upload(eq(identity1()), any(), any()))
                .thenReturn(Mono.error(new KnowledgeAccessPolicy.UploadIdentityException()));
        var builder = new MultipartBodyBuilder();
        builder.part("file", textPart("doc.md", "内容")).contentType(MediaType.TEXT_MARKDOWN);

        client.post().uri("/api/knowledge/upload")
                .header("Authorization", "Bearer test-token")
                .body(BodyInserters.fromMultipartData(builder.build()))
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody()
                .jsonPath("$.code").isEqualTo("IDENTITY_SCOPE_REQUIRED");
    }

    @Test
    void listDefaultsToPendingStatus() {
        asUser1();
        var now = LocalDateTime.now();
        when(service.list(eq(Status.PENDING), eq(identity1()))).thenReturn(Flux.just(
                new KnowledgeDocumentRow("k1", "标题", "doc.md", "/tmp/k1.md",
                        Status.PENDING, "摘要", "user-1", now, null, null)));

        client.get().uri("/api/knowledge").header("Authorization", "Bearer test-token")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.length()").isEqualTo(1)
                .jsonPath("$[0].id").isEqualTo("k1")
                .jsonPath("$[0].status").isEqualTo("PENDING");
    }

    @Test
    void listWithStatusQueryParamFiltersApproved() {
        asUser1();
        when(service.list(eq(Status.APPROVED), eq(identity1()))).thenReturn(Flux.empty());

        client.get().uri("/api/knowledge?status=APPROVED").header("Authorization", "Bearer test-token")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.length()").isEqualTo(0);

        verify(service).list(Status.APPROVED, identity1());
    }

    @Test
    void downloadReturns404WhenFileMissing() {
        asUser1();
        var now = LocalDateTime.now();
        when(service.get("k1", identity1())).thenReturn(Mono.just(new KnowledgeDocumentRow("k1", "标题", "doc.md",
                "/no/such/path/k1.md", Status.PENDING, "摘要", "user-1", now, null, null)));

        client.get().uri("/api/knowledge/k1/download").header("Authorization", "Bearer test-token")
                .exchange()
                .expectStatus().isNotFound();
    }

    @Test
    void downloadReturns404WhenIdUnknown() {
        asUser1();
        when(service.get("missing", identity1())).thenReturn(Mono.empty());

        client.get().uri("/api/knowledge/missing/download").header("Authorization", "Bearer test-token")
                .exchange()
                .expectStatus().isNotFound();
    }

    @Test
    void approveReturns404ForUnknownId() {
        asUser1();
        when(service.approve(eq("missing"), eq(identity1()), any())).thenReturn(
                Mono.just(KnowledgeDocumentService.ActionResult.NOT_FOUND));

        client.post().uri("/api/knowledge/missing/approve").header("Authorization", "Bearer test-token")
                .exchange()
                .expectStatus().isNotFound();
    }

    @Test
    void approveReturns409ForAlreadyProcessedDocument() {
        asUser1();
        when(service.approve(eq("k1"), eq(identity1()), any())).thenReturn(
                Mono.just(KnowledgeDocumentService.ActionResult.CONFLICT));

        client.post().uri("/api/knowledge/k1/approve").header("Authorization", "Bearer test-token")
                .exchange()
                .expectStatus().isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void approveReturns403WhenServiceDeniesManagementScope() {
        asUser1();
        when(service.approve(eq("k1"), eq(identity1()), any()))
                .thenReturn(Mono.just(KnowledgeDocumentService.ActionResult.FORBIDDEN));

        client.post().uri("/api/knowledge/k1/approve").header("Authorization", "Bearer test-token")
                .exchange()
                .expectStatus().isForbidden();
    }

    @Test
    void approveWithBodyPassesSummaryOverride() {
        asUser1();
        when(service.approve("k1", identity1(), "改写后的摘要")).thenReturn(
                Mono.just(KnowledgeDocumentService.ActionResult.OK));

        client.post().uri("/api/knowledge/k1/approve")
                .header("Authorization", "Bearer test-token")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"summary\":\"改写后的摘要\"}")
                .exchange()
                .expectStatus().isOk();

        verify(service).approve("k1", identity1(), "改写后的摘要");
    }

    @Test
    void rejectReturnsOkForPendingDocument() {
        asUser1();
        when(service.reject("k1", identity1())).thenReturn(Mono.just(KnowledgeDocumentService.ActionResult.OK));

        client.post().uri("/api/knowledge/k1/reject").header("Authorization", "Bearer test-token")
                .exchange()
                .expectStatus().isOk();
    }
}
