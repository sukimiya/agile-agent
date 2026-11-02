package io.github.sukimiya.agileagent.memory.items;

import io.github.sukimiya.agileagent.memory.MemoryScope;

/** User memory profile snapshot ({@code user_memory_profiles}). */
public record Profile(MemoryScope scope, String content, long version, long builtThrough) {}
