package io.github.sukimiya.agileagent;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;

import org.junit.jupiter.api.Test;

class PostgresTestDatabaseTest {

    @Test
    void migratesAndServesBothJdbcAndR2dbcFromTheSameDatabase() {
        try (var db = PostgresTestDatabase.create("selftest")) {
            db.migrate();

            assertThat(db.jdbc().queryForObject(
                    "SELECT COUNT(*) FROM information_schema.tables "
                            + "WHERE table_schema = 'public' AND table_name = 'sessions'",
                    Integer.class)).isEqualTo(1);

            db.jdbc().update("INSERT INTO sessions (id, user_id, title, created_at, updated_at) "
                    + "VALUES ('s1', 'u1', 'hello', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)");

            assertThat(db.databaseClient().sql("SELECT title FROM sessions WHERE id = 's1'")
                    .map((row, metadata) -> row.get("title", String.class))
                    .one().block(Duration.ofSeconds(10))).isEqualTo("hello");
        }
    }

    @Test
    void eachHandleGetsAnIsolatedDatabase() {
        try (var first = PostgresTestDatabase.create("isolation_a");
             var second = PostgresTestDatabase.create("isolation_b")) {
            first.migrate();
            second.migrate();
            first.jdbc().update("INSERT INTO sessions (id, user_id, title, created_at, updated_at) "
                    + "VALUES ('s1', 'u1', 'only-in-first', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)");

            assertThat(second.jdbc().queryForObject("SELECT COUNT(*) FROM sessions", Integer.class))
                    .isZero();
        }
    }
}
