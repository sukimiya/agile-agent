package io.github.sukimiya.agileagent.hint;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.github.sukimiya.agileagent.PostgresTestDatabase;

class HintRepositoryTest {

    private PostgresTestDatabase database;
    private HintRepository repository;

    @BeforeEach
    void setUp() {
        database = PostgresTestDatabase.create("hint_repo_test");
        database.flyway().table(historyTable()).load().migrate();
        repository = new HintRepository(database.databaseClient());
    }

    @AfterEach
    void tearDown() {
        database.close();
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
