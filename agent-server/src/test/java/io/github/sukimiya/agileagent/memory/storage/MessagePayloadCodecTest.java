package io.github.sukimiya.agileagent.memory.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.micrometer.core.instrument.Metrics;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

/** Encoding and integrity of compressed stored text (user decisions 2026-10-30 points 1 and 5). */
class MessagePayloadCodecTest {

    private final SimpleMeterRegistry registry = new SimpleMeterRegistry();

    @BeforeEach
    void setUp() {
        Metrics.addRegistry(registry);
    }

    @AfterEach
    void tearDown() {
        Metrics.removeRegistry(registry);
    }

    private static final String TEXT = "{\"role\":\"user\",\"content\":\"" + "下周四下午三点在西湖文化广场北门见。".repeat(50)
            + "\"}";

    @Test
    void plainRowsAreReturnedAsIs() {
        var stored = new MessagePayloadCodec.Stored(TEXT, null, null, null, null);
        assertThat(MessagePayloadCodec.decode(MessagePayloadCodec.Field.MESSAGE_PAYLOAD, stored)).isEqualTo(TEXT);
        assertThat(MessagePayloadCodec.decode(MessagePayloadCodec.Field.TOOL_RESULT,
                new MessagePayloadCodec.Stored(null, null, null, null, null))).isNull();
    }

    @Test
    void inlineZstdRoundTripsAtBothLevelsWithFrameChecksumAndSha256() {
        for (int level : new int[] {3, 19}) {
            var enc = MessagePayloadCodec.encode(TEXT, level);
            assertThat(enc.data().length).isLessThan(TEXT.getBytes(StandardCharsets.UTF_8).length);
            assertThat(enc.sha256()).hasSize(64).isEqualTo(MessagePayloadCodec.sha256(TEXT));
            assertThat(MessagePayloadCodec.hasFrameChecksum(enc.data())).isTrue();
            var stored = new MessagePayloadCodec.Stored(null, MessagePayloadCodec.CODEC_ZSTD, enc.data(), enc.sha256(),
                    null);
            assertThat(MessagePayloadCodec.decode(MessagePayloadCodec.Field.MESSAGE_PAYLOAD, stored)).isEqualTo(TEXT);
        }
    }

    @Test
    void sharedBlobRowsDecodeFromTheJoinedBlob() {
        var enc = MessagePayloadCodec.encode(TEXT, 3);
        var stored = new MessagePayloadCodec.Stored(null, MessagePayloadCodec.CODEC_REF, null, enc.sha256(), enc.data());
        assertThat(MessagePayloadCodec.decode(MessagePayloadCodec.Field.TOOL_RESULT, stored)).isEqualTo(TEXT);
    }

    @Test
    void shaMismatchIsAnErrorAndCountedNeverCorruptText() {
        var enc = MessagePayloadCodec.encode(TEXT, 3);
        var wrongHash = MessagePayloadCodec.sha256("something else");
        var stored = new MessagePayloadCodec.Stored(null, MessagePayloadCodec.CODEC_ZSTD, enc.data(), wrongHash, null);
        assertThatThrownBy(() -> MessagePayloadCodec.decode(MessagePayloadCodec.Field.MESSAGE_PAYLOAD, stored))
                .isInstanceOf(CorruptStoredPayloadException.class).hasMessageNotContaining("西湖");
        assertThat(registry.get("memory.storage.integrity.failures").tag("field", "payload").counter().count())
                .isEqualTo(1.0);
    }

    @Test
    void damagedFrameIsDetectedByTheZstdChecksum() {
        var enc = MessagePayloadCodec.encode(TEXT, 19);
        var data = enc.data().clone();
        data[data.length / 2] ^= 0x5A;
        var stored = new MessagePayloadCodec.Stored(null, MessagePayloadCodec.CODEC_ZSTD, data, enc.sha256(), null);
        assertThatThrownBy(() -> MessagePayloadCodec.decode(MessagePayloadCodec.Field.TOOL_ARGUMENTS, stored))
                .isInstanceOf(CorruptStoredPayloadException.class);
        assertThat(registry.get("memory.storage.integrity.failures").tag("field", "arguments").counter().count())
                .isEqualTo(1.0);
    }

    @Test
    void missingSharedBlobOrUnknownCodecIsAnError() {
        var enc = MessagePayloadCodec.encode(TEXT, 3);
        assertThatThrownBy(() -> MessagePayloadCodec.decode(MessagePayloadCodec.Field.MESSAGE_PAYLOAD,
                new MessagePayloadCodec.Stored(null, MessagePayloadCodec.CODEC_REF, null, enc.sha256(), null)))
                .isInstanceOf(CorruptStoredPayloadException.class);
        assertThatThrownBy(() -> MessagePayloadCodec.decode(MessagePayloadCodec.Field.MESSAGE_PAYLOAD,
                new MessagePayloadCodec.Stored(null, "lz4", enc.data(), enc.sha256(), null)))
                .isInstanceOf(CorruptStoredPayloadException.class);
    }

    @Test
    void selectAndJoinNameTheFieldColumns() {
        assertThat(MessagePayloadCodec.select(MessagePayloadCodec.Field.MESSAGE_PAYLOAD, "m"))
                .contains("m.payload", "m.payload_codec", "m.payload_blob", "m.payload_sha256");
        assertThat(MessagePayloadCodec.join(MessagePayloadCodec.Field.TOOL_RESULT, "e"))
                .contains("LEFT JOIN message_blobs").contains("e.result_sha256").contains("e.tenant_id");
    }
}
