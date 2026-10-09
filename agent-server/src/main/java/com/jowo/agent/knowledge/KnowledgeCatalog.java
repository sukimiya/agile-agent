package com.jowo.agent.knowledge;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;

import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jowo.agent.config.KnowledgeProperties;

import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

@Component
public class KnowledgeCatalog {

    public record KnowledgeEntry(String id, String title, String summary) {}

    private final KnowledgeProperties props;
    private final ObjectMapper json = new ObjectMapper();
    private final AtomicReference<List<KnowledgeEntry>> entries = new AtomicReference<>();

    public KnowledgeCatalog(KnowledgeProperties props) {
        this.props = props;
        reload();
    }

    public void reload() {
        entries.set(loadAll());
    }

    private List<KnowledgeEntry> loadAll() {
        var libDir = Path.of(props.getLibDir());
        if (!Files.isDirectory(libDir)) {
            return List.of();
        }
        var result = new ArrayList<KnowledgeEntry>();
        try (Stream<Path> files = Files.list(libDir)) {
            files.filter(p -> p.getFileName().toString().endsWith(".meta.json"))
                    .sorted()
                    .forEach(metaFile -> result.add(parseEntry(metaFile)));
        } catch (IOException e) {
            throw new UncheckedIOException("failed to list knowledge lib dir " + libDir, e);
        }
        return result;
    }

    private KnowledgeEntry parseEntry(Path metaFile) {
        try {
            String fileName = metaFile.getFileName().toString();
            String id = fileName.substring(0, fileName.length() - ".meta.json".length());
            var node = json.readTree(Files.readString(metaFile, StandardCharsets.UTF_8));
            return new KnowledgeEntry(id, node.path("title").asText(""), node.path("summary").asText(""));
        } catch (IOException e) {
            throw new UncheckedIOException("failed to read " + metaFile, e);
        }
    }

    public String compactIndex() {
        var sb = new StringBuilder();
        for (var e : entries.get()) {
            sb.append(e.id()).append(" | ").append(e.title()).append(" | ").append(e.summary()).append('\n');
        }
        return sb.toString();
    }

    public Mono<String> content(String id) {
        return findEntry(id)
                .map(e -> Mono.fromCallable(() -> readContent(id))
                        .subscribeOn(Schedulers.boundedElastic()))
                .orElseGet(() -> Mono.just(""));
    }

    private Optional<KnowledgeEntry> findEntry(String id) {
        return entries.get().stream().filter(e -> e.id().equals(id)).findFirst();
    }

    private String readContent(String id) throws IOException {
        return Files.readString(Path.of(props.getLibDir(), id + ".md"), StandardCharsets.UTF_8);
    }
}
