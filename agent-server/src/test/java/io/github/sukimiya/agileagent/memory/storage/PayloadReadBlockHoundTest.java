package io.github.sukimiya.agileagent.memory.storage;

import java.time.Duration;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import io.github.sukimiya.agileagent.memory.storage.MessagePayloadCodec.Field;
import io.github.sukimiya.agileagent.memory.storage.MessagePayloadCodec.Stored;
import reactor.blockhound.BlockHound;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import reactor.test.StepVerifier;

/**
 * Transparent reads of compressed text decode on the calling Reactor thread; BlockHound proves that step never
 * blocks (the native library is loaded at startup by {@link MessageCompressor}, as here in {@link #setUp}).
 */
@Tag("blockhound")
class PayloadReadBlockHoundTest {

    @BeforeAll
    static void setUp() {
        MessagePayloadCodec.encode("warm up the native library", 3);
        BlockHound.install();
    }

    @Test
    void readPathNonBlocking() {
        var text = "{\"role\":\"user\",\"content\":\"" + "压缩后透明读取 ".repeat(1000) + "\"}";
        var inline = MessagePayloadCodec.encode(text, 3);
        var shared = MessagePayloadCodec.encode(text, 19);

        StepVerifier.create(Mono.fromCallable(() -> MessagePayloadCodec.decode(Field.MESSAGE_PAYLOAD,
                                new Stored(null, MessagePayloadCodec.CODEC_ZSTD, inline.data(), inline.sha256(), null))
                        + MessagePayloadCodec.decode(Field.TOOL_RESULT,
                                new Stored(null, MessagePayloadCodec.CODEC_REF, null, shared.sha256(), shared.data())))
                        .subscribeOn(Schedulers.parallel()))
                .expectNext(text + text)
                .verifyComplete();
    }

    @Test
    void blockHoundIsActive() {
        StepVerifier.create(Mono.fromCallable(() -> {
                    Thread.sleep(1);
                    return 0;
                }).subscribeOn(Schedulers.parallel()))
                .expectError(reactor.blockhound.BlockingOperationError.class)
                .verify(Duration.ofSeconds(5));
    }
}
