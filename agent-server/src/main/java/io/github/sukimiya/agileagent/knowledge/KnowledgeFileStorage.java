package io.github.sukimiya.agileagent.knowledge;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.UUID;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.springframework.http.MediaType;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.sukimiya.agileagent.config.KnowledgeProperties;
import io.github.sukimiya.agileagent.knowledge.KnowledgeDocumentRepository.KnowledgeDocumentRow.ScopeType;

import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

@Component
public class KnowledgeFileStorage {

    private static final Pattern GENERATED_ID = Pattern.compile(
            "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}");
    private static final Pattern STAGING_SUFFIX = Pattern.compile("[0-9a-f-]{36}\\.(md|meta)\\.stage");
    private static final java.util.Set<String> ALLOWED_CONTENT_TYPES = java.util.Set.of(
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

    public static class StorageFailureException extends RuntimeException {
        public StorageFailureException() {
            super("knowledge storage operation failed");
        }
    }

    public record UploadedFile(String tempPath, String content) {}

    public record LibraryMetadata(String title, String summary, String approvedBy, LocalDateTime approvedAt,
                                  ScopeType scopeType, String tenantId, String tenantName,
                                  String orgId, String orgName) {}

    public Mono<UploadedFile> saveTemp(String id, FilePart part) {
        return Mono.defer(() -> {
            var contentType = part.headers().getContentType();
            var declaredType = contentType == null ? null : contentType.toString();
            if (declaredType == null || !ALLOWED_CONTENT_TYPES.contains(declaredType)) {
                return Mono.error(new RejectedException("FILE_REJECTED",
                        "content type is not allowed"));
            }
            return Mono.usingWhen(
                    Mono.fromCallable(() -> prepareTempTarget(id))
                            .subscribeOn(Schedulers.boundedElastic())
                            .onErrorMap(KnowledgeFileStorage::sanitizeStorageFailure),
                    target -> part.transferTo(target)
                            .subscribeOn(Schedulers.boundedElastic())
                            .then(Mono.fromCallable(() -> finalizeTemp(target))
                                    .subscribeOn(Schedulers.boundedElastic()))
                            .onErrorMap(KnowledgeFileStorage::sanitizeStorageFailure),
                    target -> Mono.empty(),
                    (target, error) -> deletePath(target),
                    target -> deletePath(target));
        });
    }

    private Path prepareTempTarget(String id) throws IOException {
        var root = configuredRoot(props.getTempDir());
        Files.createDirectories(root);
        var realRoot = root.toRealPath();
        var target = confinedPath(realRoot, id, ".md");
        if (Files.exists(target, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("target already exists");
        }
        return target;
    }

    private UploadedFile finalizeTemp(Path target) throws IOException {
        long size = Files.size(target);
        if (size > props.getMaxSizeBytes()) {
            throw new RejectedException("FILE_REJECTED", "file exceeds the configured size limit");
        }
        return new UploadedFile(target.toAbsolutePath().normalize().toString(),
                Files.readString(target, StandardCharsets.UTF_8));
    }

    public Mono<Void> deleteTemp(String id) {
        return Mono.fromRunnable(() -> {
            try {
                var root = configuredRoot(props.getTempDir());
                if (!Files.exists(root, LinkOption.NOFOLLOW_LINKS)) {
                    return;
                }
                deleteGeneratedFile(confinedPath(root.toRealPath(), id, ".md"));
            } catch (IOException e) {
                throw new StorageFailureException();
            }
        }).subscribeOn(Schedulers.boundedElastic()).then();
    }

    public Mono<Void> promoteToLib(String id, LibraryMetadata metadata) {
        return Mono.fromRunnable(() -> doPromote(id, metadata))
                .subscribeOn(Schedulers.boundedElastic())
                .then();
    }

    private void doPromote(String id, LibraryMetadata metadata) {
        Path stageMarkdown = null;
        Path stageMetadata = null;
        Path libraryMarkdown = null;
        boolean markdownPublished = false;
        try {
            var tempRoot = configuredRoot(props.getTempDir()).toRealPath();
            var libRoot = configuredRoot(props.getLibDir());
            Files.createDirectories(libRoot);
            libRoot = libRoot.toRealPath();

            var tempFile = confinedPath(tempRoot, id, ".md");
            if (Files.isSymbolicLink(tempFile) || !Files.isRegularFile(tempFile, LinkOption.NOFOLLOW_LINKS)) {
                throw new IOException("temporary document is unavailable");
            }
            libraryMarkdown = confinedPath(libRoot, id, ".md");
            var libraryMetadata = confinedPath(libRoot, id, ".meta.json");
            if (Files.exists(libraryMarkdown, LinkOption.NOFOLLOW_LINKS)) {
                throw new IOException("library document already exists");
            }

            var token = UUID.randomUUID().toString();
            stageMarkdown = libRoot.resolve(id + "." + token + ".md.stage").normalize();
            stageMetadata = libRoot.resolve(id + "." + token + ".meta.stage").normalize();
            ensureWithinRoot(libRoot, stageMarkdown);
            ensureWithinRoot(libRoot, stageMetadata);
            Files.copy(tempFile, stageMarkdown);
            Files.writeString(stageMetadata, serializeMetadata(metadata), StandardCharsets.UTF_8);

            moveWithoutReplacement(stageMarkdown, libraryMarkdown);
            markdownPublished = true;
            // The metadata file is the catalog's publication marker, so it moves last.
            moveWithoutReplacement(stageMetadata, libraryMetadata);
        } catch (IOException | RuntimeException error) {
            if (markdownPublished && libraryMarkdown != null) {
                deleteGeneratedFileQuietly(libraryMarkdown);
            }
            deleteGeneratedFileQuietly(stageMarkdown);
            deleteGeneratedFileQuietly(stageMetadata);
            throw sanitizeStorageFailure(error);
        } finally {
            deleteGeneratedFileQuietly(stageMarkdown);
            deleteGeneratedFileQuietly(stageMetadata);
        }
    }

    private String serializeMetadata(LibraryMetadata metadata) throws IOException {
        var node = json.createObjectNode();
        node.put("title", metadata.title());
        node.put("summary", metadata.summary() == null ? "" : metadata.summary());
        node.put("approvedBy", metadata.approvedBy());
        node.put("approvedAt", metadata.approvedAt().toString());
        node.put("scopeType", metadata.scopeType().name());
        putNullable(node, "tenantId", metadata.tenantId());
        putNullable(node, "tenantName", metadata.tenantName());
        putNullable(node, "orgId", metadata.orgId());
        putNullable(node, "orgName", metadata.orgName());
        return json.writeValueAsString(node);
    }

    private static void putNullable(com.fasterxml.jackson.databind.node.ObjectNode node, String key, String value) {
        if (value == null) {
            node.putNull(key);
        } else {
            node.put(key, value);
        }
    }

    public Mono<Void> deleteLibraryArtifacts(String id) {
        return Mono.fromRunnable(() -> {
            try {
                var root = configuredRoot(props.getLibDir());
                if (!Files.exists(root, LinkOption.NOFOLLOW_LINKS)) {
                    return;
                }
                var realRoot = root.toRealPath();
                deleteGeneratedFile(confinedPath(realRoot, id, ".md"));
                deleteGeneratedFile(confinedPath(realRoot, id, ".meta.json"));
                deleteStagingFiles(realRoot, id);
            } catch (IOException e) {
                throw new StorageFailureException();
            }
        }).subscribeOn(Schedulers.boundedElastic()).then();
    }

    private static void deleteStagingFiles(Path root, String id) throws IOException {
        try (Stream<Path> files = Files.list(root)) {
            files.filter(path -> {
                var name = path.getFileName().toString();
                if (!name.startsWith(id + ".")) {
                    return false;
                }
                return STAGING_SUFFIX.matcher(name.substring(id.length() + 1)).matches();
            }).forEach(KnowledgeFileStorage::deleteGeneratedFileQuietly);
        }
    }

    private static void moveWithoutReplacement(Path source, Path target) throws IOException {
        Files.move(source, target);
    }

    private static Path configuredRoot(String configuredPath) {
        if (configuredPath == null || configuredPath.isBlank()) {
            throw new StorageFailureException();
        }
        return Path.of(configuredPath).toAbsolutePath().normalize();
    }

    private static Path confinedPath(Path root, String id, String suffix) {
        if (id == null || !GENERATED_ID.matcher(id).matches()) {
            throw new RejectedException("FILE_REJECTED", "document id is invalid");
        }
        var target = root.resolve(id + suffix).normalize();
        ensureWithinRoot(root, target);
        return target;
    }

    private static void ensureWithinRoot(Path root, Path target) {
        if (!target.startsWith(root)) {
            throw new RejectedException("FILE_REJECTED", "document path is invalid");
        }
    }

    private static void deleteGeneratedFile(Path path) throws IOException {
        if (Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(path)) {
            Files.deleteIfExists(path);
        }
    }

    private static void deleteGeneratedFileQuietly(Path path) {
        if (path == null) {
            return;
        }
        try {
            deleteGeneratedFile(path);
        } catch (IOException ignored) {
            // Cleanup is best effort after the primary operation has already failed.
        }
    }

    private static Mono<Void> deletePath(Path path) {
        return Mono.fromCallable(() -> {
                    deleteGeneratedFile(path);
                    return true;
                })
                .subscribeOn(Schedulers.boundedElastic())
                .onErrorMap(KnowledgeFileStorage::sanitizeStorageFailure)
                .onErrorResume(error -> Mono.empty())
                .then();
    }

    private static RuntimeException sanitizeStorageFailure(Throwable error) {
        if (error instanceof RejectedException || error instanceof StorageFailureException) {
            return (RuntimeException) error;
        }
        return new StorageFailureException();
    }
}
