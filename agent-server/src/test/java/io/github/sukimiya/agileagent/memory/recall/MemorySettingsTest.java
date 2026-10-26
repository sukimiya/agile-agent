package io.github.sukimiya.agileagent.memory.recall;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.github.sukimiya.agileagent.memory.MemoryScope;
import io.github.sukimiya.agileagent.memory.MemoryTestFixture;

/** The cross-conversation switch (D1=A): off by default, per (tenant, subject), revisioned. */
class MemorySettingsTest {

    static final Duration WAIT = Duration.ofSeconds(10);

    MemoryTestFixture fixture;
    MemorySettingsService settings;

    @BeforeEach
    void setUp() {
        fixture = MemoryTestFixture.h2();
        settings = new MemorySettingsService(fixture.databaseClient());
    }


    @Test
    void offByDefaultScopedAndRevisioned() {
        var alice = new MemoryScope("tenant-1", "alice");
        var ALICE = new MemoryScope("tenant-1", "ALICE");
        var otherTenant = new MemoryScope("tenant-2", "alice");

        var initial = settings.get(alice).block(WAIT);
        assertThat(initial.crossConversationEnabled()).isFalse();
        assertThat(initial.revision()).isZero();

        var on = settings.update(alice, true).block(WAIT);
        assertThat(on.crossConversationEnabled()).isTrue();
        assertThat(on.revision()).isEqualTo(1);
        assertThat(settings.update(alice, true).block(WAIT).revision()).isEqualTo(2);
        assertThat(settings.crossConversationEnabled(alice).block(WAIT)).isTrue();
        assertThat(settings.crossConversationEnabled(ALICE).block(WAIT)).isFalse();
        assertThat(settings.crossConversationEnabled(otherTenant).block(WAIT)).isFalse();
        assertThat(settings.update(alice, false).block(WAIT).crossConversationEnabled()).isFalse();
    }
}
