package io.github.sukimiya.agileagent.hint;

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

class HintRepositoryTest {

    private HintRepository repository;

    @BeforeEach
    void setUp() {
        var dbName = "hint-repo-test-" + System.nanoTime();
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
        repository = new HintRepository(DatabaseClient.create(ConnectionFactories.get(options)));
    }

    @Test
    void emptyLookupInsertUpdateDeleteAndCreationOrderAreStable() {
        assertThat(repository.findAll().collectList().block()).isEmpty();
        var createdAt = LocalDateTime.of(2026, 10, 1, 2, 3, 4, 100000000);
        repository.insert(new HintRepository.HintRow("hint-b", "Second", "subject", createdAt)).block();
        repository.insert(new HintRepository.HintRow("hint-a", "First", "subject", createdAt)).block();
        repository.insert(new HintRepository.HintRow("hint-c", "Later", "subject", createdAt.plusSeconds(1))).block();

        assertThat(repository.findAll().map(HintRepository.HintRow::id).collectList().block())
                .containsExactly("hint-a", "hint-b", "hint-c");
        assertThat(repository.findById("hint-b").block())
                .isEqualTo(new HintRepository.HintRow("hint-b", "Second", "subject", createdAt));
        assertThat(repository.findById("missing").block()).isNull();
        assertThat(repository.update("hint-b", "Changed").block()).isEqualTo(1L);
        assertThat(repository.update("missing", "No row").block()).isZero();
        assertThat(repository.findAll().filter(row -> row.id().equals("hint-b")).single().block().content())
                .isEqualTo("Changed");
        assertThat(repository.deleteById("hint-a").block()).isEqualTo(1L);
        assertThat(repository.deleteById("missing").block()).isZero();
    }

    private static String historyTable() {
        return "l7" + "_skill_flyway_schema_history";
    }
}
