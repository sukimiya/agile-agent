package io.github.sukimiya.agileagent.knowledge;

import java.nio.file.Files;
import java.nio.file.LinkOption;
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

import io.github.sukimiya.agileagent.config.KnowledgeProperties;
import io.github.sukimiya.agileagent.knowledge.KnowledgeDocumentRepository.KnowledgeDocumentRow;
import io.github.sukimiya.agileagent.knowledge.KnowledgeDocumentRepository.KnowledgeDocumentRow.Status;
import io.github.sukimiya.agileagent.service.AuthSessionStore;
import io.github.sukimiya.agileagent.web.ApiError;
import io.github.sukimiya.agileagent.web.CurrentUser;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

@RestController
@RequestMapping("/api/knowledge")
@CrossOrigin(origins = "*")
class KnowledgeController {

    private final KnowledgeDocumentService service;
    private final KnowledgeProperties props;
    private final KnowledgeDocumentRepository repository;

    KnowledgeController(KnowledgeDocumentService service, KnowledgeProperties props,
            KnowledgeDocumentRepository repository) {
        this.service = service;
        this.props = props;
        this.repository = repository;
    }

    @PostMapping("/upload")
    Mono<ResponseEntity<Object>> upload(
            @RequestPart("file") FilePart file,
            @RequestPart(value = "title", required = false) String title,
            @CurrentUser AuthSessionStore.AuthUser user) {
        return service.upload(user.identity(), title, file)
                .map(row -> ResponseEntity.ok((Object) toSummary(row)))
                .onErrorResume(KnowledgeFileStorage.RejectedException.class, e -> Mono.just(
                        ResponseEntity.badRequest().body((Object) Map.of(
                                "code", e.getCode(), "reason", e.getMessage()))))
                .onErrorResume(KnowledgeAccessPolicy.UploadIdentityException.class, e -> Mono.just(
                        ResponseEntity.badRequest().body((Object) new ApiError(
                                "IDENTITY_SCOPE_REQUIRED", e.getMessage()))));
    }

    @GetMapping
    Flux<KnowledgeDocumentSummary> list(
            @RequestParam(value = "status", defaultValue = "PENDING") Status status,
            @CurrentUser AuthSessionStore.AuthUser user) {
        return service.list(status, user.identity()).map(KnowledgeController::toSummary);
    }

    @GetMapping("/{id}/download")
    Mono<ResponseEntity<?>> download(@PathVariable String id, @CurrentUser AuthSessionStore.AuthUser user) {
        return service.get(id, user.identity())
                .flatMap(row -> row.status() == Status.APPROVED
                        ? repository.findApprovedArtifactPointers()
                                .filter(pointer -> pointer.id().equals(row.id()))
                                .next()
                                .publishOn(Schedulers.boundedElastic())
                                .flatMap(pointer -> fileResponse(KnowledgeFileStorage.approvedArtifactPath(
                                        Path.of(props.getLibDir()).toAbsolutePath().normalize(),
                                        pointer.id(), pointer.owner(), pointer.version(), ".md")))
                        : fileResponse(Path.of(row.tempPath())))
                .defaultIfEmpty(notFound());
    }

    private static Mono<ResponseEntity<?>> fileResponse(Path path) {
        return Mono.fromCallable(() -> {
            if (Files.isSymbolicLink(path) || !Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) {
                return notFound();
            }
            return ResponseEntity.ok()
                    .contentType(MediaType.TEXT_PLAIN)
                    .body((Object) new FileSystemResource(path));
        }).subscribeOn(Schedulers.boundedElastic());
    }

    @PostMapping("/{id}/approve")
    Mono<ResponseEntity<?>> approve(@PathVariable String id,
            @RequestBody(required = false) ApproveRequest body,
            @CurrentUser AuthSessionStore.AuthUser user) {
        String override = body == null ? null : body.summary();
        return service.approve(id, user.identity(), override).map(KnowledgeController::toStatus);
    }

    @PostMapping("/{id}/reject")
    Mono<ResponseEntity<?>> reject(@PathVariable String id, @CurrentUser AuthSessionStore.AuthUser user) {
        return service.reject(id, user.identity()).map(KnowledgeController::toStatus);
    }

    private static ResponseEntity<?> toStatus(KnowledgeDocumentService.ActionResult result) {
        return switch (result) {
            case OK -> ResponseEntity.ok().build();
            case NOT_FOUND -> notFound();
            case FORBIDDEN -> ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(new ApiError("KNOWLEDGE_FORBIDDEN", "Knowledge document is outside your management scope"));
            case CONFLICT -> ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(new ApiError("KNOWLEDGE_STATE_CONFLICT", "Knowledge document is not pending"));
        };
    }

    private static ResponseEntity<?> notFound() {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new ApiError("KNOWLEDGE_DOCUMENT_NOT_FOUND", "Knowledge document not found"));
    }

    private static KnowledgeDocumentSummary toSummary(KnowledgeDocumentRow row) {
        return new KnowledgeDocumentSummary(row.id(), row.title(), row.uploaderUserId(), row.summary(),
                row.uploadedAt(), row.status().name(), "/api/knowledge/" + row.id() + "/download",
                row.scopeType().name(), scopeName(row), row.tenantId(), row.orgId());
    }

    private static String scopeName(KnowledgeDocumentRow row) {
        return switch (row.scopeType()) {
            case GLOBAL -> null;
            case TENANT -> row.tenantName();
            case ORG -> row.orgName();
        };
    }

    record ApproveRequest(String summary) {}

    record KnowledgeDocumentSummary(String id, String title, String uploaderUserId, String summary,
            LocalDateTime uploadedAt, String status, String downloadUrl, String scopeType,
            String scopeName, String tenantId, String orgId) {}
}
