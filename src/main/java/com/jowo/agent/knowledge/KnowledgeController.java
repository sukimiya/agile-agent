package com.jowo.agent.knowledge;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.Map;

import org.springframework.core.io.FileSystemResource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;

import com.jowo.agent.config.KnowledgeProperties;
import com.jowo.agent.knowledge.KnowledgeDocumentRepository.KnowledgeDocumentRow;
import com.jowo.agent.knowledge.KnowledgeDocumentRepository.KnowledgeDocumentRow.Status;
import com.jowo.agent.service.AuthSessionStore;
import com.jowo.agent.web.CurrentUser;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/knowledge")
@CrossOrigin(origins = "*")
class KnowledgeController {

    private final KnowledgeDocumentService service;
    private final KnowledgeProperties props;

    KnowledgeController(KnowledgeDocumentService service, KnowledgeProperties props) {
        this.service = service;
        this.props = props;
    }

    @PostMapping("/upload")
    Mono<ResponseEntity<Object>> upload(
            @RequestPart("file") FilePart file,
            @RequestPart(value = "title", required = false) String title,
            @CurrentUser AuthSessionStore.AuthUser user) {
        return service.upload(user.userId(), title, file)
                .map(row -> ResponseEntity.ok((Object) toSummary(row)))
                .onErrorResume(KnowledgeFileStorage.RejectedException.class, e -> Mono.just(
                        ResponseEntity.badRequest().body((Object) Map.of(
                                "code", e.getCode(), "reason", e.getMessage()))));
    }

    @GetMapping
    Flux<KnowledgeDocumentSummary> list(
            @RequestParam(value = "status", defaultValue = "PENDING") Status status,
            @CurrentUser AuthSessionStore.AuthUser user) {
        return service.list(status).map(KnowledgeController::toSummary);
    }

    @GetMapping("/{id}/download")
    Mono<ResponseEntity<?>> download(@PathVariable String id, @CurrentUser AuthSessionStore.AuthUser user) {
        return service.get(id)
                .map(row -> {
                    var path = row.status() == Status.APPROVED
                            ? Path.of(props.getLibDir(), row.id() + ".md")
                            : Path.of(row.tempPath());
                    if (!Files.isRegularFile(path)) {
                        return (ResponseEntity<?>) ResponseEntity.notFound().build();
                    }
                    return ResponseEntity.ok()
                            .contentType(MediaType.TEXT_PLAIN)
                            .body((Object) new FileSystemResource(path));
                })
                .defaultIfEmpty((ResponseEntity<?>) ResponseEntity.notFound().build());
    }

    @PostMapping("/{id}/approve")
    Mono<ResponseEntity<Void>> approve(@PathVariable String id,
            @RequestBody(required = false) ApproveRequest body,
            @CurrentUser AuthSessionStore.AuthUser user) {
        String override = body == null ? null : body.summary();
        return service.approve(id, user.userId(), override).map(KnowledgeController::toStatus);
    }

    @PostMapping("/{id}/reject")
    Mono<ResponseEntity<Void>> reject(@PathVariable String id, @CurrentUser AuthSessionStore.AuthUser user) {
        return service.reject(id, user.userId()).map(KnowledgeController::toStatus);
    }

    private static ResponseEntity<Void> toStatus(KnowledgeDocumentService.ActionResult result) {
        return switch (result) {
            case OK -> ResponseEntity.ok().build();
            case NOT_FOUND -> ResponseEntity.notFound().build();
            case CONFLICT -> ResponseEntity.status(HttpStatus.CONFLICT).build();
        };
    }

    private static KnowledgeDocumentSummary toSummary(KnowledgeDocumentRow row) {
        return new KnowledgeDocumentSummary(row.id(), row.title(), row.uploaderUserId(), row.summary(),
                row.uploadedAt(), row.status().name(), "/api/knowledge/" + row.id() + "/download");
    }

    record ApproveRequest(String summary) {}

    record KnowledgeDocumentSummary(String id, String title, String uploaderUserId, String summary,
            LocalDateTime uploadedAt, String status, String downloadUrl) {}
}
