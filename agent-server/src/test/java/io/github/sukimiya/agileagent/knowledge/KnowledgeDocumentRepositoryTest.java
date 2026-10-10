package io.github.sukimiya.agileagent.knowledge;

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

import io.github.sukimiya.agileagent.knowledge.KnowledgeDocumentRepository.KnowledgeDocumentRow;
import io.github.sukimiya.agileagent.knowledge.KnowledgeDocumentRepository.KnowledgeDocumentRow.Status;
import io.github.sukimiya.agileagent.knowledge.KnowledgeDocumentRepository.KnowledgeDocumentRow.ScopeType;

class KnowledgeDocumentRepositoryTest {

    private KnowledgeDocumentRepository repository;

    @BeforeEach
    void setUp() {
        var dbName = "knowledge-repo-test-" + System.nanoTime();
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
        repository = new KnowledgeDocumentRepository(DatabaseClient.create(connectionFactory));
    }

    private static KnowledgeDocumentRow newRow(String id, Status status) {
        return new KnowledgeDocumentRow(id, "标题", "original.md", "/tmp/" + id + ".md",
                status, "摘要", "user-1", LocalDateTime.now(), null, null,
                ScopeType.GLOBAL, null, null, null, null);
    }

    @Test
    void findByIdOnUnknownIdReturnsEmpty() {
        assertThat(repository.findById("no-such-id").blockOptional()).isEmpty();
    }

    @Test
    void insertThenFindByIdReturnsRow() {
        repository.insert(newRow("k1", Status.PENDING)).block();

        var row = repository.findById("k1").blockOptional();

        assertThat(row).isPresent();
        assertThat(row.get().id()).isEqualTo("k1");
        assertThat(row.get().title()).isEqualTo("标题");
        assertThat(row.get().originalFilename()).isEqualTo("original.md");
        assertThat(row.get().status()).isEqualTo(Status.PENDING);
        assertThat(row.get().summary()).isEqualTo("摘要");
        assertThat(row.get().uploaderUserId()).isEqualTo("user-1");
        assertThat(row.get().reviewedByUserId()).isNull();
        assertThat(row.get().reviewedAt()).isNull();
        assertThat(row.get().scopeType()).isEqualTo(ScopeType.GLOBAL);
        assertThat(row.get().tenantId()).isNull();
        assertThat(row.get().tenantName()).isNull();
        assertThat(row.get().orgId()).isNull();
        assertThat(row.get().orgName()).isNull();
    }

    @Test
    void insertThenFindByIdRoundTripsEachScopeAndNullableSnapshots() {
        repository.insert(scopedRow("global", ScopeType.GLOBAL, null, null, null, null)).block();
        repository.insert(scopedRow("tenant", ScopeType.TENANT, "tenant-1", "Tenant One", null, null)).block();
        repository.insert(scopedRow("org", ScopeType.ORG, "tenant-2", null, "org-1", "Org One")).block();

        assertThat(repository.findById("global").block()).satisfies(row -> {
            assertThat(row.scopeType()).isEqualTo(ScopeType.GLOBAL);
            assertThat(row.tenantId()).isNull();
            assertThat(row.orgId()).isNull();
        });
        assertThat(repository.findById("tenant").block()).satisfies(row -> {
            assertThat(row.scopeType()).isEqualTo(ScopeType.TENANT);
            assertThat(row.tenantId()).isEqualTo("tenant-1");
            assertThat(row.tenantName()).isEqualTo("Tenant One");
            assertThat(row.orgId()).isNull();
            assertThat(row.orgName()).isNull();
        });
        assertThat(repository.findById("org").block()).satisfies(row -> {
            assertThat(row.scopeType()).isEqualTo(ScopeType.ORG);
            assertThat(row.tenantId()).isEqualTo("tenant-2");
            assertThat(row.tenantName()).isNull();
            assertThat(row.orgId()).isEqualTo("org-1");
            assertThat(row.orgName()).isEqualTo("Org One");
        });
    }

    @Test
    void insertCoercesNullSummaryToEmptyString() {
        var row = new KnowledgeDocumentRow("k1", "标题", "original.md", "/tmp/k1.md",
                Status.PENDING, null, "user-1", LocalDateTime.now(), null, null,
                ScopeType.GLOBAL, null, null, null, null);

        repository.insert(row).block();

        var found = repository.findById("k1").block();
        assertThat(found.summary()).isEmpty();
    }

