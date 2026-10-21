package io.github.sukimiya.agileagent.memory;

import java.util.Set;

import javax.sql.DataSource;

import org.flywaydb.core.Flyway;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.r2dbc.core.DatabaseClient;

import io.github.sukimiya.agileagent.spi.identity.IdentityContext;
import io.r2dbc.spi.ConnectionFactories;
import io.r2dbc.spi.ConnectionFactoryOptions;
import io.r2dbc.spi.Option;

/**
 * Isolated, fully migrated H2 ({@code MODE=MySQL}) database for fast memory tests. Every instance owns a
 * fresh in-memory database, so tests never share rows.
 */
public final class MemoryTestFixture {

    private final DataSource dataSource;
    private final DatabaseClient databaseClient;

    private MemoryTestFixture(DataSource dataSource, DatabaseClient databaseClient) {
        this.dataSource = dataSource;
        this.databaseClient = databaseClient;
    }

    static MemoryTestFixture of(DataSource dataSource, DatabaseClient databaseClient) {
        return new MemoryTestFixture(dataSource, databaseClient);
    }

    public static MemoryTestFixture h2() {
        var dbName = "memory-test-" + System.nanoTime();
        var dataSource = new DriverManagerDataSource(
                "jdbc:h2:mem:" + dbName + ";MODE=MySQL;DB_CLOSE_DELAY=-1", "sa", "");
        dataSource.setDriverClassName("org.h2.Driver");
        Flyway.configure().dataSource(dataSource).load().migrate();
        var options = ConnectionFactoryOptions.builder()
                .option(ConnectionFactoryOptions.DRIVER, "h2")
                .option(ConnectionFactoryOptions.PROTOCOL, "mem")
                .option(ConnectionFactoryOptions.DATABASE, dbName)
                .option(ConnectionFactoryOptions.USER, "sa")
                .option(ConnectionFactoryOptions.PASSWORD, "")
                .option(Option.valueOf("options"), "MODE=MySQL;DB_CLOSE_DELAY=-1")
                .build();
        return new MemoryTestFixture(dataSource, DatabaseClient.create(ConnectionFactories.get(options)));
    }

    public DataSource dataSource() {
        return dataSource;
    }

    public DatabaseClient databaseClient() {
        return databaseClient;
    }

    public JdbcTemplate jdbc() {
        return new JdbcTemplate(dataSource);
    }

    public static MemoryScope scope(String tenantId, String subject) {
        return new MemoryScope(tenantId, subject);
    }

    public static IdentityContext identity(String subject, String tenantId) {
        return new IdentityContext(subject, subject, tenantId, tenantId, null, null,
                Set.of(), Set.of(), Set.of(), false, false);
    }
}
