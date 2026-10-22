package io.github.sukimiya.agileagent.controller;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.MediaType;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.web.reactive.function.BodyInserters;

import io.github.sukimiya.agileagent.LocalRedisTestServer;
import io.github.sukimiya.agileagent.config.UploadProperties;
import io.github.sukimiya.agileagent.memory.MemoryScope;
import io.github.sukimiya.agileagent.memory.MemoryTestFixture;
import io.github.sukimiya.agileagent.repository.SessionRepository;
import io.github.sukimiya.agileagent.service.AuthSessionStore;
import io.github.sukimiya.agileagent.service.SessionService;
import io.github.sukimiya.agileagent.service.UploadCleanupTask;
import io.github.sukimiya.agileagent.spi.identity.IdentityContext;
import io.github.sukimiya.agileagent.util.NonBlockingIds;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
@TestPropertySource(properties = {
    "app.upload.dir=${java.io.tmpdir}/upload-test",
    "app.upload.allowed-content-types=image/png,application/pdf",
    // R2DBC and Flyway use different embedded H2 databases in this context, so memory tables are absent;
    // session ownership is checked against the migrated fixture database below.
    "app.memory.recovery-enabled=false"
})
class UploadControllerTest {

    /** One migrated database for the whole class; every test uses fresh conversation ids. */
    static final MemoryTestFixture DB = MemoryTestFixture.h2();

    @TestConfiguration
    static class OwnershipDatabase {
        @Bean
        @Primary
        SessionService ownershipSessionService() {
            return new SessionService(new SessionRepository(DB.databaseClient()));
        }
    }

    /** Uses the per-JVM loopback test Redis instead of an ambient localhost:6379 server. */
    @DynamicPropertySource
    static void redis(DynamicPropertyRegistry registry) {
        registry.add("spring.data.redis.host", () -> "127.0.0.1");
        registry.add("spring.data.redis.port", LocalRedisTestServer::port);
        registry.add("spring.data.redis.database", () -> 2);
    }

    private static final MemoryScope ALICE = new MemoryScope("tenant-1", "alice");
    private static final MemoryScope BOB = new MemoryScope("tenant-1", "bob");
    private static final MemoryScope ALICE_OTHER_TENANT = new MemoryScope("tenant-2", "alice");

    @Autowired WebTestClient client;
    @Autowired UploadProperties props;
    @Autowired AuthSessionStore sessions;
    @Autowired SessionService sessionService;
    @Autowired UploadCleanupTask cleanupTask;

    private final List<String> tokens = new ArrayList<>();
    private String originalDir;

    @BeforeEach
    void setUp() throws Exception {
        originalDir = props.getDir();
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
        props.setDir(originalDir);
        tokens.forEach(t -> sessions.delete(t).block());
    }

    private String login(MemoryScope scope) {
        return login(new IdentityContext(scope.subject(), scope.subject(), scope.tenantId(), scope.tenantId(),
                null, null, Set.of("test"), Set.of(), Set.of(), false, false));
    }

    private String login(IdentityContext identity) {
        var token = sessions.create("sl-tok", "refresh-tok", Instant.now().plusSeconds(3600), identity,
                Instant.now()).block();
        tokens.add(token);
        return token;
    }

    private String ownedConversation(MemoryScope owner) {
        var id = NonBlockingIds.uuid();
        sessionService.createOwned(id, owner, "t").block();
        return id;
    }

    private Path sessionDir(String conversationId) {
        return Path.of(props.getDir(), conversationId);
    }

    private static ByteArrayResource pngPart(String filename) {
        return new ByteArrayResource(FileStorageServiceTestData.PNG) {
            @Override public String getFilename() { return filename; }
        };
    }

    private WebTestClient.ResponseSpec upload(String token, String sessionId, String... filenames) {
        var builder = new MultipartBodyBuilder();
        if (sessionId != null) {
            builder.part("sessionId", sessionId);
        }
        for (var name : filenames) {
            builder.part("files", pngPart(name)).contentType(MediaType.IMAGE_PNG);
        }
        var spec = client.post().uri("/api/files/upload");
        if (token != null) {
            spec = spec.header("Authorization", "Bearer " + token);
        }
        return spec.body(BodyInserters.fromMultipartData(builder.build())).exchange();
    }

    private WebTestClient.ResponseSpec download(String token, String sessionId, String name) {
        return client.get().uri(b -> b.path("/api/files/download").queryParam("sessionId", sessionId)
                        .queryParam("name", name).build())
                .header("Authorization", "Bearer " + token)
                .exchange();
    }

