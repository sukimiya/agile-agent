package io.github.sukimiya.agileagent.memory;

import java.util.Locale;
import java.util.Set;

import org.flywaydb.core.Flyway;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import io.r2dbc.spi.ConnectionFactories;
import io.r2dbc.spi.ConnectionFactoryOptions;

/**
 * Connects the opt-in {@code memory-mysql-it} tests to an explicitly configured, disposable MySQL test database
 * (see {@code docker/docker-compose.memory-test.yml}). Missing configuration fails the test instead of skipping
 * it, and only a loopback host with a database name ending in {@code _it} is accepted, so these tests can never
 * clean a shared or production schema.
 */
final class MemoryMySqlTestSupport {

    static final String HOST = "MEMORY_MYSQL_IT_HOST";
    static final String PORT = "MEMORY_MYSQL_IT_PORT";
    static final String DATABASE = "MEMORY_MYSQL_IT_DATABASE";
    static final String USER = "MEMORY_MYSQL_IT_USER";
    static final String PASSWORD = "MEMORY_MYSQL_IT_PASSWORD";

    private static final Set<String> LOOPBACK = Set.of("127.0.0.1", "localhost", "::1");

    private MemoryMySqlTestSupport() {}

    /** Drops every object in the configured test database, re-runs all migrations and returns a fixture. */
    static MemoryTestFixture freshDatabase() {
        var host = required(HOST);
        var port = Integer.parseInt(required(PORT));
        var database = required(DATABASE);
        var user = required(USER);
        var password = required(PASSWORD);
        if (!LOOPBACK.contains(host.toLowerCase(Locale.ROOT))) {
            throw new IllegalStateException(HOST + " must be a loopback address for memory MySQL tests");
        }
        if (!database.endsWith("_it")) {
            throw new IllegalStateException(DATABASE + " must name a disposable test database ending in _it");
        }
        var jdbcUrl = "jdbc:mysql://" + host + ":" + port + "/" + database
                + "?useSSL=false&allowPublicKeyRetrieval=true&connectionTimeZone=UTC";
        var dataSource = new DriverManagerDataSource(jdbcUrl, user, password);
        dataSource.setDriverClassName("com.mysql.cj.jdbc.Driver");
        var flyway = Flyway.configure().dataSource(dataSource).cleanDisabled(false).load();
        flyway.clean();
        flyway.migrate();
        var options = ConnectionFactoryOptions.builder()
                .option(ConnectionFactoryOptions.DRIVER, "mysql")
                .option(ConnectionFactoryOptions.HOST, host)
                .option(ConnectionFactoryOptions.PORT, port)
                .option(ConnectionFactoryOptions.DATABASE, database)
                .option(ConnectionFactoryOptions.USER, user)
                .option(ConnectionFactoryOptions.PASSWORD, password)
                .option(ConnectionFactoryOptions.SSL, false)
                .build();
        return MemoryTestFixture.of(dataSource, ConnectionFactories.get(options));
    }

    private static String required(String name) {
        var value = System.getenv(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("memory MySQL test configuration missing: " + name
                    + " (see docker/docker-compose.memory-test.yml)");
        }
        return value;
    }
}
