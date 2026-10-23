package io.github.sukimiya.agileagent.memory.jobs;

import io.github.sukimiya.agileagent.memory.MemoryScope;

/** A job this instance holds the lease for; {@code attempts} counts this attempt. */
public record ClaimedJob(String id, JobType type, MemoryScope scope, String conversationId, String dedupeKey,
        String payload, int attempts) {}
