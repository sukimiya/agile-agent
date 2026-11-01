package io.github.sukimiya.agileagent.memory.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;

import org.junit.jupiter.api.Test;

import io.github.sukimiya.agileagent.memory.MemoryProperties;

/** {@code app.memory.storage.*} (M2b Task 9, user decisions 2026-10-30 point 7): defaults and startup validation. */
class StoragePropertiesTest {

    @Test
    void defaultsAreOffWithSevenAndNinetyDaysAndLevelsThreeAndNineteen() {
        var s = new MemoryProperties().getStorage();
        assertThat(s.isEnabled()).isFalse();
        assertThat(s.isDecompress()).isFalse();
        assertThat(s.getCompressAfter()).isEqualTo(Duration.ofDays(7));
        assertThat(s.getRecompressAfter()).isEqualTo(Duration.ofDays(90));
        assertThat(s.getWarmLevel()).isEqualTo(3);
        assertThat(s.getColdLevel()).isEqualTo(19);
        assertThat(s.getDedupeMinBytes()).isEqualTo(4096);
        assertThat(s.getBatch()).isPositive();
        assertThat(s.getInterval()).isPositive();
        assertThat(s.getRecallCacheTtl()).isEqualTo(Duration.ofHours(1));
        s.validate();
    }

    @Test
    void zeroAgesAreAllowedForTheEval() {
        var s = new MemoryProperties().getStorage();
        s.setEnabled(true);
        s.setCompressAfter(Duration.ZERO);
        s.setRecompressAfter(Duration.ZERO);
        s.validate();
    }

    @Test
    void invalidValuesAreRejected() {
        var s = new MemoryProperties().getStorage();
        assertThatThrownBy(() -> s.setCompressAfter(Duration.ofSeconds(-1))).hasMessageContaining("compress-after");
        assertThatThrownBy(() -> s.setRecompressAfter(null)).hasMessageContaining("recompress-after");
        assertThatThrownBy(() -> s.setDedupeMinBytes(0)).hasMessageContaining("dedupe-min-bytes");
        assertThatThrownBy(() -> s.setBatch(0)).hasMessageContaining("batch");
        assertThatThrownBy(() -> s.setInterval(Duration.ZERO)).hasMessageContaining("interval");
        assertThatThrownBy(() -> s.setRecallCacheTtl(Duration.ZERO)).hasMessageContaining("recall-cache-ttl");
    }

    @Test
    void recompressAfterMustNotBeShorterThanCompressAfter() {
        var s = new MemoryProperties().getStorage();
        s.setCompressAfter(Duration.ofDays(30));
        s.setRecompressAfter(Duration.ofDays(7));
        assertThatThrownBy(s::validate).hasMessageContaining("recompress-after").hasMessageContaining("compress-after");
    }

    @Test
    void levelsMustBeValidZstdLevelsWhenEnabled() {
        var s = new MemoryProperties().getStorage();
        s.setWarmLevel(23);
        // Off: the native library is not loaded at startup, levels are checked once the feature is on.
        s.validate();
        s.setEnabled(true);
        assertThatThrownBy(s::validate).hasMessageContaining("warm-level");
        s.setWarmLevel(3);
        s.setColdLevel(-200_000);
        assertThatThrownBy(s::validate).hasMessageContaining("cold-level");
    }

    @Test
    void decompressRequiresEnabled() {
        var s = new MemoryProperties().getStorage();
        s.setDecompress(true);
        assertThatThrownBy(s::validate).hasMessageContaining("decompress").hasMessageContaining("enabled");
        s.setEnabled(true);
        s.validate();
    }
}
