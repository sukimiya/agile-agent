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

class TenantLlmConfigRepositoryTest {

    private TenantLlmConfigRepository repository;

    @BeforeEach
    void setUp() {
        var dbName = "tenant-llm-test-" + System.nanoTime();
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
        repository = new TenantLlmConfigRepository(DatabaseClient.create(ConnectionFactories.get(options)));
    }

    private static TenantLlmConfigRepository.TenantLlmConfigRow row(
            String id, String tenantId, String presetId, String encryptedKey) {
        var now = LocalDateTime.of(2026, 10, 1, 2, 3, 4, 654321000);
        return new TenantLlmConfigRepository.TenantLlmConfigRow(
                id, tenantId, presetId, encryptedKey, now, now);
    }

    private static String historyTable() {
        return "l7" + "_skill_flyway_schema_history";
    }

    @Test
    void unknownTenantLookupIsEmptyAndDeleteReturnsZero() {
        assertThat(repository.findByTenantId("unknown-tenant").blockOptional()).isEmpty();
        assertThat(repository.deleteByTenantId("unknown-tenant").block()).isZero();
    }

    @Test
    void upsertIsKeyedByTenantIdAndPresetCanBeDeletedIndependently() {
        repository.upsert(row("config-1", "tenant-1", "preset-1", "enc-old")).block();
        repository.upsert(row("config-2", "tenant-1", "preset-2", "enc-new")).block();

        var config = repository.findByTenantId("tenant-1").block();
        assertThat(config.id()).isEqualTo("config-1");
        assertThat(config.presetId()).isEqualTo("preset-2");
        assertThat(config.apiKeyEncrypted()).isEqualTo("enc-new");

        assertThat(repository.deleteByTenantId("tenant-1").block()).isEqualTo(1L);
        assertThat(repository.findByTenantId("tenant-1").blockOptional()).isEmpty();
    }
}
