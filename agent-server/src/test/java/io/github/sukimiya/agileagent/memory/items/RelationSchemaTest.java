package io.github.sukimiya.agileagent.memory.items;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import javax.sql.DataSource;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

import io.github.sukimiya.agileagent.memory.MemoryTestFixture;

/**
 * V18 (relation write path, user decisions 2026-11-04) on H2 ({@code MODE=MySQL});
 * {@link RelationSchemaMySqlIntegrationTest} runs the same on MySQL 8.4.
 */
class RelationSchemaTest {

    protected MemoryTestFixture fixture;

    protected MemoryTestFixture newFixture() {
        return MemoryTestFixture.h2();
    }

    protected String vendor() {
        return "h2";
    }

    @BeforeEach
    void setUp() {
        fixture = newFixture();
    }

    private Flyway flyway(DataSource ds, String target) {
        var config = Flyway.configure().dataSource(ds).cleanDisabled(false)
                .locations("classpath:db/migration", "classpath:db/vendor/" + vendor());
        return (target == null ? config : config.target(target)).load();
    }

    @Test
    void v18DropsRelationsWithoutALiveItemAndMakesTheItemRequired() {
        var ds = fixture.dataSource();
        var jdbc = new JdbcTemplate(ds);
        flyway(ds, null).clean();
        flyway(ds, "17").migrate();
        jdbc.update("INSERT INTO memory_items (id, tenant_id, subject, conversation_id, item_type, content, status,"
                + " assertion, importance, confidence, rules_version, judge_model, reason, revision, created_at,"
                + " updated_at) VALUES ('i1', 't', 'alice', NULL, 'FACT', '我负责星河项目', 'ACTIVE', 'USER_STATED',"
                + " 0.5, 0.9, 1, 'm', 'r', 1, CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6))");
        for (var row : new String[][] {{"r1", "'i1'"}, {"r2", "NULL"}, {"r3", "'missing'"}}) {
            jdbc.update("INSERT INTO memory_relations (id, tenant_id, subject, from_entity, relation, to_entity,"
                    + " item_id, status) VALUES ('" + row[0] + "', 't', 'alice', 'e1', '负责', 'e2', " + row[1]
                    + ", 'ACTIVE')");
        }

        flyway(ds, null).migrate();

        assertThat(jdbc.queryForList("SELECT id FROM memory_relations", String.class)).containsExactly("r1");
        assertThatThrownBy(() -> jdbc.update("INSERT INTO memory_relations (id, tenant_id, subject, from_entity,"
                + " relation, to_entity, item_id, status) VALUES ('r4', 't', 'alice', 'e1', '负责', 'e2', NULL,"
                + " 'ACTIVE')")).as("item_id is required").isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void oneEdgePerItemAndIndexesLeadWithTheOwner() {
        var jdbc = fixture.jdbc();
        jdbc.update("INSERT INTO memory_relations (id, tenant_id, subject, from_entity, relation, to_entity, item_id,"
                + " status) VALUES ('r1', 't', 'alice', 'e1', '负责', 'e2', 'i1', 'ACTIVE')");
        assertThatThrownBy(() -> jdbc.update("INSERT INTO memory_relations (id, tenant_id, subject, from_entity,"
                + " relation, to_entity, item_id, status) VALUES ('r2', 't', 'alice', 'e1', '负责', 'e2', 'i1',"
                + " 'SUPERSEDED')")).as("same edge of the same item").isInstanceOf(DataIntegrityViolationException.class);
        // The same edge from another item, another owner or another relation type is a separate row.
        jdbc.update("INSERT INTO memory_relations (id, tenant_id, subject, from_entity, relation, to_entity, item_id,"
                + " status) VALUES ('r3', 't', 'alice', 'e1', '负责', 'e2', 'i2', 'ACTIVE')");
        jdbc.update("INSERT INTO memory_relations (id, tenant_id, subject, from_entity, relation, to_entity, item_id,"
                + " status) VALUES ('r4', 't', 'Alice', 'e1', '负责', 'e2', 'i1', 'ACTIVE')");
        jdbc.update("INSERT INTO memory_relations (id, tenant_id, subject, from_entity, relation, to_entity, item_id,"
                + " status) VALUES ('r5', 't', 'alice', 'e1', '使用', 'e2', 'i1', 'ACTIVE')");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM memory_relations", Integer.class)).isEqualTo(4);

        var indexes = indexColumns(jdbc);
        assertThat(indexes).contains("TENANT_ID,SUBJECT,FROM_ENTITY", "TENANT_ID,SUBJECT,TO_ENTITY")
                .doesNotContain("FROM_ENTITY,STATUS", "TO_ENTITY,STATUS");
    }

    /** Column lists (upper case, comma separated, in order) of every index on memory_relations. */
    private java.util.Set<String> indexColumns(JdbcTemplate jdbc) {
        var byIndex = new java.util.TreeMap<String, java.util.TreeMap<Integer, String>>();
        try (var c = fixture.dataSource().getConnection()) {
            var meta = c.getMetaData();
            for (var table : new String[] {"memory_relations", "MEMORY_RELATIONS"}) {
                try (var rs = meta.getIndexInfo(c.getCatalog(), null, table, false, false)) {
                    while (rs.next()) {
                        var name = rs.getString("INDEX_NAME");
                        var column = rs.getString("COLUMN_NAME");
                        if (name != null && column != null) {
                            byIndex.computeIfAbsent(name, k -> new java.util.TreeMap<>())
                                    .put(rs.getInt("ORDINAL_POSITION"), column.toUpperCase(java.util.Locale.ROOT));
                        }
                    }
                }
            }
        } catch (java.sql.SQLException e) {
            throw new IllegalStateException(e);
        }
        var out = new java.util.HashSet<String>();
        byIndex.values().forEach(cols -> out.add(String.join(",", cols.values())));
        return out;
    }
}
