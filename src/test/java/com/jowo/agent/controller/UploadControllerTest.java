package com.jowo.agent.controller;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.MediaType;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.web.reactive.function.BodyInserters;

import com.jowo.agent.config.UploadProperties;
import com.jowo.agent.service.AuthSessionStore;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
@TestPropertySource(properties = {
    "app.upload.dir=${java.io.tmpdir}/upload-test",
    "app.upload.allowed-content-types=image/png,application/pdf"
})
class UploadControllerTest {

    @Autowired WebTestClient client;
    @Autowired UploadProperties props;
    @Autowired AuthSessionStore sessions;
    String token;

    @BeforeEach
    void setUp() throws Exception {
        token = sessions.create("upload-test-user", "Upload Test", "sl-tok").block();
        var dir = Path.of(props.getDir());
        if (Files.exists(dir)) {
            try (var walk = Files.walk(dir)) {
                walk.sorted(Comparator.reverseOrder()).forEach(p -> {
                    try { Files.deleteIfExists(p); } catch (Exception ignored) {}
                });
            }
        }
    }

    @AfterEach
    void tearDown() {
        sessions.delete(token).block();
    }

    private static ByteArrayResource pngPart(String filename) {
        return new ByteArrayResource(FileStorageServiceTestData.PNG) {
            @Override public String getFilename() { return filename; }
        };
    }

    @Test
    void uploadsMultipleFiles() {
        var builder = new MultipartBodyBuilder();
        builder.part("sessionId", "conv-it");
        builder.part("files", pngPart("a.png")).contentType(MediaType.IMAGE_PNG);
        builder.part("files", pngPart("b.png")).contentType(MediaType.IMAGE_PNG);

        client.post().uri("/api/files/upload")
                .header("Authorization", "Bearer " + token)
                .body(BodyInserters.fromMultipartData(builder.build()))
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.length()").isEqualTo(2)
                .jsonPath("$[0].name").isEqualTo("a.png")
                .jsonPath("$[0].path").exists();
    }

    @Test
    void rejectsMissingSessionId() {
        var builder = new MultipartBodyBuilder();
        builder.part("files", pngPart("a.png")).contentType(MediaType.IMAGE_PNG);

        client.post().uri("/api/files/upload")
                .header("Authorization", "Bearer " + token)
                .body(BodyInserters.fromMultipartData(builder.build()))
                .exchange()
                .expectStatus().isBadRequest();
    }

    @Test
    void rejectsTooManyFiles() {
        var builder = new MultipartBodyBuilder();
        builder.part("sessionId", "conv-many");
        for (int i = 0; i < 11; i++) {
            builder.part("files", pngPart("a" + i + ".png")).contentType(MediaType.IMAGE_PNG);
        }

        client.post().uri("/api/files/upload")
                .header("Authorization", "Bearer " + token)
                .body(BodyInserters.fromMultipartData(builder.build()))
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody()
                .jsonPath("$.code").isEqualTo("FILE_REJECTED");
    }

    @Test
    void downloadsExistingFile() throws Exception {
        var dir = Path.of(props.getDir(), "conv-dl");
        Files.createDirectories(dir);
        var name = "国台办.md";
        var body = "# 标题\n\n正文。\n";
        Files.writeString(dir.resolve(name), body);

        client.get().uri(uriBuilder -> uriBuilder.path("/api/files/download")
                        .queryParam("sessionId", "conv-dl")
                        .queryParam("name", name)
                        .build())
                .header("Authorization", "Bearer " + token)
                .exchange()
                .expectStatus().isOk()
                .expectHeader().value("Content-Disposition", h -> org.assertj.core.api.Assertions.assertThat(h)
                        .contains("filename*=UTF-8''"))
                .expectBody().consumeWith(result ->
                        org.assertj.core.api.Assertions.assertThat(result.getResponseBody())
                                .isEqualTo(body.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
    }

    @Test
    void rejectsDownloadPathTraversal() {
        client.get().uri(uriBuilder -> uriBuilder.path("/api/files/download")
                        .queryParam("sessionId", "conv-dl")
                        .queryParam("name", "../../etc/passwd")
                        .build())
                .header("Authorization", "Bearer " + token)
                .exchange()
                .expectStatus().isBadRequest();
    }

    @Test
    void downloadMissingFileReturns404() {
        client.get().uri(uriBuilder -> uriBuilder.path("/api/files/download")
                        .queryParam("sessionId", "conv-dl")
                        .queryParam("name", "ghost.md")
                        .build())
                .header("Authorization", "Bearer " + token)
                .exchange()
                .expectStatus().isNotFound();
    }

    @Test
    void uploadRequiresAuthentication() {
        var builder = new MultipartBodyBuilder();
        builder.part("sessionId", "conv-it");
        builder.part("files", pngPart("a.png")).contentType(MediaType.IMAGE_PNG);

        client.post().uri("/api/files/upload")
                .body(BodyInserters.fromMultipartData(builder.build()))
                .exchange()
                .expectStatus().isUnauthorized();
    }
}
