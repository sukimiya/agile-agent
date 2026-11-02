package io.github.sukimiya.agileagent.memory.items;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import java.time.Duration;

import org.junit.jupiter.api.Test;

import io.github.sukimiya.agileagent.memory.MemoryProperties;
import io.github.sukimiya.agileagent.memory.items.MemoryItem.Assertion;

/** M2b Task 6 decay rules (D12 as revised 2026-10-27), pure: half-life weight and who may become INACTIVE. */
class DecayPolicyTest {

    static final long DAY = Duration.ofDays(1).toSeconds();

    @Test
    void weightDecaysByLastRecallHalfLife() {
        var policy = new DecayPolicy(new MemoryProperties().getDecay());

        assertThat(policy.weight(0)).isEqualTo(1.0);
        assertThat(policy.weight(30 * DAY)).isCloseTo(0.5, within(1e-9));
        assertThat(policy.weight(60 * DAY)).isCloseTo(0.25, within(1e-9));
        assertThat(policy.weight(-5)).as("clock skew never boosts").isEqualTo(1.0);
        assertThat(policy.score(0.4, 30 * DAY)).isCloseTo((DecayPolicy.ITEM_BASE_SCORE + 0.4) * 0.5, within(1e-9));
        assertThat(policy.score(null, 0)).isEqualTo(DecayPolicy.ITEM_BASE_SCORE);

        var tuned = new MemoryProperties().getDecay();
        tuned.setHalfLife(Duration.ofDays(10));
        assertThat(new DecayPolicy(tuned).weight(10 * DAY)).isCloseTo(0.5, within(1e-9));
    }

    @Test
    void onlyNonUserAssertionsBecomeInactiveAfterTheThreshold() {
        var policy = new DecayPolicy(new MemoryProperties().getDecay());

        assertThat(policy.inactivates(Assertion.INFERRED, 181 * DAY)).isTrue();
        assertThat(policy.inactivates(null, 181 * DAY)).isTrue();
        assertThat(policy.inactivates(Assertion.INFERRED, 179 * DAY)).isFalse();
        assertThat(policy.inactivates(Assertion.USER_STATED, 1_000 * DAY)).isFalse();
        assertThat(policy.inactivates(Assertion.USER_CONFIRMED, 1_000 * DAY)).isFalse();
    }

    @Test
    void settingsMustBePositive() {
        var decay = new MemoryProperties().getDecay();
        assertThat(decay.getHalfLife()).isEqualTo(Duration.ofDays(30));
        assertThat(decay.getInactiveAfter()).isEqualTo(Duration.ofDays(180));
        assertThatThrownBy(() -> decay.setHalfLife(Duration.ZERO)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> decay.setInactiveAfter(Duration.ofDays(-1)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> decay.setInterval(null)).isInstanceOf(IllegalArgumentException.class);
    }
}
