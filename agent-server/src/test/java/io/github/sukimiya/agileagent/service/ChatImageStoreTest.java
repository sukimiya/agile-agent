package io.github.sukimiya.agileagent.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.web.reactive.function.client.WebClient;

import io.github.sukimiya.agileagent.config.CosProperties;
import io.github.sukimiya.agileagent.config.UploadProperties;

class ChatImageStoreTest {

    private static final String CONV = "5a1b2c3d-4e5f-4a6b-8c7d-9e0f1a2b3c4d";
    private static final String ENDPOINT = "https://bucket-1.cos.ap-hongkong.myqcloud.com";

    @TempDir Path dir;
    private FileStorageService storage;

    @BeforeEach
    void setUp() {
        var upload = new UploadProperties();
        upload.setDir(dir.toString());
        storage = new FileStorageService(upload);
    }

    private ChatImageStore store(boolean enabled) {
        var props = new CosProperties();
        if (enabled) {
            props.setEndpoint(ENDPOINT);
            props.setSecretId("akid-test");
            props.setSecretKey("secret-test");
        }
        return new ChatImageStore(props, new CosClient(props, WebClient.builder()), storage);
    }

    private String saveFile(String name) throws Exception {
        var convDir = dir.resolve(CONV);
        Files.createDirectories(convDir);
        var file = convDir.resolve(name);
        Files.write(file, new byte[]{1, 2, 3});
        return name;
    }

    @Test
    void keyIsAsciiSafeDeterministicAndKeepsShortExtensions() {
        var key = ChatImageStore.key("chat-uploads", CONV, "截图 (1).PNG");

        assertThat(key).matches("chat-uploads/" + CONV + "/[0-9a-f]{32}\\.png");
        assertThat(key).isEqualTo(ChatImageStore.key("chat-uploads", CONV, "截图 (1).PNG"));
        assertThat(key).isNotEqualTo(ChatImageStore.key("chat-uploads", CONV, "other.png"));
        assertThat(ChatImageStore.key("p", CONV, "noext")).matches("p/" + CONV + "/[0-9a-f]{32}");
        assertThat(ChatImageStore.key("p", CONV, "x.toolongext")).matches("p/" + CONV + "/[0-9a-f]{32}");
    }

    @Test
    void imageUrlsIsEmptyWhenDisabled() {
        assertThat(store(false).imageUrls(CONV, java.util.List.of("a.png"))).isEmpty();
    }

    @Test
    void imageUrlsSkipsMissingInvalidAndNonImageNames() throws Exception {
        saveFile("photo.png");
        saveFile("doc.txt");
        var urls = store(true).imageUrls(CONV,
                java.util.List.of("photo.png", "doc.txt", "missing.jpg", "../evil.png", "bad/name.webp"));

        assertThat(urls).hasSize(1);
        var key = ChatImageStore.key("chat-uploads", CONV, "photo.png");
        assertThat(urls.getFirst()).isEqualTo(ENDPOINT + "/" + key);
        assertThat(urls.getFirst()).doesNotContain("?").doesNotContain("q-");
    }

    @Test
    void imageUrlsCoversAllSupportedImageExtensions() throws Exception {
        for (var name : java.util.List.of("a.jpg", "b.jpeg", "c.png", "d.gif", "e.webp")) {
            saveFile(name);
        }
        var urls = store(true).imageUrls(CONV,
                java.util.List.of("a.jpg", "b.jpeg", "c.png", "d.gif", "e.webp"));
        assertThat(urls).hasSize(5);
    }

    @Test
    void mirrorIsNoOpWhenDisabledOrNotAnImage() throws Exception {
        var file = saveFile("a.png");
        var path = dir.resolve(CONV).resolve(file);
        var disabled = store(false);
        // A disabled store never touches COS; with a WebClient pointed nowhere this would fail otherwise.
        disabled.mirror(CONV, "a.png", path, "image/png").block();
        store(true).mirror(CONV, "a.pdf", path, "application/pdf").block();
    }
}
