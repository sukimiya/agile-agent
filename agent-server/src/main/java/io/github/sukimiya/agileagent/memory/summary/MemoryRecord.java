package io.github.sukimiya.agileagent.memory.summary;

import java.util.List;

/**
 * One {@code memory-record-v1} record: the search index entry of one completed turn (M2a Task 4, D18). It is
 * never injected into a prompt; a search hit leads back to the turn's original messages
 * ({@link SummaryRepository#originalTurn}). {@code source} (turn and sequence range) is filled by the server.
 */
public record MemoryRecord(String id, String turnId, long fromSequence, long toSequence, String fromMessageId,
        String toMessageId, String summary, List<Entity> entities, List<KeyFact> keyFacts, boolean decision,
        List<String> keywords, MemoryType memoryType, String generatorModel, String schemaVersion, int version) {

    /** Stable fact (相对稳定的事实) or episodic event (发生过的事). */
    public enum MemoryType { FACT, EVENT }

    /** A proper name copied verbatim from the turn; {@code type}: PERSON, ORG, PLACE, PRODUCT, PROJECT or OTHER. */
    public record Entity(String name, String type) {}

    /** A number, version, name, date, amount or code copied verbatim; {@code kind} as in the schema, else OTHER. */
    public record KeyFact(String text, String kind) {}

    public MemoryRecord {
        entities = List.copyOf(entities);
        keyFacts = List.copyOf(keyFacts);
        keywords = List.copyOf(keywords);
    }
}
