package com.jowo.agent.service;

import java.io.IOException;
import java.nio.file.*;
import java.time.Instant;
import org.slf4j.*;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import com.jowo.agent.config.UploadProperties;

@Component
public class UploadCleanupTask {

    private static final Logger log = LoggerFactory.getLogger(UploadCleanupTask.class);
    private final UploadProperties props;

    public UploadCleanupTask(UploadProperties props) {
        this.props = props;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onStartup() {
        cleanup();
    }

    @Scheduled(fixedRate = 3_600_000L)
    public void scheduledCleanup() {
        cleanup();
    }

    public void cleanup() {
        var root = Path.of(props.getDir());
        if (!Files.isDirectory(root)) return;
        var cutoff = Instant.now().minusMillis(props.getTtlMillis());
        try (var stream = Files.list(root)) {
            stream.filter(Files::isDirectory).forEach(p -> {
                try {
                    if (Files.getLastModifiedTime(p).toInstant().isBefore(cutoff)) {
                        deleteRecursively(p);
                    }
                } catch (IOException e) {
                    log.warn("cleanup error for {}: {}", p, e.getMessage());
                }
            });
        } catch (IOException e) {
            log.warn("cleanup list error: {}", e.getMessage());
        }
    }

    private static void deleteRecursively(Path p) throws IOException {
        try (var walk = Files.walk(p)) {
            walk.sorted(java.util.Comparator.reverseOrder()).forEach(child -> {
                try { Files.deleteIfExists(child); } catch (IOException ignored) {}
            });
        }
    }
}