    private Path writeAttachment(String conversationId, String name, String body) throws Exception {
        var dir = sessionDir(conversationId);
        Files.createDirectories(dir);
        return Files.writeString(dir.resolve(name), body);
    }

    private void assertNoServerPath(String body) {
        assertThat(body).doesNotContain("\"path\"");
        var dir = Path.of(originalDir);
        for (var prefix : List.of(dir.toString(), dir.toAbsolutePath().toString(), System.getProperty("java.io.tmpdir"),
                "upload-test")) {
            assertThat(body).doesNotContain(prefix);
        }
    }

    @Test
    void uploadsMultipleFiles() {
        var token = login(ALICE);
        var conversation = ownedConversation(ALICE);

        upload(token, conversation, "a.png", "b.png")
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.length()").isEqualTo(2)
                .jsonPath("$[0].name").isEqualTo("a.png")
                .jsonPath("$[0].size").isEqualTo(FileStorageServiceTestData.PNG.length)
                .jsonPath("$[0].sessionId").isEqualTo(conversation)
                .jsonPath("$[0].path").doesNotExist();
        assertThat(sessionDir(conversation).resolve("b.png")).exists();
    }

    @Test
    void uploadCreatesOwnedSessionWhenAbsent() {
        var token = login(ALICE);
        var conversation = NonBlockingIds.uuid();

        upload(token, conversation, "a.png").expectStatus().isOk();

        assertThat(sessionService.findOwned(conversation, ALICE).blockOptional()).isPresent();
        assertThat(sessionService.findOwned(conversation, BOB).blockOptional()).isEmpty();
    }

    @Test
    void uploadResponseHasNoServerPath() {
        var token = login(ALICE);

        var body = upload(token, NonBlockingIds.uuid(), "a.png").expectStatus().isOk()
                .expectBody(String.class).returnResult().getResponseBody();

        assertNoServerPath(body);
    }

    @Test
    void uploadToForeignSessionReturns404() {
        var conversation = ownedConversation(BOB);

        upload(login(ALICE), conversation, "a.png")
                .expectStatus().isNotFound()
                .expectBody().jsonPath("$.code").isEqualTo("CONVERSATION_NOT_FOUND");
        assertThat(sessionDir(conversation)).doesNotExist();
    }

    @Test
    void uploadToDeletedSessionReturns404() {
        var conversation = ownedConversation(ALICE);
        DB.jdbc().update("UPDATE sessions SET deleted_at = CURRENT_TIMESTAMP(6) WHERE id = ?", conversation);

        upload(login(ALICE), conversation, "a.png").expectStatus().isNotFound();
        assertThat(sessionDir(conversation)).doesNotExist();
    }

    @Test
    void uploadWithoutTenantReturns403() {
        var token = login(io.github.sukimiya.agileagent.TestAuthUsers.identity("no-tenant", "No Tenant", Set.of("test")));
        var conversation = NonBlockingIds.uuid();

        upload(token, conversation, "a.png")
                .expectStatus().isForbidden()
                .expectBody().jsonPath("$.code").isEqualTo("TENANT_REQUIRED");
        assertThat(sessionDir(conversation)).doesNotExist();
    }

    @Test
    void uploadWithInvalidConversationIdReturns400AndWritesNothing() {
        var token = login(ALICE);
        var valid = NonBlockingIds.uuid();
        for (var id : List.of("a/b", "a_b", "a..b", "conv-1", valid.toUpperCase(), " " + valid)) {
            upload(token, id, "a.png")
                    .expectStatus().isBadRequest()
                    .expectBody().jsonPath("$.code").isEqualTo("INVALID_CONVERSATION_ID");
        }
        assertThat(Path.of(props.getDir())).satisfiesAnyOf(
                dir -> assertThat(dir).doesNotExist(),
                dir -> assertThat(dir).isEmptyDirectory());
    }

    @Test
    void rejectsMissingSessionId() {
        upload(login(ALICE), null, "a.png").expectStatus().isBadRequest();
    }

    @Test
    void rejectsTooManyFiles() {
        var names = new String[11];
        for (int i = 0; i < names.length; i++) {
            names[i] = "a" + i + ".png";
        }
        upload(login(ALICE), NonBlockingIds.uuid(), names)
                .expectStatus().isBadRequest()
                .expectBody()
                .jsonPath("$.code").isEqualTo("FILE_REJECTED");
    }

