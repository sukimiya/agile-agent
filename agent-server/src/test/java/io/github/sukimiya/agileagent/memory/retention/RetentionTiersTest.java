package io.github.sukimiya.agileagent.memory.retention;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.io.ClassPathResource;

import io.github.sukimiya.agileagent.memory.MemoryProperties;
import io.github.sukimiya.agileagent.memory.MemoryProperties.Purge.Tier;

/** Reference-count retention tiers (M2b, Q4 redesign C): config-only, validated, resolved by one method. */
class RetentionTiersTest {

    static Tier tier(long from, Duration keep) {
        var t = new Tier();
        t.setFromCount(from);
        t.setKeep(keep);
        return t;
    }

    static MemoryProperties bind(Map<String, String> values) {
        var props = new MemoryProperties();
        new Binder(new MapConfigurationPropertySource(values)).bind("app.memory", Bindable.ofInstance(props));
        return props;
    }

    @Test
    void defaultsAreOneDayOneWeekOneMonth() {
        var tiers = RetentionTiers.of(new MemoryProperties().getPurge().getTiers());
        assertThat(tiers.retentionFor(0)).isEqualTo(Duration.ofDays(1));
        assertThat(tiers.retentionFor(1)).isEqualTo(Duration.ofDays(1));
        assertThat(tiers.retentionFor(2)).isEqualTo(Duration.ofDays(7));
        assertThat(tiers.retentionFor(4)).isEqualTo(Duration.ofDays(7));
        assertThat(tiers.retentionFor(5)).isEqualTo(Duration.ofDays(30));
        assertThat(tiers.retentionFor(10_000)).isEqualTo(Duration.ofDays(30));
        assertThat(tiers.size()).isEqualTo(3);
    }

    @Test
    void applicationYmlDeclaresTheDefaultTiers() throws Exception {
        var sources = new YamlPropertySourceLoader().load("app", new ClassPathResource("application.yml"));
        var values = new LinkedHashMap<String, String>();
        for (var source : sources) {
            if (source.getSource() instanceof Map<?, ?> map) {
                map.forEach((k, v) -> {
                    if (k.toString().startsWith("app.memory.purge.")) {
                        values.put(k.toString(), String.valueOf(v));
                    }
                });
            }
        }
        assertThat(values).containsKeys("app.memory.purge.tiers[0].from-count", "app.memory.purge.tiers[2].keep");
        var tiers = RetentionTiers.of(bind(values).getPurge().getTiers());
        assertThat(List.of(tiers.retentionFor(1), tiers.retentionFor(3), tiers.retentionFor(7)))
                .containsExactly(Duration.ofDays(1), Duration.ofDays(7), Duration.ofDays(30));
    }

    @Test
    void tierCountIsConfigOnly() {
        var five = new LinkedHashMap<String, String>();
        long[] froms = {0, 1, 3, 10, 50};
        for (int i = 0; i < froms.length; i++) {
            five.put("app.memory.purge.tiers[" + i + "].from-count", String.valueOf(froms[i]));
            five.put("app.memory.purge.tiers[" + i + "].keep", (i + 1) + "h");
        }
        var tiers = RetentionTiers.of(bind(five).getPurge().getTiers());
        assertThat(tiers.size()).isEqualTo(5);
        assertThat(tiers.retentionFor(0)).isEqualTo(Duration.ofHours(1));
        assertThat(tiers.retentionFor(2)).isEqualTo(Duration.ofHours(2));
        assertThat(tiers.retentionFor(9)).isEqualTo(Duration.ofHours(3));
        assertThat(tiers.retentionFor(49)).isEqualTo(Duration.ofHours(4));
        assertThat(tiers.retentionFor(50)).isEqualTo(Duration.ofHours(5));

        var nine = new LinkedHashMap<String, String>();
        for (int i = 0; i < 9; i++) {
            nine.put("app.memory.purge.tiers[" + i + "].from-count", String.valueOf(i * 2));
            nine.put("app.memory.purge.tiers[" + i + "].keep", (i + 1) + "d");
        }
        var nineTiers = RetentionTiers.of(bind(nine).getPurge().getTiers());
        assertThat(nineTiers.size()).isEqualTo(9);
        assertThat(nineTiers.retentionFor(17)).isEqualTo(Duration.ofDays(9));
        assertThat(nineTiers.retentionFor(15)).isEqualTo(Duration.ofDays(8));
    }

    @Test
    void invalidTiersAreRejected() {
        assertThatThrownBy(() -> RetentionTiers.of(List.of())).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("at least one");
        assertThatThrownBy(() -> RetentionTiers.of(List.of(tier(1, Duration.ofDays(1)))))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("start at 0");
        assertThatThrownBy(() -> RetentionTiers.of(List.of(tier(0, Duration.ofDays(1)), tier(5, Duration.ofDays(30)),
                tier(2, Duration.ofDays(7))))).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("ascending");
        assertThatThrownBy(() -> RetentionTiers.of(List.of(tier(0, Duration.ofDays(1)), tier(2, Duration.ofDays(7)),
                tier(2, Duration.ofDays(30))))).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("ascending");
        assertThatThrownBy(() -> RetentionTiers.of(List.of(tier(0, Duration.ZERO))))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("positive");
        assertThatThrownBy(() -> RetentionTiers.of(List.of(tier(0, null))))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("positive");
        assertThatThrownBy(() -> RetentionTiers.of(List.of(tier(0, Duration.ofDays(1)))).retentionFor(-1))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
