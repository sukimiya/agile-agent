package io.github.sukimiya.agileagent.service;

import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import io.github.sukimiya.agileagent.config.CosProperties;
import io.github.sukimiya.agileagent.service.FileStorageService.RejectedException;

import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

/**
 * Mirrors uploaded chat images to the COS bucket and returns the plain object URLs for the model call. The
 * bucket is public-read by design (only writes are signed), so the URLs never expire and are persisted with
 * the user message. Object keys are deterministic, ASCII-safe and carry no user data beyond the conversation
 * id: {@code {prefix}/{conversationId}/{sha256(name)[:32]}{ext}}.
 */
@Service
public class ChatImageStore {

    private static final Logger log = LoggerFactory.getLogger(ChatImageStore.class);
    private static final Set<String> IMAGE_EXTENSIONS = Set.of("jpg", "jpeg", "png", "gif", "webp");
    private static final java.util.concurrent.atomic.AtomicBoolean DISABLED_LOGGED =
            new java.util.concurrent.atomic.AtomicBoolean();

    private final CosProperties props;
    private final CosClient cos;
    private final FileStorageService storage;

    public ChatImageStore(CosProperties props, CosClient cos, FileStorageService storage) {
        this.props = props;
        this.cos = cos;
        this.storage = storage;
    }

    public boolean enabled() {
        return props.enabled();
    }

    /**
     * The COS object key of an attachment: {@code prefix/conversationId/sha256(name)[:32]}{ext} where the
     * extension is a lowercase {@code .xxx} suffix of the name, kept only when it is 1–5 alphanumerics.
     */
    public static String key(String prefix, String conversationId, String name) {
        String hash;
        try {
            var digest = MessageDigest.getInstance("SHA-256")
                    .digest(name.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            var sb = new StringBuilder(32);
            for (int i = 0; i < 16; i++) {
                sb.append(Character.forDigit((digest[i] >> 4) & 0xF, 16))
                        .append(Character.forDigit(digest[i] & 0xF, 16));
            }
            hash = sb.toString();
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
        return prefix + "/" + conversationId + "/" + hash + extension(name);
    }

    private static String extension(String name) {
        var dot = name.lastIndexOf('.');
        if (dot < 0) return "";
        var ext = name.substring(dot).toLowerCase(Locale.ROOT);
        return ext.matches("\\.[a-z0-9]{1,5}$") ? ext : "";
    }

    /**
     * Uploads the stored file to COS when COS is configured and {@code contentType} is {@code image/*};
     * anything else is a no-op. Failures propagate (the upload endpoint maps them to {@code STORAGE_ERROR}).
     */
    public Mono<Void> mirror(String conversationId, String name, Path file, String contentType) {
        if (!props.enabled() || contentType == null
                || !contentType.toLowerCase(Locale.ROOT).startsWith("image/")) {
            return Mono.empty();
        }
        return Mono.fromCallable(() -> Files.readAllBytes(file))
                .subscribeOn(Schedulers.boundedElastic())
                .flatMap(bytes -> cos.put(key(props.getKeyPrefix(), conversationId, name), bytes, contentType))
                .onErrorMap(e -> !(e instanceof UncheckedIOException) && !(e instanceof java.io.IOException),
                        e -> new UncheckedIOException(
                                e instanceof java.io.IOException i ? i
                                        : new java.io.IOException("COS mirror failed: "
                                                + e.getClass().getSimpleName())));
    }

    /**
     * One object URL per stored image name, in order. Missing files, invalid names and non-image extensions
     * are skipped (debug log); without COS configuration the result is empty (info, once).
     */
    public List<String> imageUrls(String conversationId, List<String> names) {
        if (!props.enabled()) {
            if (DISABLED_LOGGED.compareAndSet(false, true)) {
                log.info("COS is not configured (app.cos.*); chat images will not be sent to the model");
            }
            return List.of();
        }
        var urls = new ArrayList<String>();
        for (var name : names) {
            var ext = extension(name);
            if (ext.isEmpty() || !IMAGE_EXTENSIONS.contains(ext.substring(1))) {
                log.debug("skipping non-image attachment for model vision name={}", name);
                continue;
            }
            try {
                if (storage.locate(conversationId, name).isEmpty()) {
                    log.debug("skipping attachment not found name={}", name);
                    continue;
                }
            } catch (RejectedException e) {
                log.debug("skipping invalid attachment name={} code={}", name, e.getCode());
                continue;
            }
            urls.add(props.getEndpoint() + "/"
                    + key(props.getKeyPrefix(), conversationId, name));
        }
        return urls;
    }
}
