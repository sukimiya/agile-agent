package io.github.sukimiya.agileagent.memory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.jupiter.api.Test;

import io.github.sukimiya.agileagent.memory.MemoryModel.TokenUsage;
import io.github.sukimiya.agileagent.memory.MemoryModel.TurnTicket;
import reactor.core.publisher.Mono;

class TurnRecorderTest {

    final TurnTicket ticket = new TurnTicket(new MemoryScope("t1", "u1"), "c1", "turn-1", 1);
    final List<String> checkpoints = new ArrayList<>();
    final AtomicLong now = new AtomicLong();
    final MemoryProperties props = new MemoryProperties();

    TurnRecorder recorder(boolean owned) {
        return new TurnRecorder(ticket, (t, text) -> {
            checkpoints.add(text);
            return Mono.just(owned);
        }, props, now::get);
    }

    @Test
    void checkpointsAfterEightKibOfUtf8TextNotCharacters() {
        var recorder = recorder(true);
        var chunk = "记".repeat(1000); // 3000 bytes, 1000 chars

        recorder.delta(chunk).block();
        recorder.delta(chunk).block();
        assertThat(checkpoints).isEmpty();

        recorder.delta(chunk).block(); // 9000 bytes >= 8192
        assertThat(checkpoints).containsExactly(chunk.repeat(3));

        recorder.delta("x").block();
        assertThat(checkpoints).hasSize(1);
    }

    @Test
    void checkpointsAfterFiveSecondsEvenWhenSmall() {
        var recorder = recorder(true);

        recorder.delta("a").block();
        now.addAndGet(Duration.ofSeconds(5).toNanos());
        recorder.delta("b").block();

        assertThat(checkpoints).containsExactly("ab");
    }

    @Test
    void lostOwnershipStopsTheStream() {
        props.setCheckpointBytes(1);
        var recorder = recorder(false);

        assertThatThrownBy(() -> recorder.delta("a").block())
                .isInstanceOf(TurnLifecycle.ExecutionRightLostException.class);
    }

    @Test
    void completedStagesFinalTextAndUsageWithoutWriting() {
        var recorder = recorder(true);
        recorder.delta("par").block();

        recorder.completed("partial answer", new TokenUsage(true, 1, 2, 3)).block();

        assertThat(checkpoints).isEmpty();
        assertThat(recorder.isCompleted()).isTrue();
        assertThat(recorder.text()).isEqualTo("partial answer");
        assertThat(recorder.usage().totalTokens()).isEqualTo(3);
    }
}
