package io.github.sukimiya.agileagent.controller;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;

import io.github.sukimiya.agileagent.config.UploadProperties;
import io.github.sukimiya.agileagent.memory.ConversationIds;
import io.github.sukimiya.agileagent.memory.MemoryException;
import io.github.sukimiya.agileagent.memory.MemoryScope;
import io.github.sukimiya.agileagent.service.AuthSessionStore;
import io.github.sukimiya.agileagent.service.FileStorageService;
import io.github.sukimiya.agileagent.service.FileStorageService.RejectedException;
import io.github.sukimiya.agileagent.service.SessionService;
import io.github.sukimiya.agileagent.web.CurrentUser;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

/**
 * Attachment upload and download, both scoped to a conversation the caller owns. Order of checks: canonical
 * conversation id (400 {@code INVALID_CONVERSATION_ID}), tenant scope (403 {@code TENANT_REQUIRED}), ownership
 * (404 {@code CONVERSATION_NOT_FOUND}; upload creates the caller's empty conversation when the id is unused),
 * then the file itself. Responses and error bodies never carry server paths or exception messages.
 */
@RestController
@RequestMapping("/api/files")
@CrossOrigin(origins = "*")
class UploadController {

    private static final Logger log = LoggerFactory.getLogger(UploadController.class);

    /** One uploaded file: the reference needed to download it again ({@code sessionId} + {@code name}). */
    record UploadedFile(String sessionId, String name, long size) {}

    private final FileStorageService storage;
    private final UploadProperties props;
    private final SessionService sessionService;

    UploadController(FileStorageService storage, UploadProperties props, SessionService sessionService) {
        this.storage = storage;
        this.props = props;
        this.sessionService = sessionService;
    }

    @GetMapping("/download")
    Mono<ResponseEntity<?>> download(
            @RequestParam("sessionId") String sessionId,
            @RequestParam("name") String name,
            @CurrentUser AuthSessionStore.AuthUser user) {
        return Mono.defer(() -> {
                    var conversationId = ConversationIds.validate(sessionId);
                    var scope = MemoryScope.of(user.identity());
                    return sessionService.findOwned(conversationId, scope)
                            .map(owned -> conversationId);
                })
                .flatMap(conversationId -> Mono.<ResponseEntity<?>>fromCallable(() -> serve(conversationId, name))
                        .subscribeOn(Schedulers.boundedElastic()))
                .switchIfEmpty(Mono.fromSupplier(() -> notFound()));
    }

    private ResponseEntity<?> serve(String conversationId, String name) {
        final Path path;
        try {
            var located = storage.locate(conversationId, name);
            if (located.isEmpty()) {
                return notFound();
            }
            path = located.get();
        } catch (RejectedException e) {
            return ResponseEntity.badRequest().body(Map.of("code", e.getCode()));
        }
        var encoded = URLEncoder.encode(name, StandardCharsets.UTF_8).replace("+", "%20");
        var contentType = MediaType.APPLICATION_OCTET_STREAM_VALUE;
        try {
            var probed = Files.probeContentType(path);
            if (probed != null) contentType = probed;
        } catch (IOException ignored) {}
        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION,
                    "attachment; filename*=UTF-8''" + encoded)
            .header(HttpHeaders.CONTENT_TYPE, contentType)
            .body(new FileSystemResource(path));
    }

    /** Unknown, foreign, deleted conversation or missing file: one indistinguishable answer. */
    private static ResponseEntity<?> notFound() {
        return ResponseEntity.status(404).body(Map.of("code", "CONVERSATION_NOT_FOUND",
                "message", "conversation or file not found"));
    }

    @PostMapping("/upload")
    Mono<ResponseEntity<Object>> upload(
            @RequestPart("sessionId") String sessionId,
            @RequestPart("files") Flux<FilePart> files,
            @CurrentUser AuthSessionStore.AuthUser user) {
        return Mono.defer(() -> {
                    var conversationId = ConversationIds.validate(sessionId);
                    var scope = MemoryScope.of(user.identity());
                    return sessionService.createOwned(conversationId, scope, null).thenReturn(conversationId);
                })
                .flatMap(conversationId -> files.collectList().flatMap(list -> {
                    if (list.size() > props.getMaxFilesPerRequest()) {
                        return Mono.just(ResponseEntity.badRequest().body((Object) Map.of(
                                "code", "FILE_REJECTED",
                                "reason", "exceeds max files per request: " + props.getMaxFilesPerRequest())));
                    }
                    return Flux.fromIterable(list)
                            .concatMap(f -> storage.save(conversationId, f)
                                    .map(ref -> new UploadedFile(conversationId, ref.name(), ref.size()))
                                    .onErrorMap(RejectedException.class, e -> new PerFileRejected(e, f.filename())))
                            .collectList()
                            .map(refs -> ResponseEntity.ok((Object) refs));
                }))
                .onErrorResume(PerFileRejected.class, e -> Mono.just(ResponseEntity.badRequest().body((Object) Map.of(
                        "code", e.cause.getCode(), "reason", e.cause.getMessage(), "file", e.filename))))
                .onErrorResume(UploadController::isStorageFailure, e -> {
                    // The cause (often naming server paths) goes to the server log only.
                    log.warn("attachment storage failed errorType={}", e.getClass().getSimpleName(), e);
                    return Mono.just(ResponseEntity.internalServerError().body((Object) Map.of(
                            "code", "STORAGE_ERROR", "reason", "附件保存失败，请稍后重试")));
                });
    }

    private static boolean isStorageFailure(Throwable e) {
        return !(e instanceof MemoryException) && (e instanceof IOException || e instanceof UncheckedIOException
                || e.getCause() instanceof IOException || e instanceof IllegalStateException);
    }

    private static final class PerFileRejected extends RuntimeException {
        final RejectedException cause;
        final String filename;
        PerFileRejected(RejectedException cause, String filename) {
            super(cause.getMessage(), cause);
            this.cause = cause;
            this.filename = filename;
        }
    }
}
