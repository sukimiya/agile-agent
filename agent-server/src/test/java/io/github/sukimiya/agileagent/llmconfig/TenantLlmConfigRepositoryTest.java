package io.github.sukimiya.agileagent.llmconfig;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.SQLException;
import java.time.LocalDateTime;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;

import io.r2dbc.spi.ConnectionFactories;
import io.r2dbc.spi.ConnectionFactoryOptions;
import io.r2dbc.spi.Option;
import io.r2dbc.spi.R2dbcException;
import reactor.core.publisher.Mono;

class TenantLlmConfigRepositoryTest {

    private TenantLlmConfigRepository repository;
    private DatabaseClient db;

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
        db = DatabaseClient.create(ConnectionFactories.get(options));
        repository = new TenantLlmConfigRepository(db);
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

    @Test
    void idCollisionAcrossTenantsFailsWithoutChangingTheExistingTenant() {
        repository.upsert(row("shared-id", "tenant-a", "preset-a", "enc-a")).block();

        assertThatThrownBy(() -> repository.upsert(row("shared-id", "tenant-b", "preset-b", "enc-b")).block())
                .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);

        var tenantA = repository.findByTenantId("tenant-a").block();
        assertThat(tenantA.presetId()).isEqualTo("preset-a");
        assertThat(tenantA.apiKeyEncrypted()).isEqualTo("enc-a");
        assertThat(repository.findByTenantId("tenant-b").blockOptional()).isEmpty();
    }

    @Test
    void concurrentUpsertsForSameTenantLeaveOneInternallyConsistentRow() {
        var configA = row("config-a", "tenant-concurrent", "preset-a", "key-a");
        var configB = row("config-b", "tenant-concurrent", "preset-b", "key-b");

        Mono.when(repository.upsert(configA), repository.upsert(configB)).block();

        var config = repository.findByTenantId("tenant-concurrent").block();
        var rowCount = db.sql("SELECT COUNT(*) AS row_count FROM tenant_llm_config WHERE tenant_id = :tenantId")
                .bind("tenantId", "tenant-concurrent")
                .map((row, metadata) -> row.get("row_count", Long.class))
                .one()
                .block();
        assertThat(rowCount).isEqualTo(1L);
        assertThat(config.id()).isIn("config-a", "config-b");
        assertThat(config.presetId()).isEqualTo(config.apiKeyEncrypted().equals("key-a") ? "preset-a" : "preset-b");
        assertThat(config.apiKeyEncrypted()).isIn("key-a", "key-b");
    }

    @Test
    void identicalTenantUpsertIsIdempotentAndLeavesOneRow() {
        var config = row("config-idempotent", "tenant-idempotent", "preset-a", "key-a");

        repository.upsert(config).block();
        repository.upsert(config).block();

        assertThat(repository.findByTenantId("tenant-idempotent").block()).isEqualTo(config);
        var rowCount = db.sql("SELECT COUNT(*) AS row_count FROM tenant_llm_config WHERE tenant_id = :tenantId")
                .bind("tenantId", "tenant-idempotent")
                .map((row, metadata) -> row.get("row_count", Long.class))
                .one()
                .block();
        assertThat(rowCount).isEqualTo(1L);
    }

    @Test
    void duplicateClassifierRecognizesWrappedDatabaseSignalsButNotGenericIntegrityFailures() {
        assertThat(TenantLlmConfigRepository.isDuplicateKey(new SQLException("duplicate", "23505"))).isTrue();
        assertThat(TenantLlmConfigRepository.isDuplicateKey(
                new R2dbcException("duplicate", "23505") { })).isTrue();
        assertThat(TenantLlmConfigRepository.isDuplicateKey(new DataIntegrityViolationException(
                "wrapped mysql failure", new R2dbcException("duplicate", "23000", 1062) { })))
                .isTrue();
        assertThat(TenantLlmConfigRepository.isDuplicateKey(new DuplicateKeyException("duplicate"))).isTrue();
        assertThat(TenantLlmConfigRepository.isDuplicateKey(new DataIntegrityViolationException("not a duplicate"))).isFalse();
        assertThat(TenantLlmConfigRepository.isDuplicateKey(new IllegalStateException("unavailable"))).isFalse();
    }
}
