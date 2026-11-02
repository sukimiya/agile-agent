package io.github.sukimiya.agileagent.memory.storage;

/**
 * Compressed stored text that cannot be restored exactly: missing data, an unknown codec, a damaged zstd frame
 * (frame checksum) or a plain-text SHA-256 mismatch. Never carries any of the text.
 */
public class CorruptStoredPayloadException extends IllegalStateException {

    private static final long serialVersionUID = 1L;

    public CorruptStoredPayloadException(String field, String reason) {
        super("stored " + field + " cannot be restored: " + reason);
    }
}
