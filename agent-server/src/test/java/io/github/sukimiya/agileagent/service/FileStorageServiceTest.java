package io.github.sukimiya.agileagent.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.codec.multipart.FilePart;
import io.github.sukimiya.agileagent.config.UploadProperties;
import io.github.sukimiya.agileagent.memory.MemoryException;
import reactor.core.publisher.Mono;

class FileStorageServiceTest {

    @TempDir Path tempDir;
    UploadProperties props;
    FileStorageService service;

    @BeforeEach
    void setUp() {
        props = new UploadProperties();
        props.setDir(tempDir.toString());
        props.setMaxSizeMb(10);
        props.setMaxFilesPerRequest(10);
        props.setAllowedContentTypes(List.of("image/png", "application/pdf"));
        service = new FileStorageService(props);
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

    private static final String CONV_A = "0b6d1c5e-1f7a-4e2b-9c3d-4a5b6c7d8e9f";
    private static final String CONV_B = "7e8f9a0b-1c2d-4e3f-8a4b-5c6d7e8f9a0b";

    private Path stored(String conversationId, String name) {
        return tempDir.resolve(conversationId).resolve(name);
    }

    @Test
    void savesFileToSessionDirWithoutExposingThePath() {
        var file = mockFilePart("photo.png", "image/png", pngBytes());
        var ref = service.save(CONV_A, file).block();
        assertEquals("photo.png", ref.name());
        assertEquals(pngBytes().length, ref.size());
        assertTrue(Files.exists(stored(CONV_A, "photo.png")));
        assertFalse(ref.toString().contains(tempDir.toString()), "Ref must not carry a server path: " + ref);
        assertEquals(List.of("name", "size"), java.util.Arrays.stream(FileStorageService.Ref.class.getRecordComponents())
                .map(java.lang.reflect.RecordComponent::getName).toList());
    }

    @Test
    void renamesOnCollision() {
        var ref1 = service.save(CONV_A, mockFilePart("photo.png", "image/png", pngBytes())).block();
        var ref2 = service.save(CONV_A, mockFilePart("photo.png", "image/png", pngBytes())).block();
        assertEquals("photo.png", ref1.name());
        assertEquals("photo-1.png", ref2.name());
    }

    @Test
    void rejectsPathTraversal() throws Exception {
        var ref = service.save(CONV_A, mockFilePart("../etc/passwd", "image/png", pngBytes())).block();
        assertFalse(ref.name().contains("..") || ref.name().contains("/"), "got: " + ref.name());
        try (var files = Files.list(tempDir)) {
            assertEquals(List.of(tempDir.resolve(CONV_A)), files.toList(), "nothing may escape the session dir");
        }
    }

    @Test
    void slashAndUnderscoreIdsDoNotShareDirectory() {
        for (var id : List.of("a/b", "a_b", "a\\b")) {
            var ex = assertThrows(MemoryException.class,
                    () -> service.save(id, mockFilePart("x.png", "image/png", pngBytes())).block(), id);
            assertEquals(MemoryException.ErrorCode.INVALID_CONVERSATION_ID, ex.code());
        }
        assertEquals(0, tempDir.toFile().list().length, "rejected ids must not create directories");
    }

    @Test
    void dotDotIdRejected() {
        for (var id : List.of("..", "a..b", "../" + CONV_A)) {
            var ex = assertThrows(MemoryException.class,
                    () -> service.save(id, mockFilePart("x.png", "image/png", pngBytes())).block(), id);
            assertEquals(MemoryException.ErrorCode.INVALID_CONVERSATION_ID, ex.code());
        }
        assertEquals(0, tempDir.toFile().list().length);
    }

    @Test
    void uppercaseVariantRejected() {
        var ex = assertThrows(MemoryException.class,
                () -> service.save(CONV_A.toUpperCase(), mockFilePart("x.png", "image/png", pngBytes())).block());
        assertEquals(MemoryException.ErrorCode.INVALID_CONVERSATION_ID, ex.code());
        assertEquals(0, tempDir.toFile().list().length);
    }

    @Test
    void distinctConversationsUseDistinctDirectories() {
        service.save(CONV_A, mockFilePart("same.png", "image/png", pngBytes())).block();
        service.save(CONV_B, mockFilePart("same.png", "image/png", pngBytes())).block();
        assertTrue(Files.exists(stored(CONV_A, "same.png")));
        assertTrue(Files.exists(stored(CONV_B, "same.png")));
        assertEquals(stored(CONV_A, "same.png"), service.locate(CONV_A, "same.png").orElseThrow());
        assertTrue(service.locate(CONV_B, "missing.png").isEmpty());
    }

    @Test
    void locateRejectsInvalidIdsAndPathLikeNames() {
        assertThrows(MemoryException.class, () -> service.locate("a_b", "x.png"));
        for (var name : List.of("../x.png", "a/b.png", "a\\b.png", "..", "")) {
            assertThrows(FileStorageService.RejectedException.class, () -> service.locate(CONV_A, name), name);
        }
    }

    @Test
    void rejectsMimeMismatch() {
        // 把 plain text 假装成 PNG 上传
        var fake = mockFilePart("fake.png", "image/png", "hello".getBytes());
        var ex = assertThrows(FileStorageService.RejectedException.class, () -> service.save(CONV_A, fake).block());
        assertEquals("MIME_MISMATCH", ex.getCode());
    }

    @Test
    void rejectsTooLarge() {
        var big = new byte[11 * 1024 * 1024];
        var f = mockFilePart("big.pdf", "application/pdf", big);
        var ex = assertThrows(FileStorageService.RejectedException.class, () -> service.save(CONV_A, f).block());
        assertEquals("FILE_REJECTED", ex.getCode());
    }

    @Test
    void rejectsForbiddenType() {
        var doc = mockFilePart("x.docx", "application/vnd.openxmlformats-officedocument.wordprocessingml.document", pngBytes());
        var ex = assertThrows(FileStorageService.RejectedException.class, () -> service.save(CONV_A, doc).block());
        assertEquals("FILE_REJECTED", ex.getCode());
    }

    /** Minimal valid PNG (1x1 transparent). */
    static byte[] pngBytes() {
        return new byte[] {
            (byte)0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A,
            0x00, 0x00, 0x00, 0x0D, 0x49, 0x48, 0x44, 0x52,
            0x00, 0x00, 0x00, 0x01, 0x00, 0x00, 0x00, 0x01,
            0x08, 0x06, 0x00, 0x00, 0x00, 0x1F, 0x15, (byte)0xC4,
            (byte)0x89, 0x00, 0x00, 0x00, 0x0D, 0x49, 0x44, 0x41,
            0x54, 0x78, (byte)0x9C, 0x62, 0x00, 0x01, 0x00, 0x00,
            0x05, 0x00, 0x01, 0x0D, 0x0A, 0x2D, (byte)0xB4, 0x00,
            0x00, 0x00, 0x00, 0x49, 0x45, 0x4E, 0x44, (byte)0xAE,
            0x42, 0x60, (byte)0x82
        };
    }
}
