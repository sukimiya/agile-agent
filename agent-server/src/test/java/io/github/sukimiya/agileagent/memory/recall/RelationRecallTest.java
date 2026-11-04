package io.github.sukimiya.agileagent.memory.recall;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.github.sukimiya.agileagent.memory.MemoryModel.TurnStatus;
import io.github.sukimiya.agileagent.memory.MemoryScope;
import io.github.sukimiya.agileagent.memory.MemoryTestFixture;
import io.github.sukimiya.agileagent.memory.MemoryTestStack;
import io.github.sukimiya.agileagent.memory.items.EntityGraphRepository;
import io.github.sukimiya.agileagent.memory.items.ItemStatus;
import io.github.sukimiya.agileagent.memory.items.MemoryItem;
import io.github.sukimiya.agileagent.memory.items.MemoryItemRepository;
import io.github.sukimiya.agileagent.memory.items.ProfileCache;
import io.github.sukimiya.agileagent.memory.items.ProfileService;
import io.github.sukimiya.agileagent.memory.jobs.MemoryJobWorker;
import io.github.sukimiya.agileagent.memory.summary.SummaryRepository;

/**
 * Relation recall respects the cross-conversation switch and the item's conversation (relation write path T1):
 * with the switch off a session item's relation never reaches another conversation.
 */
class RelationRecallTest {

    static final Duration WAIT = Duration.ofSeconds(15);
    static final MemoryScope ALICE = MemoryTestStack.ALICE;

    MemoryTestStack stack;
    MemorySettingsService settings;
    MemoryItemRepository items;
    EntityGraphRepository graph;
    MemoryRetriever retriever;

    protected MemoryTestFixture newFixture() {
        return MemoryTestFixture.h2();
    }

    @BeforeEach
    void setUp() {
        stack = MemoryTestStack.of(newFixture());
        var db = stack.fixture().databaseClient();
        var chunks = new ChunkRepository(db, stack.conversations());
        new ChunkIndexer(db, stack.fixture().connectionFactory(), chunks, new MemoryJobWorker(db, () -> false),
                stack.props());
        items = new MemoryItemRepository(db);
        graph = new EntityGraphRepository(db, stack.props());
        var cache = new ProfileCache(null, stack.props());
        settings = new MemorySettingsService(db, cache);
        var profiles = new ProfileService(db, items, settings, cache, stack.props());
        retriever = new MemoryRetriever(db, stack.props(), settings, chunks, new SummaryRepository(db,
                stack.conversations()), FullTextSearch.forDatabase(db), stack.metrics(), items, graph, profiles);
    }

    @AfterEach
    void tearDown() {
        stack.close();
    }

    void related(String conversationId, String project) {
        var item = MemoryItem.newItem(ALICE, conversationId, "FACT", "我负责" + project, ItemStatus.ACTIVE,
                MemoryItem.Assertion.USER_STATED, 0.5, 0.9, 1, "m", "r");
        items.save(item).block(WAIT);
        graph.saveRelation(ALICE, item.id(), graph.self(ALICE).block(WAIT).id(), "负责",
                graph.upsertEntity(ALICE, project, "PROJECT").block(WAIT).id()).block(WAIT);
    }

    List<String> lines(String conversationId, String question) {
        var ticket = stack.accept(ALICE, conversationId, MemoryTestStack.newConversationId(), question).ticket();
        try {
            return retriever.recall(ticket, question).block(WAIT).relationLines();
        } finally {
            stack.finalizer().finish(ticket, TurnStatus.FAILED, "").block(WAIT);
        }
    }

    @Test
    void switchOffKeepsSessionRelationsInTheirConversation() {
        var here = MemoryTestStack.newConversationId();
        var elsewhere = MemoryTestStack.newConversationId();
        related(here, "星河项目");
        related(null, "北斗项目");

        assertThat(lines(here, "星河项目 北斗项目")).singleElement().asString().contains("星河项目");
        assertThat(lines(elsewhere, "星河项目 北斗项目")).as("no leak into another conversation").isEmpty();

        settings.update(ALICE, true).block(WAIT);
        assertThat(lines(elsewhere, "星河项目 北斗项目")).singleElement().asString().contains("北斗项目");
    }

    @Test
    void recalledRelationsAreCappedByTheRelationsSetting() {
        settings.update(ALICE, true).block(WAIT);
        for (var project : List.of("项目甲", "项目乙", "项目丙", "项目丁")) {
            related(null, project);
        }
        stack.props().getRelations().setMaxRecalled(2);
        assertThat(lines(MemoryTestStack.newConversationId(), "项目甲 项目乙 项目丙 项目丁")).hasSize(2);
    }
}
