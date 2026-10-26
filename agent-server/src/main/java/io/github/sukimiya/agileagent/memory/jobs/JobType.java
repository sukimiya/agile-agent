package io.github.sukimiya.agileagent.memory.jobs;

/**
 * Durable background job kinds stored in {@code memory_jobs.job_type}.
 */
public enum JobType {
    /** Physical cleanup of a tombstoned conversation (M1 deletion phase two). */
    CONVERSATION_CLEANUP,
    /** Cut a completed turn into recall chunks (M2a). */
    INDEX_CHUNKS,
    /** Write turn memory records for an idle conversation (M2a). */
    SUMMARIZE,
    /** Judge long-term memory items from idle turns (M2b, D10). */
    JUDGE_MEMORY,
    /** Rebuild the user memory profile (M2b). */
    REBUILD_PROFILE,
    /** Replay judgement under a new Rules version (M2b, D13). */
    REPLAY_RULES
}
