package io.github.sukimiya.agileagent.repository;

import static org.assertj.core.api.Assertions.assertThat;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.r2dbc.core.DatabaseClient;

import io.r2dbc.spi.ConnectionFactories;
import io.r2dbc.spi.ConnectionFactoryOptions;
import io.r2dbc.spi.Option;

class SessionRepositoryTest {

    private SessionRepository repository;

    @BeforeEach
    void setUp() {
        var dbName = "session-repo-test-" + System.nanoTime();
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
        var connectionFactory = ConnectionFactories.get(options);
        repository = new SessionRepository(DatabaseClient.create(connectionFactory));
    }

    @Test
    void findByIdOnUnknownIdReturnsEmpty() {
        assertThat(repository.findById("no-such-id").blockOptional()).isEmpty();
    }

    @Test
    void insertThenFindByIdReturnsRow() {
        repository.insert("s1", "user-1", "第一次对话").block();

        var row = repository.findById("s1").blockOptional();

        assertThat(row).isPresent();
        assertThat(row.get().id()).isEqualTo("s1");
        assertThat(row.get().userId()).isEqualTo("user-1");
        assertThat(row.get().title()).isEqualTo("第一次对话");
    }

    @Test
    void touchUpdatesUpdatedAt() throws InterruptedException {
        repository.insert("s1", "user-1", "标题").block();
        var before = repository.findById("s1").block().updatedAt();
        Thread.sleep(10);

        repository.touch("s1").block();

        var after = repository.findById("s1").block().updatedAt();
        assertThat(after).isAfter(before);
    }

    @Test
    void findByUserIdReturnsOnlyThatUsersSessionsSortedByUpdatedAtDesc() throws InterruptedException {
        repository.insert("s1", "user-1", "会话一").block();
        Thread.sleep(10);
        repository.insert("s2", "user-1", "会话二").block();
        repository.insert("s3", "user-2", "别人的会话").block();

        var rows = repository.findByUserId("user-1").collectList().block();

        assertThat(rows).extracting(SessionRepository.SessionRow::id).containsExactly("s2", "s1");
    }
}
