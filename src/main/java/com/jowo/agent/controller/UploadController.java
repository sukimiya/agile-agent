package com.jowo.agent.controller;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

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

import com.jowo.agent.config.UploadProperties;
import com.jowo.agent.service.AuthSessionStore;
import com.jowo.agent.service.FileStorageService;
import com.jowo.agent.service.FileStorageService.RejectedException;
import com.jowo.agent.web.CurrentUser;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

@RestController
@RequestMapping("/api/files")
@CrossOrigin(origins = "*")
class UploadController {

    private final FileStorageService storage;
    private final UploadProperties props;

    UploadController(FileStorageService storage, UploadProperties props) {
        this.storage = storage;
        this.props = props;
    }

    @GetMapping("/download")
    Mono<ResponseEntity<?>> download(
            @RequestParam("sessionId") String sessionId,
            @RequestParam("name") String name,
            @CurrentUser AuthSessionStore.AuthUser user) {
        return Mono.<ResponseEntity<?>>fromCallable(() -> doDownload(sessionId, name))
                .subscribeOn(Schedulers.boundedElastic());
    }

    private ResponseEntity<?> doDownload(String sessionId, String name) {
        if (sessionId.contains("/") || sessionId.contains("\\") || sessionId.contains("..")
                || name.contains("/") || name.contains("\\") || name.contains("..")) {
            return ResponseEntity.badRequest().body(Map.of("code", "INVALID_PATH"));
        }
        var path = Path.of(props.getDir(), sessionId, name);
        if (!Files.isRegularFile(path)) {
            return ResponseEntity.notFound().build();
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

    @PostMapping("/upload")
    Mono<ResponseEntity<Object>> upload(
            @RequestPart("sessionId") String sessionId,
            @RequestPart("files") Flux<FilePart> files,
            @CurrentUser AuthSessionStore.AuthUser user) {
        return files.collectList().flatMap(list -> {
            if (list.size() > props.getMaxFilesPerRequest()) {
                return Mono.just(ResponseEntity.badRequest().body((Object) Map.of(
                        "code", "FILE_REJECTED",
                        "reason", "exceeds max files per request: " + props.getMaxFilesPerRequest())));
            }
            return Flux.fromIterable(list)
                    .concatMap(f -> storage.save(sessionId, f)
                            .onErrorMap(RejectedException.class, e -> new PerFileRejected(e, f.filename())))
                    .collectList()
                    .map(refs -> ResponseEntity.ok((Object) refs));
        })
        .onErrorResume(PerFileRejected.class, e -> Mono.just(ResponseEntity.badRequest().body((Object) Map.of(
                "code", e.cause.getCode(), "reason", e.cause.getMessage(), "file", e.filename))))
        .onErrorResume(IOException.class, e -> Mono.just(ResponseEntity.internalServerError().body((Object) Map.of(
                "code", "STORAGE_ERROR", "reason", e.getMessage()))));
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
