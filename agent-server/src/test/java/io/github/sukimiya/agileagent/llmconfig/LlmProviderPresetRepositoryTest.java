package io.github.sukimiya.agileagent.llmconfig;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.github.sukimiya.agileagent.PostgresTestDatabase;

class LlmProviderPresetRepositoryTest {

    private PostgresTestDatabase database;
    private LlmProviderPresetRepository repository;

    @BeforeEach
    void setUp() {
        database = PostgresTestDatabase.create("llm_preset_test");
        database.flyway().table(historyTable()).load().migrate();
        repository = new LlmProviderPresetRepository(database.databaseClient());
    }

    @AfterEach
    void tearDown() {
        database.close();
    }

    private static LlmProviderPresetRepository.LlmProviderPresetRow row(String id, String balancePath) {
        var now = LocalDateTime.of(2026, 10, 1, 2, 3, 4, 123456000);
        return new LlmProviderPresetRepository.LlmProviderPresetRow(
                id, "Provider " + id, "https://api.example.test/v1", "model-a", balancePath,
                "subject-1", now, now);
    }

    private static String historyTable() {
        return "l7" + "_skill_flyway_schema_history";
    }

    @Test
    void unknownPresetLookupIsEmpty() {
        assertThat(repository.findById("missing").blockOptional()).isEmpty();
    }

    @Test
    void crudPreservesNullableBalancePathAndUpdatesOnlyRequestedPreset() {
        repository.insert(row("preset-a", null)).block();
        repository.insert(row("preset-b", "/balance")).block();

        var inserted = repository.findById("preset-a").block();
        assertThat(inserted.balancePath()).isNull();
        assertThat(inserted.createdAt()).isEqualTo(LocalDateTime.of(2026, 10, 1, 2, 3, 4, 123456000));

        var changed = new LlmProviderPresetRepository.LlmProviderPresetRow(
                "preset-a", "Changed", "https://changed.example.test", "model-b", "/usage",
                "subject-2", inserted.createdAt(), inserted.updatedAt().plusHours(1));
        assertThat(repository.update(changed).block()).isEqualTo(1L);
        assertThat(repository.findById("preset-a").block().name()).isEqualTo("Changed");
        assertThat(repository.findById("preset-b").block().name()).isEqualTo("Provider preset-b");
        assertThat(repository.findAll().collectList().block()).hasSize(2);

        assertThat(repository.deleteById("preset-a").block()).isEqualTo(1L);
        assertThat(repository.findById("preset-a").blockOptional()).isEmpty();
    }
}
