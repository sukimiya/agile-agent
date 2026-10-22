package io.github.sukimiya.agileagent.service;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

import org.springframework.http.codec.multipart.FilePart;
import org.springframework.stereotype.Service;

import io.github.sukimiya.agileagent.config.UploadProperties;
import io.github.sukimiya.agileagent.memory.ConversationIds;

import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

/**
 * Local attachment storage under {@code ${app.upload.dir}/{conversationId}/{name}}. The directory is the
 * conversation id itself, accepted only in its canonical form ({@link ConversationIds}), so distinct
 * conversations never share a directory; only file names are cleaned. Ownership is checked by the caller before
 * any call here. Nothing returned from this service carries a server path.
 */
@Service
public class FileStorageService {

    /** A stored attachment: its final file name inside the conversation directory and its size. */
    public record Ref(String name, long size) {}

    private final UploadProperties props;

    public FileStorageService(UploadProperties props) {
        this.props = props;
    }

    public static class RejectedException extends RuntimeException {
        private final String code;
        public RejectedException(String code, String message) {
            super(message);
            this.code = code;
        }
        public String getCode() { return code; }
    }

    public Mono<Ref> save(String sessionId, FilePart part) {
        return Mono.defer(() -> {
            ConversationIds.validate(sessionId);
            final Path temp;
            try {
                temp = Files.createTempFile("upload-", ".tmp");
            } catch (IOException e) {
                return Mono.error(e);
            }
            return part.transferTo(temp)
                    .then(Mono.fromCallable(() -> finalizeSave(sessionId, part, temp))
                            .subscribeOn(Schedulers.boundedElastic()))
                    .doFinally(sig -> {
                        try { Files.deleteIfExists(temp); } catch (IOException ignored) {}
                    });
        });
    }

    private Ref finalizeSave(String sessionId, FilePart part, Path temp) throws IOException {
        long size = Files.size(temp);
        if (size > props.getMaxSizeBytes()) {
            throw new RejectedException("FILE_REJECTED", "file exceeds " + props.getMaxSizeMb() + " MB");
        }
        var contentType = part.headers().getContentType();
        var declaredType = contentType == null ? null : contentType.toString();
        if (!props.getAllowedContentTypes().contains(declaredType)) {
            throw new RejectedException("FILE_REJECTED", "content-type " + declaredType + " not allowed");
        }
        var detector = new org.apache.tika.detect.DefaultDetector();
        var metadata = new org.apache.tika.metadata.Metadata();
        String detected;
        try (var is = new BufferedInputStream(Files.newInputStream(temp))) {
            detected = detector.detect(is, metadata).toString();
        }
        if (!props.getAllowedContentTypes().contains(detected)) {
            throw new RejectedException("MIME_MISMATCH", "real type " + detected + " differs from declared " + declaredType);
        }
        var dir = conversationDir(sessionId);
        Files.createDirectories(dir);
        var target = resolveUnique(dir, sanitize(part.filename()));
        Files.copy(temp, target, StandardCopyOption.REPLACE_EXISTING);
        return new Ref(target.getFileName().toString(), size);
    }

    /**
     * The stored file {@code name} of a conversation, if it exists. The id must be canonical
     * ({@code INVALID_CONVERSATION_ID} otherwise) and the name a plain file name ({@code INVALID_PATH}
     * otherwise); the resolved path is confined to the conversation directory.
     */
    public java.util.Optional<Path> locate(String conversationId, String name) {
        var dir = conversationDir(ConversationIds.validate(conversationId));
        if (name == null || name.isBlank() || name.contains("/") || name.contains("\\") || name.contains("..")
                || name.indexOf('\0') >= 0) {
            throw new RejectedException("INVALID_PATH", "invalid file name");
        }
        var path = dir.resolve(name).normalize();
        if (!dir.equals(path.getParent()) || !Files.isRegularFile(path)) {
            return java.util.Optional.empty();
        }
        return java.util.Optional.of(path);
    }

    private Path conversationDir(String conversationId) {
        return Path.of(props.getDir(), ConversationIds.validate(conversationId)).normalize();
    }

    /** File names only; conversation ids are validated, never sanitized. */
    private static String sanitize(String name) {
        if (name == null || name.isBlank()) return "file";
        var basename = name.replaceAll("[\\\\/]", "_").replaceAll("\\.\\.+", "_");
        return basename.isBlank() ? "file" : basename;
    }

    private static Path resolveUnique(Path dir, String name) {
        var target = dir.resolve(name);
        if (!Files.exists(target)) return target;
        var dotIdx = name.lastIndexOf('.');
        var stem = dotIdx > 0 ? name.substring(0, dotIdx) : name;
        var ext = dotIdx > 0 ? name.substring(dotIdx) : "";
        for (int i = 1; i < 1000; i++) {
            var candidate = dir.resolve(stem + "-" + i + ext);
            if (!Files.exists(candidate)) return candidate;
        }
        throw new IllegalStateException("Too many collisions for " + name);
    }
}
