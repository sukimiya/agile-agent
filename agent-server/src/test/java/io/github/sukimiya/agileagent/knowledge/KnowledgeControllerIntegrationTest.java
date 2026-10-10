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

import io.r2dbc.spi.ConnectionFactories;
import io.r2dbc.spi.ConnectionFactory;
import io.r2dbc.spi.ConnectionFactoryOptions;
import io.r2dbc.spi.Option;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import reactor.core.publisher.Mono;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
@Import(KnowledgeControllerIntegrationTest.TestDbConfig.class)
@TestPropertySource(properties = {
    "app.knowledge.temp-dir=${java.io.tmpdir}/knowledge-test/pending",
    "app.knowledge.lib-dir=${java.io.tmpdir}/knowledge-test/lib",
    "spring.flyway.url=jdbc:h2:mem:knowledge-it-test;MODE=MySQL;DB_CLOSE_DELAY=-1",
    "spring.flyway.user=sa",
    "spring.flyway.password="
})
class KnowledgeControllerIntegrationTest {

    @Autowired WebTestClient client;
    @Autowired KnowledgeProperties props;
    @Autowired AuthSessionStore sessions;
    @MockBean LlmClient llm;
    @MockBean TenantLlmClientResolver resolver;
    String token;
    String reviewerToken;

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
        deleteRecursively(Path.of(props.getTempDir()));
        deleteRecursively(Path.of(props.getLibDir()));
    }

    @AfterEach
    void tearDown() {
        sessions.delete(token).block();
        sessions.delete(reviewerToken).block();
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
                .jsonPath("$[?(@.id=='" + id + "')]").exists();

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

    @TestConfiguration
    static class TestDbConfig {
        @Bean
        ConnectionFactory connectionFactory() {
            return ConnectionFactories.get(ConnectionFactoryOptions.builder()
                    .option(ConnectionFactoryOptions.DRIVER, "h2")
                    .option(ConnectionFactoryOptions.PROTOCOL, "mem")
                    .option(ConnectionFactoryOptions.DATABASE, "knowledge-it-test")
                    .option(ConnectionFactoryOptions.USER, "sa")
                    .option(ConnectionFactoryOptions.PASSWORD, "")
                    .option(Option.valueOf("options"), "MODE=MySQL;DB_CLOSE_DELAY=-1")
                    .build());
        }
    }
}
