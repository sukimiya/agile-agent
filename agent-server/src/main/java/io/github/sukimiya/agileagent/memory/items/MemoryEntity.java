package io.github.sukimiya.agileagent.memory.items;

import io.github.sukimiya.agileagent.memory.MemoryScope;

/** Named entity in the user memory graph ({@code memory_entities}). */
public record MemoryEntity(String id, MemoryScope scope, String name, String entityType, String normalizedKey) {}
