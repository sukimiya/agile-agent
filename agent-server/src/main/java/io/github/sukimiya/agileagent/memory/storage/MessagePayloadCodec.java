package io.github.sukimiya.agileagent.memory.storage;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

import com.github.luben.zstd.Zstd;
import com.github.luben.zstd.ZstdCompressCtx;
import com.github.luben.zstd.ZstdDecompressCtx;

import io.github.sukimiya.agileagent.memory.MemoryProperties;
import io.micrometer.core.instrument.Metrics;
import io.r2dbc.spi.Readable;

/**
 * The single reader and writer of compressed stored text (M2b Task 9, user decisions 2026-10-30 points 4 and 5).
 * <ul>
 * <li>Every SQL statement that reads {@code chat_messages.payload} or the {@code chat_tool_events}
 * {@code arguments} / {@code result} selects {@link #select} with {@link #join} and restores the text with
 * {@link #decode}; {@code PayloadReadGuardTest} rejects any other read.</li>
 * <li>A field is plain ({@code <field>_codec IS NULL}), inline zstd ({@value #CODEC_ZSTD}: data in
 * {@code <field>_blob}) or shared ({@value #CODEC_REF}: data in {@code message_blobs} of the same tenant, keyed by
 * {@code <field>_sha256}).</li>
 * <li>Compressed data carries the zstd frame checksum and the row keeps the SHA-256 of the plain text; both are
 * verified on every read. A failure throws {@link CorruptStoredPayloadException} and counts
 * {@code memory.storage.integrity.failures{field}}; damaged text is never returned.</li>
 * </ul>
 * Decompression is a bounded in-memory CPU step and runs on the calling Reactor thread.
 */
public final class MessagePayloadCodec {

    public static final String CODEC_ZSTD = "zstd";
    public static final String CODEC_REF = "ref";
    public static final String TIER_WARM = "WARM";
    public static final String TIER_COLD = "COLD";

    /** A compressible text column. */
    public enum Field {
        MESSAGE_PAYLOAD("chat_messages", "payload", "created_at"),
        TOOL_ARGUMENTS("chat_tool_events", "arguments", "started_at"),
        TOOL_RESULT("chat_tool_events", "result", "started_at");

        private final String table;
        private final String column;
        private final String createdColumn;

        Field(String table, String column, String createdColumn) {
            this.table = table;
            this.column = column;
            this.createdColumn = createdColumn;
        }

        public String table() { return table; }
        public String column() { return column; }
        /** The creation time the age of the field is counted from. */
        public String createdColumn() { return createdColumn; }
        public String codecColumn() { return column + "_codec"; }
        public String tierColumn() { return column + "_tier"; }
        public String blobColumn() { return column + "_blob"; }
        public String shaColumn() { return column + "_sha256"; }
        String sharedAlias(String alias) { return alias + "_" + column + "_sb"; }
        String sharedColumn() { return column + "_shared"; }
    }

    /** The stored columns of one field (shared data from the joined {@code message_blobs} row). */
    public record Stored(String plain, String codec, byte[] blob, String sha256, byte[] shared) {}

    /** Compressed text and the SHA-256 (hex) of its plain UTF-8 bytes. */
    public record Encoded(byte[] data, String sha256, int plainBytes) {}

    private MessagePayloadCodec() {}

    /** Columns to select for {@code field} of the table aliased {@code alias}; use with {@link #join}. */
    public static String select(Field field, String alias) {
        return alias + "." + field.column() + ", " + alias + "." + field.codecColumn() + ", " + alias + "."
                + field.blobColumn() + ", " + alias + "." + field.shaColumn() + ", " + field.sharedAlias(alias)
                + ".data AS " + field.sharedColumn();
    }

    /** The join of the shared blob of {@code field}, same tenant only. */
    public static String join(Field field, String alias) {
        var sb = field.sharedAlias(alias);
        return " LEFT JOIN message_blobs " + sb + " ON " + alias + "." + field.codecColumn() + " = '" + CODEC_REF
                + "' AND " + sb + ".tenant_id = " + alias + ".tenant_id AND " + sb + ".content_hash = " + alias + "."
                + field.shaColumn();
    }

    /** {@link #select} of {@code chat_messages.payload}. */
    public static String messageColumns(String alias) {
        return select(Field.MESSAGE_PAYLOAD, alias);
    }

