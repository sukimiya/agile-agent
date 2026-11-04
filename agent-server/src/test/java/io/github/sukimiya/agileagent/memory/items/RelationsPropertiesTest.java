package io.github.sukimiya.agileagent.memory.items;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.junit.jupiter.api.Test;

import io.github.sukimiya.agileagent.memory.MemoryProperties;

/** {@code app.memory.relations.*} (relation write path, user decisions 2026-11-04): defaults and startup checks. */
class RelationsPropertiesTest {

    @Test
    void defaultsFollowTheAcceptedDesign() {
        var r = new MemoryProperties().getRelations();
        assertThat(r.isEnabled()).isTrue();
        assertThat(r.getTypes()).containsExactly("负责", "属于", "使用", "采用", "位于", "拥有", "合作", "偏好");
        assertThat(r.getEntityTypes()).containsExactly("PERSON", "PROJECT", "ORG", "PLACE", "PRODUCT", "TOPIC",
                "OTHER");
        assertThat(r.getMaxPerItem()).isEqualTo(3);
        assertThat(r.getMaxEntityNameChars()).isEqualTo(64);
        assertThat(r.getEntityMatchMinChars()).isEqualTo(2);
        assertThat(r.getEntityScanLimit()).isEqualTo(500);
        assertThat(r.getMaxRecalled()).isEqualTo(10);
        assertThat(r.getFirstPersonWords()).containsExactly("我", "我的", "本人");
        r.validate();
    }

    @Test
    void numbersMustBePositive() {
        var r = new MemoryProperties().getRelations();
        assertThatThrownBy(() -> r.setMaxPerItem(0)).hasMessageContaining("app.memory.relations.max-per-item");
        assertThatThrownBy(() -> r.setMaxEntityNameChars(0))
                .hasMessageContaining("app.memory.relations.max-entity-name-chars");
        assertThatThrownBy(() -> r.setEntityMatchMinChars(0))
                .hasMessageContaining("app.memory.relations.entity-match-min-chars");
        assertThatThrownBy(() -> r.setEntityScanLimit(-1))
                .hasMessageContaining("app.memory.relations.entity-scan-limit");
        assertThatThrownBy(() -> r.setMaxRecalled(0)).hasMessageContaining("app.memory.relations.max-recalled");
    }

    @Test
    void vocabulariesMustBeNonEmptyDistinctAndFitTheColumns() {
        var r = new MemoryProperties().getRelations();
        assertThatThrownBy(() -> r.setTypes(List.of())).hasMessageContaining("app.memory.relations.types");
        assertThatThrownBy(() -> r.setTypes(List.of("负责", " "))).hasMessageContaining("blank");
        assertThatThrownBy(() -> r.setTypes(List.of("负责", "负责"))).hasMessageContaining("duplicate");
        assertThatThrownBy(() -> r.setTypes(List.of("x".repeat(65)))).hasMessageContaining("64");
        assertThatThrownBy(() -> r.setEntityTypes(List.of())).hasMessageContaining("app.memory.relations.entity-types");
        assertThatThrownBy(() -> r.setEntityTypes(List.of("PERSON", "SELF")))
                .as("SELF is reserved for the user-self entity").hasMessageContaining("SELF");
        assertThatThrownBy(() -> r.setFirstPersonWords(List.of("我", "")))
                .hasMessageContaining("app.memory.relations.first-person-words");
        r.setTypes(List.of(" 负责 ", "使用"));
        assertThat(r.getTypes()).containsExactly("负责", "使用");
    }

    @Test
    void entityNameLimitsAreCheckedTogether() {
        var r = new MemoryProperties().getRelations();
        r.setEntityMatchMinChars(10);
        r.setMaxEntityNameChars(5);
        assertThatThrownBy(r::validate).hasMessageContaining("entity-match-min-chars")
                .hasMessageContaining("max-entity-name-chars");
        var wide = new MemoryProperties().getRelations();
        assertThatThrownBy(() -> wide.setMaxEntityNameChars(513)).as("memory_entities.name is VARCHAR(512)")
                .hasMessageContaining("512");
    }

    @Test
    void theOldProfileSettingIsGone() {
        assertThat(java.util.Arrays.stream(MemoryProperties.Profile.class.getDeclaredMethods())
                .map(java.lang.reflect.Method::getName)).noneMatch(n -> n.contains("MaxRelations"));
    }
}