    @Test
    void findByStatusReturnsOnlyMatchingRowsNewestFirst() throws InterruptedException {
        repository.insert(newRow("k1", Status.PENDING)).block();
        Thread.sleep(10);
        repository.insert(newRow("k2", Status.PENDING)).block();
        repository.insert(newRow("k3", Status.APPROVED)).block();

        var rows = repository.findByStatus(Status.PENDING).collectList().block();

        assertThat(rows).extracting(KnowledgeDocumentRow::id).containsExactly("k2", "k1");
    }

    @Test
    void findByStatusReturnsEmptyWhenNoMatchingRows() {
        repository.insert(newRow("k1", Status.PENDING)).block();

        var rows = repository.findByStatus(Status.APPROVED).collectList().block();

        assertThat(rows).isEmpty();
    }

    @Test
    void approveIfPendingUpdatesRowAndReturnsOneWhenPending() {
        repository.insert(newRow("k1", Status.PENDING)).block();

        var updated = repository.approveIfPending("k1", "新摘要", "admin-1", LocalDateTime.now()).block();

        assertThat(updated).isEqualTo(1L);
        var row = repository.findById("k1").block();
        assertThat(row.status()).isEqualTo(Status.APPROVED);
        assertThat(row.summary()).isEqualTo("新摘要");
        assertThat(row.reviewedByUserId()).isEqualTo("admin-1");
        assertThat(row.reviewedAt()).isNotNull();
    }

    @Test
    void approveIfPendingReturnsZeroWhenAlreadyApproved() {
        repository.insert(newRow("k1", Status.APPROVED)).block();

        var updated = repository.approveIfPending("k1", "新摘要", "admin-1", LocalDateTime.now()).block();

        assertThat(updated).isZero();
    }

    @Test
    void approveIfPendingCoercesNullSummaryToEmptyString() {
        repository.insert(newRow("k1", Status.PENDING)).block();

        repository.approveIfPending("k1", null, "admin-1", LocalDateTime.now()).block();

        var row = repository.findById("k1").block();
        assertThat(row.summary()).isEmpty();
    }

    @Test
    void rejectIfPendingUpdatesRowAndReturnsOneWhenPending() {
        repository.insert(newRow("k1", Status.PENDING)).block();

        var updated = repository.rejectIfPending("k1", "admin-1", LocalDateTime.now()).block();

        assertThat(updated).isEqualTo(1L);
        var row = repository.findById("k1").block();
        assertThat(row.status()).isEqualTo(Status.REJECTED);
        assertThat(row.reviewedByUserId()).isEqualTo("admin-1");
    }

    @Test
    void rejectIfPendingReturnsZeroWhenAlreadyRejected() {
        repository.insert(newRow("k1", Status.REJECTED)).block();

        var updated = repository.rejectIfPending("k1", "admin-1", LocalDateTime.now()).block();

        assertThat(updated).isZero();
    }

    @Test
    void statusTransitionsPreserveScopeMetadata() {
        repository.insert(scopedRow("org-transition", ScopeType.ORG,
                "tenant-1", "Tenant One", "org-1", null)).block();

        repository.approveIfPending("org-transition", "摘要", "admin-1", LocalDateTime.now()).block();

        var row = repository.findById("org-transition").block();
        assertThat(row.status()).isEqualTo(Status.APPROVED);
        assertThat(row.scopeType()).isEqualTo(ScopeType.ORG);
        assertThat(row.tenantId()).isEqualTo("tenant-1");
        assertThat(row.tenantName()).isEqualTo("Tenant One");
        assertThat(row.orgId()).isEqualTo("org-1");
    }

    private static KnowledgeDocumentRow scopedRow(String id, ScopeType scopeType,
            String tenantId, String tenantName, String orgId, String orgName) {
        return new KnowledgeDocumentRow(id, "标题", "original.md", "/tmp/" + id + ".md",
                Status.PENDING, "摘要", "user-1", LocalDateTime.now(), null, null,
                scopeType, tenantId, tenantName, orgId, orgName);
    }
}
