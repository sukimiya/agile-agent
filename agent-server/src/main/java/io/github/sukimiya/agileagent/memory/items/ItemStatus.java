package io.github.sukimiya.agileagent.memory.items;

/** Lifecycle of a long-term {@link MemoryItem} (M2b). */
public enum ItemStatus {
    CANDIDATE,
    ACTIVE,
    CONFLICT,
    SUPERSEDED,
    INACTIVE,
    DELETED
}
