package io.github.sukimiya.agileagent.llmconfig;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.r2dbc.core.DatabaseClient;

import io.r2dbc.spi.ConnectionFactories;
import io.r2dbc.spi.ConnectionFactoryOptions;
import io.r2dbc.spi.Option;

class LlmProviderPresetRepositoryTest {

    private LlmProviderPresetRepository repository;

    @BeforeEach
    void setUp() {
        var dbName = "llm-preset-test-" + System.nanoTime();
        var dataSource = new DriverManagerDataSource(
                "jdbc:h2:mem:" + dbName + ";MODE=MySQL;DB_CLOSE_DELAY=-1", "sa", "");
        dataSource.setDriverClassName("org.h2.Driver");
        Flyway.configure().dataSource(dataSource)
                .table(historyTable())
                .locations("classpath:db/migration").load().migrate();
        var options = ConnectionFactoryOptions.builder()
                .option(ConnectionFactoryOptions.DRIVER, "h2")
                .option(ConnectionFactoryOptions.PROTOCOL, "mem")
                .option(ConnectionFactoryOptions.DATABASE, dbName)
                .option(ConnectionFactoryOptions.USER, "sa")
                .option(ConnectionFactoryOptions.PASSWORD, "")
                .option(Option.valueOf("options"), "MODE=MySQL;DB_CLOSE_DELAY=-1")
                .build();
        repository = new LlmProviderPresetRepository(DatabaseClient.create(ConnectionFactories.get(options)));
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
