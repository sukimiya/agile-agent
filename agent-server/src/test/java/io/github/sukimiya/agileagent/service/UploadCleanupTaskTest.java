package io.github.sukimiya.agileagent.service;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.*;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import io.github.sukimiya.agileagent.config.UploadProperties;

class UploadCleanupTaskTest {

    @TempDir Path tempDir;

    @Test
    void deletesDirectoriesOlderThanTtl() throws Exception {
        var oldDir = tempDir.resolve("conv-old");
        var newDir = tempDir.resolve("conv-new");
        Files.createDirectories(oldDir);
        Files.createDirectories(newDir);
        Files.writeString(oldDir.resolve("x.txt"), "x");
        Files.writeString(newDir.resolve("y.txt"), "y");
        Files.setLastModifiedTime(oldDir, FileTime.from(Instant.now().minusSeconds(48 * 3600)));

        var props = new UploadProperties();
        props.setDir(tempDir.toString());
        props.setTtlHours(24);

        new UploadCleanupTask(props).cleanup();

        assertFalse(Files.exists(oldDir), "old dir should be deleted");
        assertTrue(Files.exists(newDir), "new dir should remain");
    }

    @Test
    void noOpWhenDirMissing() {
        var props = new UploadProperties();
        props.setDir(tempDir.resolve("does-not-exist").toString());
        props.setTtlHours(24);
        assertDoesNotThrow(() -> new UploadCleanupTask(props).cleanup());
    }
}
