package com.jowo.agent.service;

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
import com.jowo.agent.config.UploadProperties;
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

    @Test
    void savesFileToSessionDir() {
        var file = mockFilePart("photo.png", "image/png", pngBytes());
        var ref = service.save("conv-123", file).block();
        assertEquals("photo.png", ref.name());
        assertTrue(ref.path().endsWith("/conv-123/photo.png"));
        assertTrue(Files.exists(Path.of(ref.path())));
    }

    @Test
    void renamesOnCollision() {
        var f1 = mockFilePart("photo.png", "image/png", pngBytes());
        var f2 = mockFilePart("photo.png", "image/png", pngBytes());
        var ref1 = service.save("conv-x", f1).block();
        var ref2 = service.save("conv-x", f2).block();
        assertNotEquals(ref1.path(), ref2.path());
        assertTrue(ref2.path().endsWith("/photo-1.png"), "got: " + ref2.path());
    }

    @Test
    void rejectsPathTraversal() {
        var bad = mockFilePart("../etc/passwd", "image/png", pngBytes());
        var ref = service.save("conv-x", bad).block();
        assertFalse(ref.path().contains(".."));
        assertTrue(ref.path().endsWith("/.._etc_passwd")
                 || ref.path().endsWith("/_etc_passwd")
                 || ref.path().endsWith("/__etc_passwd"),
                 "got: " + ref.path());
    }

    @Test
    void rejectsMimeMismatch() {
        // 把 plain text 假装成 PNG 上传
        var fake = mockFilePart("fake.png", "image/png", "hello".getBytes());
        var ex = assertThrows(FileStorageService.RejectedException.class, () -> service.save("conv-x", fake).block());
        assertEquals("MIME_MISMATCH", ex.getCode());
    }

    @Test
    void rejectsTooLarge() {
        var big = new byte[11 * 1024 * 1024];
        var f = mockFilePart("big.pdf", "application/pdf", big);
        var ex = assertThrows(FileStorageService.RejectedException.class, () -> service.save("conv-x", f).block());
        assertEquals("FILE_REJECTED", ex.getCode());
    }

    @Test
    void rejectsForbiddenType() {
        var doc = mockFilePart("x.docx", "application/vnd.openxmlformats-officedocument.wordprocessingml.document", pngBytes());
        var ex = assertThrows(FileStorageService.RejectedException.class, () -> service.save("conv-x", doc).block());
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