    /** {@link #join} of {@code chat_messages.payload}. */
    public static String messageJoin(String alias) {
        return join(Field.MESSAGE_PAYLOAD, alias);
    }

    /** The plain {@code chat_messages.payload} JSON of a row selected with {@link #messageColumns}. */
    public static String messageText(Readable row) {
        return text(row, Field.MESSAGE_PAYLOAD);
    }

    /** The stored columns of {@code field} in a row selected with {@link #select}. */
    public static Stored stored(Readable row, Field field) {
        return new Stored(row.get(field.column(), String.class), row.get(field.codecColumn(), String.class),
                bytes(row.get(field.blobColumn())), row.get(field.shaColumn(), String.class),
                bytes(row.get(field.sharedColumn())));
    }

    /** The plain text of {@code field} in a row selected with {@link #select} (null for an empty tool field). */
    public static String text(Readable row, Field field) {
        return decode(field, stored(row, field));
    }

    static byte[] bytes(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof byte[] b) {
            return b;
        }
        if (value instanceof ByteBuffer buffer) {
            var copy = new byte[buffer.remaining()];
            buffer.duplicate().get(copy);
            return copy;
        }
        throw new IllegalStateException("unsupported blob value " + value.getClass().getName());
    }

    /** Restores the plain text, verifying the zstd frame checksum and the stored SHA-256. */
    public static String decode(Field field, Stored stored) {
        if (stored.codec() == null) {
            if (stored.plain() == null && field == Field.MESSAGE_PAYLOAD) {
                throw corrupt(field.column(), "neither plain text nor compressed data");
            }
            return stored.plain();
        }
        byte[] data = switch (stored.codec()) {
            case CODEC_ZSTD -> stored.blob();
            case CODEC_REF -> stored.shared();
            default -> throw corrupt(field.column(), "unknown codec");
        };
        return decompress(field.column(), data, stored.sha256());
    }

    /** Restores the text of a shared {@code message_blobs} row (same checks as {@link #decode}). */
    public static String decodeShared(byte[] data, String sha256) {
        return decompress("message_blobs", data, sha256);
    }

    private static String decompress(String field, byte[] data, String sha256) {
        if (data == null || sha256 == null) {
            throw corrupt(field, "missing compressed data or hash");
        }
        byte[] plain;
        try {
            long size = Zstd.getFrameContentSize(data);
            if (size < 0 || size > MemoryProperties.MEDIUMTEXT_MAX_BYTES) {
                throw corrupt(field, "invalid frame size");
            }
            try (var ctx = new ZstdDecompressCtx()) {
                plain = ctx.decompress(data, (int) size);
            }
        } catch (CorruptStoredPayloadException e) {
            throw e;
        } catch (RuntimeException e) {
            throw corrupt(field, "damaged zstd frame (" + e.getClass().getSimpleName() + ")");
        }
        if (!sha256(plain).equals(sha256)) {
            throw corrupt(field, "SHA-256 mismatch");
        }
        return new String(plain, StandardCharsets.UTF_8);
    }

    private static CorruptStoredPayloadException corrupt(String field, String reason) {
        Metrics.counter("memory.storage.integrity.failures", "field", field).increment();
        return new CorruptStoredPayloadException(field, reason);
    }

    /** zstd at {@code level}, with content size and frame checksum. */
    public static Encoded encode(String text, int level) {
        var plain = text.getBytes(StandardCharsets.UTF_8);
        return new Encoded(compress(plain, level), sha256(plain), plain.length);
    }

    /** Recompresses already compressed data (verified first) at {@code level}. */
    public static byte[] recompress(Field field, byte[] data, String sha256, int level) {
        var text = decode(field, new Stored(null, CODEC_ZSTD, data, sha256, null));
        return compress(text.getBytes(StandardCharsets.UTF_8), level);
    }

    private static byte[] compress(byte[] plain, int level) {
        try (var ctx = new ZstdCompressCtx()) {
            ctx.setLevel(level);
            ctx.setChecksum(true);
            ctx.setContentSize(true);
            return ctx.compress(plain);
        }
    }

    /** Whether a zstd frame declares a content checksum (frame header descriptor bit 2). */
    static boolean hasFrameChecksum(byte[] frame) {
        return frame.length > 4 && (frame[4] & 0x04) != 0;
    }

    public static String sha256(String text) {
        return sha256(text.getBytes(StandardCharsets.UTF_8));
    }

    static String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
