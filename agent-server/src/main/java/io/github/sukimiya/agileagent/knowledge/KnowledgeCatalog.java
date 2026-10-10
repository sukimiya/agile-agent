package io.github.sukimiya.agileagent.knowledge;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.LinkOption;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.sukimiya.agileagent.config.KnowledgeProperties;
import io.github.sukimiya.agileagent.knowledge.KnowledgeDocumentRepository.ApprovedArtifactPointer;
import io.github.sukimiya.agileagent.spi.identity.IdentityContext;

import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

@Component
public class KnowledgeCatalog {

    public record KnowledgeEntry(String id, String title, String summary, String owner, long version) {}

    private final KnowledgeProperties props;
    private final KnowledgeDocumentRepository repository;
    private final ObjectMapper json = new ObjectMapper();
    private final KnowledgeAccessPolicy accessPolicy;

    public KnowledgeCatalog(KnowledgeProperties props, KnowledgeDocumentRepository repository,
            KnowledgeAccessPolicy accessPolicy) {
        this.props = props;
        this.repository = repository;
        this.accessPolicy = accessPolicy;
    }

    public void reload() {
        repository.findApprovedArtifactPointers().then().block();
    }

    private Mono<List<KnowledgeEntry>> approvedEntries(IdentityContext identity) {
        return repository.findApprovedArtifactPointers().collectList()
                .publishOn(Schedulers.boundedElastic())
                .map(pointers -> loadApprovedArtifacts(pointers, identity));
    }

    private List<KnowledgeEntry> loadApprovedArtifacts(List<ApprovedArtifactPointer> pointers,
            IdentityContext identity) {
        var libDir = Path.of(props.getLibDir()).toAbsolutePath().normalize();
        if (!Files.isDirectory(libDir)) {
            return List.of();
        }
        var result = new ArrayList<KnowledgeEntry>();
        for (var pointer : pointers) {
            if (!accessPolicy.canConsume(identity, pointer.accessRow())) {
                continue;
            }
            try {
                var artifact = readApprovedArtifact(pointer);
                if (artifact != null) {
                    result.add(artifact.entry());
                }
            } catch (IOException ignored) {
                // An unreadable artifact is not advertised in the compact index.
            }
        }
        return List.copyOf(result);
    }

    private KnowledgeEntry parseEntry(String id, String owner, long version, Path metaFile) {
        try {
            var node = json.readTree(Files.readString(metaFile, StandardCharsets.UTF_8));
            return new KnowledgeEntry(id, node.path("title").asText(""),
                    node.path("summary").asText(""), owner, version);
        } catch (IOException | RuntimeException e) {
            throw new IllegalStateException("failed to read approved knowledge metadata");
        }
    }

    public Mono<String> compactIndex(IdentityContext identity) {
        return approvedEntries(identity).map(KnowledgeCatalog::formatIndex);
    }

    public Mono<String> content(String id, IdentityContext identity) {
        return repository.findApprovedArtifactPointers()
                .filter(pointer -> pointer.id().equals(id))
                .filter(pointer -> accessPolicy.canConsume(identity, pointer.accessRow()))
                .next()
                .publishOn(Schedulers.boundedElastic())
                .flatMap(pointer -> Mono.fromCallable(() -> readApprovedArtifact(pointer))
                        .onErrorResume(error -> Mono.empty()))
                .map(artifact -> artifact == null ? "" : artifact.content())
                .defaultIfEmpty("");
    }

    private ApprovedArtifact readApprovedArtifact(ApprovedArtifactPointer pointer) throws IOException {
        Path libDir = Path.of(props.getLibDir()).toAbsolutePath().normalize();
        var metaFile = KnowledgeFileStorage.approvedArtifactPath(
                libDir, pointer.id(), pointer.owner(), pointer.version(), ".meta.json");
        var markdownFile = KnowledgeFileStorage.approvedArtifactPath(
                libDir, pointer.id(), pointer.owner(), pointer.version(), ".md");
        if (!Files.isRegularFile(metaFile, LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(metaFile)
                || !Files.isRegularFile(markdownFile, LinkOption.NOFOLLOW_LINKS)
                || Files.isSymbolicLink(markdownFile)) {
            return null;
        }
        var entry = parseEntry(pointer.id(), pointer.owner(), pointer.version(), metaFile);
        var content = readContent(pointer);
        return content.isEmpty() ? null : new ApprovedArtifact(entry, content);
    }

    private String readContent(ApprovedArtifactPointer pointer) throws IOException {
        Path contentPath = KnowledgeFileStorage.approvedArtifactPath(
                Path.of(props.getLibDir()).toAbsolutePath().normalize(),
                pointer.id(), pointer.owner(), pointer.version(), ".md");
        if (Files.isSymbolicLink(contentPath)
                || !Files.isRegularFile(contentPath, LinkOption.NOFOLLOW_LINKS)) {
            return "";
        }
        return Files.readString(contentPath, StandardCharsets.UTF_8);
    }

    private static String formatIndex(List<KnowledgeEntry> catalogEntries) {
        var sb = new StringBuilder();
        for (var entry : catalogEntries) {
            sb.append(entry.id()).append(" | ").append(entry.title()).append(" | ")
                    .append(entry.summary()).append('\n');
        }
        return sb.toString();
    }

    private record ApprovedArtifact(KnowledgeEntry entry, String content) {}
}
