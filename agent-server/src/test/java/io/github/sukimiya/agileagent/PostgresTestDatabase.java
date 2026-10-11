package io.github.sukimiya.agileagent;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

import javax.sql.DataSource;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.configuration.FluentConfiguration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.r2dbc.core.DatabaseClient;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import io.r2dbc.pool.ConnectionPool;
import io.r2dbc.spi.ConnectionFactories;
import io.r2dbc.spi.ConnectionFactory;
import io.r2dbc.spi.ConnectionFactoryOptions;

/**
 * A fresh disposable PostgreSQL database on one JVM-shared Testcontainers instance.
 * Mirrors the per-test in-memory isolation the suite used before the PostgreSQL
 * migration: each {@link #create(String)} handle owns a dedicated database and
 * {@link #close()} force-drops it. Requires a running Docker daemon.
 */
public final class PostgresTestDatabase implements AutoCloseable {

    private static final PostgreSQLContainer<?> CONTAINER = startSharedContainer();

    private final String database;
    private final DriverManagerDataSource dataSource;
    private volatile ConnectionFactory connectionFactory;
    private volatile DatabaseClient databaseClient;

    private PostgresTestDatabase(String database) {
        this.database = database;
        this.dataSource = new DriverManagerDataSource(jdbcUrl(database), CONTAINER.getUsername(),
                CONTAINER.getPassword());
        this.dataSource.setDriverClassName("org.postgresql.Driver");
    }

    private static PostgreSQLContainer<?> startSharedContainer() {
        var container = new PostgreSQLContainer<>(DockerImageName.parse("postgres:16-alpine"));
        container.start();
        return container;
    }

    public static PostgresTestDatabase create(String prefix) {
        var sanitized = (prefix + "_" + System.nanoTime()).toLowerCase().replaceAll("[^a-z0-9_]", "_");
        if (sanitized.length() > 63) {
            sanitized = sanitized.substring(sanitized.length() - 63);
        }
        executeOnAdminDatabase("CREATE DATABASE " + sanitized);
        return new PostgresTestDatabase(sanitized);
    }

    public FluentConfiguration flyway() {
        return Flyway.configure().dataSource(dataSource).locations("classpath:db/migration");
    }

    public void migrate() {
        flyway().load().migrate();
    }

    public String jdbcUrl() {
        return jdbcUrl(database);
    }

    public String r2dbcUrl() {
        return "r2dbc:postgresql://" + CONTAINER.getHost() + ":" + mappedPort() + "/" + database;
    }

    public String username() {
        return CONTAINER.getUsername();
    }

    public String password() {
        return CONTAINER.getPassword();
    }

    public DataSource dataSource() {
        return dataSource;
    }

    public JdbcTemplate jdbc() {
        return new JdbcTemplate(dataSource);
    }

    public ConnectionFactory connectionFactory() {
        ConnectionFactory current = connectionFactory;
        if (current == null) {
            current = ConnectionFactories.get(ConnectionFactoryOptions.builder()
                    .option(ConnectionFactoryOptions.DRIVER, "postgresql")
                    .option(ConnectionFactoryOptions.HOST, CONTAINER.getHost())
                    .option(ConnectionFactoryOptions.PORT, mappedPort())
                    .option(ConnectionFactoryOptions.DATABASE, database)
                    .option(ConnectionFactoryOptions.USER, username())
                    .option(ConnectionFactoryOptions.PASSWORD, password())
                    .build());
            connectionFactory = current;
        }
        return current;
    }

    public DatabaseClient databaseClient() {
        DatabaseClient current = databaseClient;
        if (current == null) {
            current = DatabaseClient.create(connectionFactory());
            databaseClient = current;
        }
        return current;
    }

    @Override
    public void close() {
        if (connectionFactory instanceof ConnectionPool pool) {
            pool.dispose();
        }
        executeOnAdminDatabase("DROP DATABASE IF EXISTS " + database + " WITH (FORCE)");
    }

    private static int mappedPort() {
        return CONTAINER.getMappedPort(PostgreSQLContainer.POSTGRESQL_PORT);
    }

    private static String jdbcUrl(String database) {
        return "jdbc:postgresql://" + CONTAINER.getHost() + ":" + mappedPort() + "/" + database;
    }

    private static void executeOnAdminDatabase(String sql) {
        try (Connection connection = DriverManager.getConnection(
                CONTAINER.getJdbcUrl(), CONTAINER.getUsername(), CONTAINER.getPassword());
             Statement statement = connection.createStatement()) {
            statement.execute(sql);
        } catch (SQLException e) {
            throw new IllegalStateException("test database operation failed: " + sql, e);
        }
    }
}
