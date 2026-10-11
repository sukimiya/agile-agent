package io.github.sukimiya.agileagent.repository;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.github.sukimiya.agileagent.PostgresTestDatabase;

class SessionRepositoryTest {

    private PostgresTestDatabase database;
    private SessionRepository repository;

    @BeforeEach
    void setUp() {
        database = PostgresTestDatabase.create("session_repo_test");
        database.migrate();
        repository = new SessionRepository(database.databaseClient());
    }

    @AfterEach
    void tearDown() {
        database.close();
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
