package io.github.sukimiya.agileagent.knowledge;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.LinkOption;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.sukimiya.agileagent.config.KnowledgeProperties;
import io.github.sukimiya.agileagent.knowledge.KnowledgeDocumentRepository.ApprovedArtifactPointer;

import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

@Component
public class KnowledgeCatalog {

    public record KnowledgeEntry(String id, String title, String summary, String owner, long version) {}

    private final KnowledgeProperties props;
    private final KnowledgeDocumentRepository repository;
    private final ObjectMapper json = new ObjectMapper();
    private final AtomicReference<List<KnowledgeEntry>> entries = new AtomicReference<>(List.of());

    public KnowledgeCatalog(KnowledgeProperties props, KnowledgeDocumentRepository repository) {
        this.props = props;
        this.repository = repository;
    }

    public void reload() {
        entries.set(loadAll());
    }

    private List<KnowledgeEntry> loadAll() {
        return approvedEntries().blockOptional().orElse(List.of());
    }

    private Mono<List<KnowledgeEntry>> approvedEntries() {
        return repository.findApprovedArtifactPointers().collectList()
                .publishOn(Schedulers.boundedElastic())
                .map(this::loadApprovedArtifacts);
    }

    private List<KnowledgeEntry> loadApprovedArtifacts(List<ApprovedArtifactPointer> pointers) {
        var libDir = Path.of(props.getLibDir()).toAbsolutePath().normalize();
        if (!Files.isDirectory(libDir)) {
            return List.of();
        }
        var result = new ArrayList<KnowledgeEntry>();
        for (var pointer : pointers) {
            var metaFile = KnowledgeFileStorage.approvedArtifactPath(
                    libDir, pointer.id(), pointer.owner(), pointer.version(), ".meta.json");
            var markdownFile = KnowledgeFileStorage.approvedArtifactPath(
                    libDir, pointer.id(), pointer.owner(), pointer.version(), ".md");
            if (!Files.isRegularFile(metaFile) || Files.isSymbolicLink(metaFile)
                    || !Files.isRegularFile(markdownFile) || Files.isSymbolicLink(markdownFile)) {
                continue;
            }
            result.add(parseEntry(pointer.id(), pointer.owner(), pointer.version(), metaFile));
        }
        return List.copyOf(result);
    }

    private KnowledgeEntry parseEntry(String id, String owner, long version, Path metaFile) {
        try {
            var node = json.readTree(Files.readString(metaFile, StandardCharsets.UTF_8));
            return new KnowledgeEntry(id, node.path("title").asText(""),
                    node.path("summary").asText(""), owner, version);
        } catch (IOException e) {
            throw new UncheckedIOException("failed to read " + metaFile, e);
        }
    }

    public Mono<String> compactIndex() {
        return approvedEntries().doOnNext(entries::set).map(KnowledgeCatalog::formatIndex);
    }

    public Mono<String> content(String id) {
        return repository.findApprovedArtifactPointers()
                .filter(pointer -> pointer.id().equals(id))
                .next()
                .publishOn(Schedulers.boundedElastic())
                .flatMap(pointer -> Mono.fromCallable(() -> readContent(pointer))
                        .onErrorResume(error -> Mono.just("")))
                .defaultIfEmpty("");
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
}
