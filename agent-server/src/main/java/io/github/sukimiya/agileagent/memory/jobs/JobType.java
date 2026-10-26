package io.github.sukimiya.agileagent.memory.jobs;

/**
 * Durable background job kinds stored in {@code memory_jobs.job_type}. M2b adds {@code JUDGE_MEMORY},
 * {@code REBUILD_PROFILE} and {@code REPLAY_RULES}.
 */
public enum JobType {
    /** Physical cleanup of a tombstoned conversation (M1 deletion phase two). */
    CONVERSATION_CLEANUP,
    /** Cut a completed turn into recall chunks (M2a). */
    INDEX_CHUNKS,
    /** Summarize an older range of a conversation (M2a). */
    SUMMARIZE
}
