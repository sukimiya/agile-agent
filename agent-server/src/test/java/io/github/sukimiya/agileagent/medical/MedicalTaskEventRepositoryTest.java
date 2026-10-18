package io.github.sukimiya.agileagent.medical;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;

import org.junit.jupiter.api.Test;

import io.github.sukimiya.agileagent.PostgresTestDatabase;
import io.github.sukimiya.medical.rules.MedicalTaskEventRepository;
import io.github.sukimiya.medical.rules.MedicalTaskEventRow;

class MedicalTaskEventRepositoryTest {

    @Test
    void insertsAndReadsBackEventsInOrder() {
        try (var db = PostgresTestDatabase.create("task_event_repo")) {
            db.migrate();
            var repo = new MedicalTaskEventRepository(db.databaseClient());

            repo.insert(new MedicalTaskEventRow("ev-1", "task-1",
                    MedicalTaskEventRow.EventType.CREATED, 0, "user-1", "rule_code=r1", null))
                    .block(Duration.ofSeconds(10));
            repo.insert(new MedicalTaskEventRow("ev-2", "task-1",
                    MedicalTaskEventRow.EventType.ESCALATED, 1, null, null, null))
                    .block(Duration.ofSeconds(10));
            repo.insert(new MedicalTaskEventRow("ev-3", "task-other",
                    MedicalTaskEventRow.EventType.CREATED, 0, "user-1", null, null))
                    .block(Duration.ofSeconds(10));

            var events = repo.findByTaskId("task-1").collectList().block(Duration.ofSeconds(10));
            assertThat(events).hasSize(2);
            assertThat(events.get(0).eventType()).isEqualTo(MedicalTaskEventRow.EventType.CREATED);
            assertThat(events.get(0).detail()).isEqualTo("rule_code=r1");
            assertThat(events.get(0).createdAt()).isNotNull();
            assertThat(events.get(1).eventType()).isEqualTo(MedicalTaskEventRow.EventType.ESCALATED);
            assertThat(events.get(1).level()).isEqualTo(1);
        }
    }

    @Test
    void hasEventMatchesTypeAndExactDetail() {
        try (var db = PostgresTestDatabase.create("task_event_repo_has")) {
            db.migrate();
            var repo = new MedicalTaskEventRepository(db.databaseClient());
            repo.insert(new MedicalTaskEventRow("ev-1", "task-1",
                    MedicalTaskEventRow.EventType.ESCALATED, 3, null, "已冒泡至最高层级，停止升级", null))
                    .block(Duration.ofSeconds(10));

            assertThat(repo.hasEvent("task-1", MedicalTaskEventRow.EventType.ESCALATED,
                    "已冒泡至最高层级，停止升级").block(Duration.ofSeconds(10))).isTrue();
            assertThat(repo.hasEvent("task-1", MedicalTaskEventRow.EventType.ESCALATED,
                    "其他").block(Duration.ofSeconds(10))).isFalse();
            assertThat(repo.hasEvent("task-1", MedicalTaskEventRow.EventType.CREATED,
                    "已冒泡至最高层级，停止升级").block(Duration.ofSeconds(10))).isFalse();
            assertThat(repo.hasEvent("task-x", MedicalTaskEventRow.EventType.ESCALATED,
                    "已冒泡至最高层级，停止升级").block(Duration.ofSeconds(10))).isFalse();
        }
    }
}