    @Test
    void storageErrorBodyHasNoExceptionMessageOrPath() throws Exception {
        var token = login(ALICE);
        var blocker = Files.createTempFile("upload-blocker-", ".txt");
        try {
            props.setDir(blocker.resolve("nested").toString());

            var body = upload(token, NonBlockingIds.uuid(), "a.png")
                    .expectStatus().is5xxServerError()
                    .expectBody(String.class).returnResult().getResponseBody();

            assertThat(body).contains("\"code\":\"STORAGE_ERROR\"")
                    .doesNotContain(blocker.toString()).doesNotContain(blocker.getFileName().toString())
                    .doesNotContain("Exception").doesNotContain("directory");
            assertNoServerPath(body);
        } finally {
            props.setDir(originalDir);
            Files.deleteIfExists(blocker);
        }
    }

    @Test
    void downloadsExistingFile() throws Exception {
        var token = login(ALICE);
        var conversation = ownedConversation(ALICE);
        var name = "国台办.md";
        var body = "# 标题\n\n正文。\n";
        writeAttachment(conversation, name, body);

        download(token, conversation, name)
                .expectStatus().isOk()
                .expectHeader().value("Content-Disposition", h -> assertThat(h).contains("filename*=UTF-8''"))
                .expectBody().consumeWith(result ->
                        assertThat(result.getResponseBody()).isEqualTo(body.getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    void downloadForeignSessionReturns404() throws Exception {
        var conversation = ownedConversation(BOB);
        writeAttachment(conversation, "secret.md", "bob only");

        download(login(ALICE), conversation, "secret.md").expectStatus().isNotFound();
    }

    @Test
    void sameSubjectOtherTenantCannotDownload() throws Exception {
        var conversation = ownedConversation(ALICE);
        writeAttachment(conversation, "secret.md", "tenant-1 only");

        download(login(ALICE_OTHER_TENANT), conversation, "secret.md").expectStatus().isNotFound();
        download(login(ALICE), conversation, "secret.md").expectStatus().isOk();
    }

    @Test
    void downloadOfUnknownSessionReturns404EvenIfADirectoryExists() throws Exception {
        var conversation = NonBlockingIds.uuid();
        writeAttachment(conversation, "orphan.md", "no owner");

        download(login(ALICE), conversation, "orphan.md").expectStatus().isNotFound();
    }

    @Test
    void downloadWithoutTenantReturns403() {
        var token = login(io.github.sukimiya.agileagent.TestAuthUsers.identity("no-tenant", "No Tenant", Set.of("test")));

        download(token, NonBlockingIds.uuid(), "x.md").expectStatus().isForbidden();
    }

    @Test
    void downloadWithInvalidConversationIdReturns400() {
        var token = login(ALICE);
        for (var id : List.of("a_b", "..", NonBlockingIds.uuid().toUpperCase())) {
            download(token, id, "x.md")
                    .expectStatus().isBadRequest()
                    .expectBody().jsonPath("$.code").isEqualTo("INVALID_CONVERSATION_ID");
        }
    }

    @Test
    void rejectsDownloadPathTraversal() {
        download(login(ALICE), ownedConversation(ALICE), "../../etc/passwd").expectStatus().isBadRequest();
    }

    @Test
    void downloadMissingFileReturns404() {
        download(login(ALICE), ownedConversation(ALICE), "ghost.md").expectStatus().isNotFound();
    }

    @Test
    void twoUsersNeverShareAttachmentDirectory() throws Exception {
        var alice = login(ALICE);
        var bob = login(BOB);
        var aliceConversation = NonBlockingIds.uuid();
        var bobConversation = NonBlockingIds.uuid();

        upload(alice, aliceConversation, "a.png").expectStatus().isOk();
        upload(bob, bobConversation, "a.png").expectStatus().isOk();

        assertThat(sessionDir(aliceConversation)).isNotEqualTo(sessionDir(bobConversation));
        download(bob, aliceConversation, "a.png").expectStatus().isNotFound();
        upload(bob, aliceConversation, "b.png").expectStatus().isNotFound();
        assertThat(sessionDir(aliceConversation).resolve("b.png")).doesNotExist();

        // Bob's attachments expire; the cleanup removes only his directory.
        Files.setLastModifiedTime(sessionDir(bobConversation), FileTime.from(Instant.now().minusSeconds(48 * 3600)));
        cleanupTask.cleanup();

        assertThat(sessionDir(bobConversation)).doesNotExist();
        assertThat(sessionDir(aliceConversation).resolve("a.png")).exists();
        download(alice, aliceConversation, "a.png").expectStatus().isOk();
    }

    @Test
    void uploadRequiresAuthentication() {
        upload(null, NonBlockingIds.uuid(), "a.png")
                .expectStatus().isUnauthorized()
                .expectBody().json("{\"code\":\"UNAUTHORIZED\",\"message\":\"Authentication required\"}");
    }
}
