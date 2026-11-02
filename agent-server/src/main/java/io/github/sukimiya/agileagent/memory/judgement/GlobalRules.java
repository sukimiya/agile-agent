package io.github.sukimiya.agileagent.memory.judgement;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

/**
 * Versioned global Rules for memory judgement (M2b Task 1). Loaded only from the classpath resource
 * {@code memory/global-rules.md}; never from a request, RAG document or arbitrary filesystem path as
 * high-priority instructions. Startup fails if the resource is missing, unreadable or has no version.
 */
@Component
public class GlobalRules {

    static final String CLASSPATH_RESOURCE = "memory/global-rules.md";
    private static final Pattern VERSION = Pattern.compile("(?m)^rules-version:\\s*(\\d+)\\s*$");

    /** Immutable snapshot of the loaded Rules. */
    public record RulesVersion(int version, String text, String sha256) {}

    private final RulesVersion current;

    public GlobalRules() {
        this(loadFromClasspath().current);
    }

    GlobalRules(RulesVersion current) {
        this.current = Objects.requireNonNull(current, "current");
    }

    public RulesVersion current() {
        return current;
    }

    /** Loads and validates the classpath Rules (also used by tests without Spring). */
    public static GlobalRules loadFromClasspath() {
        try {
            var resource = new ClassPathResource(CLASSPATH_RESOURCE);
            if (!resource.exists()) {
                throw new IllegalStateException("missing classpath resource " + CLASSPATH_RESOURCE);
            }
            try (InputStream in = resource.getInputStream()) {
                return new GlobalRules(parse(new String(in.readAllBytes(), StandardCharsets.UTF_8)));
            }
        } catch (IOException e) {
            throw new IllegalStateException("cannot read classpath resource " + CLASSPATH_RESOURCE, e);
        }
    }

    /**
     * Rejected on purpose: Rules must not be loaded from an arbitrary path (documents, uploads, temp files).
     */
    public static GlobalRules loadFromPath(Path path) {
        throw new IllegalArgumentException("global Rules may only be loaded from classpath (" + CLASSPATH_RESOURCE
                + "), not from " + path);
    }

    /**
     * Rejected on purpose: a request or document body must not become the high-priority Rules.
     */
    public static GlobalRules loadFromText(int version, String text) {
        throw new UnsupportedOperationException("global Rules may only be loaded from classpath ("
                + CLASSPATH_RESOURCE + "), not from request or document text (version=" + version + ")");
    }

    static RulesVersion parse(String text) {
        if (text == null || text.isBlank()) {
            throw new IllegalStateException("global Rules text is empty");
        }
        Matcher m = VERSION.matcher(text);
        if (!m.find()) {
            throw new IllegalStateException("global Rules missing rules-version");
        }
        int version = Integer.parseInt(m.group(1));
        if (version < 1) {
            throw new IllegalStateException("global Rules version must be >= 1");
        }
        return new RulesVersion(version, text, sha256(text));
    }

    private static String sha256(String text) {
        try {
            var digest = MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
