package com.jowo.agent.knowledge;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.util.Set;

import org.springframework.http.MediaType;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jowo.agent.config.KnowledgeProperties;

import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

@Component
public class KnowledgeFileStorage {

    private static final Set<String> ALLOWED_CONTENT_TYPES = Set.of(
            MediaType.TEXT_PLAIN_VALUE, "text/markdown");

    private final KnowledgeProperties props;
    private final ObjectMapper json = new ObjectMapper();

    public KnowledgeFileStorage(KnowledgeProperties props) {
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

    public record UploadedFile(String tempPath, String content) {}

    public Mono<UploadedFile> saveTemp(String id, FilePart part) {
        return Mono.defer(() -> {
            var contentType = part.headers().getContentType();
            var declaredType = contentType == null ? null : contentType.toString();
            if (declaredType == null || !ALLOWED_CONTENT_TYPES.contains(declaredType)) {
                return Mono.error(new RejectedException("FILE_REJECTED",
                        "content-type " + declaredType + " not allowed"));
            }
            var target = Path.of(props.getTempDir(), id + ".md");
            return Mono.fromCallable(() -> Files.createDirectories(target.getParent()))
                    .subscribeOn(Schedulers.boundedElastic())
                    .then(part.transferTo(target))
                    .then(Mono.fromCallable(() -> finalizeTemp(target))
                            .subscribeOn(Schedulers.boundedElastic()));
        });
    }

    private UploadedFile finalizeTemp(Path target) throws IOException {
        long size = Files.size(target);
        if (size > props.getMaxSizeBytes()) {
            Files.deleteIfExists(target);
            throw new RejectedException("FILE_REJECTED", "file exceeds " + props.getMaxSizeMb() + " MB");
        }
        String content = Files.readString(target, StandardCharsets.UTF_8);
        return new UploadedFile(target.toAbsolutePath().toString(), content);
    }

    public Mono<Void> promoteToLib(String id, String tempPath, String title, String summary,
                                    String approvedBy, LocalDateTime approvedAt) {
        return Mono.fromRunnable(() -> doPromote(id, tempPath, title, summary, approvedBy, approvedAt))
                .subscribeOn(Schedulers.boundedElastic())
                .then();
    }

    private void doPromote(String id, String tempPath, String title, String summary,
                            String approvedBy, LocalDateTime approvedAt) {
        try {
            var libDir = Path.of(props.getLibDir());
            Files.createDirectories(libDir);
            Files.copy(Path.of(tempPath), libDir.resolve(id + ".md"), StandardCopyOption.REPLACE_EXISTING);
            var meta = json.createObjectNode();
            meta.put("title", title);
            meta.put("summary", summary);
            meta.put("approvedBy", approvedBy);
            meta.put("approvedAt", approvedAt.toString());
            Files.writeString(libDir.resolve(id + ".meta.json"), json.writeValueAsString(meta));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
