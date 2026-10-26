package io.github.sukimiya.agileagent.memory.items;

import java.time.LocalDateTime;

import io.github.sukimiya.agileagent.memory.MemoryScope;

/** One-hop relation row ({@code memory_relations}). */
public record MemoryRelation(String id, MemoryScope scope, String fromEntityId, String relation, String toEntityId,
        String itemId, LocalDateTime validFrom, LocalDateTime validUntil, String status) {}
