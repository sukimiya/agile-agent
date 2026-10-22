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
        var oldDir = tempDir.resolve("0b6d1c5e-1f7a-4e2b-9c3d-4a5b6c7d8e9f");
        var newDir = tempDir.resolve("7e8f9a0b-1c2d-4e3f-8a4b-5c6d7e8f9a0b");
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
    void skipsDirectoriesThatAreNotConversationIds() throws Exception {
        var foreign = tempDir.resolve("a_b");
        var upper = tempDir.resolve("0B6D1C5E-1F7A-4E2B-9C3D-4A5B6C7D8E9F");
        for (var dir : new Path[] {foreign, upper}) {
            Files.createDirectories(dir);
            Files.writeString(dir.resolve("keep.txt"), "k");
            Files.setLastModifiedTime(dir, FileTime.from(Instant.now().minusSeconds(48 * 3600)));
        }
        var props = new UploadProperties();
        props.setDir(tempDir.toString());
        props.setTtlHours(24);

        new UploadCleanupTask(props).cleanup();

        assertTrue(Files.exists(foreign.resolve("keep.txt")), "non-conforming names are never matched to a session");
        assertTrue(Files.exists(upper.resolve("keep.txt")));
    }

    @Test
    void noOpWhenDirMissing() {
        var props = new UploadProperties();
        props.setDir(tempDir.resolve("does-not-exist").toString());
        props.setTtlHours(24);
        assertDoesNotThrow(() -> new UploadCleanupTask(props).cleanup());
    }
}
