package io.github.sukimiya.agileagent.memory;

import java.nio.charset.StandardCharsets;
import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

import io.github.sukimiya.agileagent.memory.MemoryException.ErrorCode;

/**
 * Tunables of the durable memory runtime ({@code app.memory.*}); defaults are the values fixed in the M1 plan.
 * Cache and instance-heartbeat values are wired by later tasks.
 */
@ConfigurationProperties(prefix = "app.memory")
public class MemoryProperties {

    /** MySQL {@code MEDIUMTEXT} capacity in bytes; the application limit must stay below it. */
    public static final int MEDIUMTEXT_MAX_BYTES = 16 * 1024 * 1024 - 1;

    /** Deployment namespace; scopes cache keys and the single-instance heartbeat. */
    private String namespace = "default";
    /** Maximum UTF-8 size of one serialized message or tool record; larger ones are rejected, never truncated. */
    private int maxMessageBytes = 1024 * 1024;
    private Duration leaseDuration = Duration.ofSeconds(60);
    private Duration leaseRenewInterval = Duration.ofSeconds(15);
    private Duration recoveryScanInterval = Duration.ofSeconds(30);
    private Duration cleanupScanInterval = Duration.ofSeconds(30);
    /** How long an Idempotency-Key stays bound to its turn (counted from acceptance); then it is released. */
    private Duration idempotencyKeyRetention = Duration.ofHours(24);
    /** Runs the expired-turn recovery scan at startup and every {@link #recoveryScanInterval}. */
    private boolean recoveryEnabled = true;
    private Duration checkpointInterval = Duration.ofSeconds(5);
    private int checkpointBytes = 8 * 1024;
    private Duration finalizerTimeout = Duration.ofSeconds(5);
    private int cacheMaxMessages = 100;
    private int cacheMaxBytes = 256 * 1024;
    private Duration cacheIdleTtl = Duration.ofDays(7);
    private Duration instanceHeartbeatInterval = Duration.ofSeconds(10);
    private Duration instanceStaleAfter = Duration.ofSeconds(30);
    private boolean instanceGuardEnabled = true;
    private final Context context = new Context();

    public Context getContext() { return context; }

    private final Summary summary = new Summary();

    public Summary getSummary() { return summary; }

    private final Recall recall = new Recall();

    public Recall getRecall() { return recall; }

    private final Consolidation consolidation = new Consolidation();

    public Consolidation getConsolidation() { return consolidation; }

    private final Judgement judgement = new Judgement();

    public Judgement getJudgement() { return judgement; }

    private final Purge purge = new Purge();

    public Purge getPurge() { return purge; }

    /**
     * Periodic purge of derived index rows of deleted sources (M2b Q4 redesign, {@code app.memory.purge.*}).
     * {@code tiers}: "from this recall reference count on, keep for this long", ascending, first from 0; the
     * number of tiers is free. Validated by {@code RetentionTiers.of}.
     */
    public static class Purge {

        private boolean enabled = true;
        private Duration interval = Duration.ofHours(1);
        private int batch = 200;
        private java.util.List<Tier> tiers = new java.util.ArrayList<>(java.util.List.of(
                Tier.of(0, Duration.ofDays(1)), Tier.of(2, Duration.ofDays(7)), Tier.of(5, Duration.ofDays(30))));

        public boolean isEnabled() { return enabled; }

        public void setEnabled(boolean enabled) { this.enabled = enabled; }

        public Duration getInterval() { return interval; }

        public void setInterval(Duration interval) { this.interval = interval; }

        public int getBatch() { return batch; }

        public void setBatch(int batch) { this.batch = batch; }

        public java.util.List<Tier> getTiers() { return tiers; }

        public void setTiers(java.util.List<Tier> tiers) { this.tiers = tiers; }

        /** One retention tier: reference count from {@code fromCount} (inclusive) on, keep for {@code keep}. */
        public static class Tier {

            private long fromCount;
            private Duration keep;

            public static Tier of(long fromCount, Duration keep) {
                var tier = new Tier();
                tier.setFromCount(fromCount);
                tier.setKeep(keep);
                return tier;
            }

            public long getFromCount() { return fromCount; }

            public void setFromCount(long fromCount) { this.fromCount = fromCount; }

            public Duration getKeep() { return keep; }

            public void setKeep(Duration keep) { this.keep = keep; }
        }
    }

    private final Profile profile = new Profile();

    public Profile getProfile() { return profile; }

    /** {@code app.memory.profile.*}: user memory profile snapshot (M2b Task 4, D14). */
    public static class Profile {
        /** Max estimated tokens of the rebuilt profile text (D14). */
        private int maxTokens = 800;
        /** Redis snapshot TTL (idle refresh on hit). */
        private java.time.Duration cacheTtl = java.time.Duration.ofHours(1);
        /** Max one-hop relations injected with a recall. */
        private int maxRelations = 10;

        public int getMaxTokens() { return maxTokens; }
        public void setMaxTokens(int v) {
            if (v <= 0) {
                throw new IllegalArgumentException("app.memory.profile.max-tokens must be > 0");
            }
            this.maxTokens = v;
        }
        public java.time.Duration getCacheTtl() { return cacheTtl; }
        public void setCacheTtl(java.time.Duration v) {
            if (v == null || v.isNegative() || v.isZero()) {
                throw new IllegalArgumentException("app.memory.profile.cache-ttl must be positive");
            }
            this.cacheTtl = v;
        }
        public int getMaxRelations() { return maxRelations; }
        public void setMaxRelations(int v) {
            if (v <= 0) {
                throw new IllegalArgumentException("app.memory.profile.max-relations must be > 0");
            }
            this.maxRelations = v;
        }
    }

    /**
     * {@code app.memory.judgement.*}: idle-triggered LLM memory judgement (M2b Task 3, D10).
     * Shares idle detection with {@link Summary} (D19).
     */
    public static class Judgement {
        /** Off by default (cost control); without a handler JUDGE_MEMORY jobs wait unclaimed. */
        private boolean enabled;
        /** {@code max_tokens} of the judgement call. */
        private int maxOutputTokens = 1_200;
        /** Upper bound of one judgement model call. */
        private java.time.Duration jobTimeout = java.time.Duration.ofSeconds(120);
        /** Largest transcript (estimated tokens) one judgement call sends. */
        private int maxSourceTokens = 12_000;

        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean v) { this.enabled = v; }
        public int getMaxOutputTokens() { return maxOutputTokens; }
        public void setMaxOutputTokens(int v) {
            if (v <= 0) {
                throw new IllegalArgumentException("app.memory.judgement.max-output-tokens must be > 0");
            }
            this.maxOutputTokens = v;
        }
        public java.time.Duration getJobTimeout() { return jobTimeout; }
        public void setJobTimeout(java.time.Duration v) {
            if (v == null || v.isNegative() || v.isZero()) {
                throw new IllegalArgumentException("app.memory.judgement.job-timeout must be positive");
            }
            this.jobTimeout = v;
        }
        public int getMaxSourceTokens() { return maxSourceTokens; }
        public void setMaxSourceTokens(int v) {
            if (v <= 0) {
                throw new IllegalArgumentException("app.memory.judgement.max-source-tokens must be > 0");
            }
            this.maxSourceTokens = v;
        }
    }

    /**
     * {@code app.memory.consolidation.*}: per-tenant daily token budget and concurrency for background LLM
     * jobs (SUMMARIZE / JUDGE_MEMORY / REBUILD_PROFILE / REPLAY_RULES). D11=A.
     */
    public static class Consolidation {
        /**
         * When true, registers {@code memory.consolidation.backlog} and sets
         * {@code memory.feature.enabled{feature=consolidation}}=1. Quota enforcement for LLM jobs is always on
         * once the bean is wired (cost control from day one); this flag only gates the backlog gauge.
         */
        private boolean enabled;
        /** Global default daily token limit; overridden per tenant by {@code memory_tenant_quotas}. */
        private long dailyTokenLimit = 200_000L;
        /** Max concurrent LLM consolidation jobs per tenant on this instance. */
        private int maxConcurrentPerTenant = 1;
        /** Tokens reserved up-front when claiming one LLM job (settled to actual usage afterwards). */
        private long reserveTokens = 12_800L;

        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean v) { this.enabled = v; }
        public long getDailyTokenLimit() { return dailyTokenLimit; }
        public void setDailyTokenLimit(long v) {
            if (v <= 0) {
                throw new IllegalArgumentException("app.memory.consolidation.daily-token-limit must be > 0");
            }
            this.dailyTokenLimit = v;
        }
        public int getMaxConcurrentPerTenant() { return maxConcurrentPerTenant; }
        public void setMaxConcurrentPerTenant(int v) {
            if (v <= 0) {
                throw new IllegalArgumentException("app.memory.consolidation.max-concurrent-per-tenant must be > 0");
            }
            this.maxConcurrentPerTenant = v;
        }
        public long getReserveTokens() { return reserveTokens; }
        public void setReserveTokens(long v) {
            if (v <= 0) {
                throw new IllegalArgumentException("app.memory.consolidation.reserve-tokens must be > 0");
            }
            this.reserveTokens = v;
        }
    }

    /** {@code app.memory.recall.*}: basic recall over turn records and original-text chunks (M2a Task 5, D22/D24). */
    public static class Recall {
        /** Off: no retrieval and no reference message, prompts as before Task 5 (chunks are still indexed). */
        private boolean enabled = true;
        /** At most this many recalled turns per call (D24). */
        private int maxItems = 5;
        /** Share of the history budget the reference message may take, only when there are hits (D24). */
        private double budgetShare = 0.25;
        /** Candidates kept per turn after merging, before the budget. */
        private int candidateLimit = 20;
        /** A full-text hit is trusted only with at least this many distinct query terms in it. */
        private int minMatchedTerms = 2;
        /** Cross-conversation recall searches at most this many of the owner's newest other conversations. */
        private int maxConversations = 50;
        /** Un-indexed completed turns of the current conversation scanned directly per recall. */
        private int backfillTurns = 20;
        /** Newest turn records scanned for entity names contained in the question. */
        private int entityScanRecords = 200;
        /** Chunk size limit in estimated tokens; longer turns are split by paragraph. */
        private int chunkMaxTokens = 1_500;

        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean v) { this.enabled = v; }
        public int getMaxItems() { return maxItems; }
        public void setMaxItems(int v) { this.maxItems = positive("max-items", v); }
        public double getBudgetShare() { return budgetShare; }
        public void setBudgetShare(double v) {
            if (!(v > 0 && v <= 1)) {
                throw new IllegalArgumentException("app.memory.recall.budget-share must be in (0, 1]");
            }
            this.budgetShare = v;
        }
        public int getCandidateLimit() { return candidateLimit; }
        public void setCandidateLimit(int v) { this.candidateLimit = positive("candidate-limit", v); }
        public int getMinMatchedTerms() { return minMatchedTerms; }
        public void setMinMatchedTerms(int v) { this.minMatchedTerms = positive("min-matched-terms", v); }
        public int getMaxConversations() { return maxConversations; }
        public void setMaxConversations(int v) { this.maxConversations = positive("max-conversations", v); }
        public int getBackfillTurns() { return backfillTurns; }
        public void setBackfillTurns(int v) { this.backfillTurns = positive("backfill-turns", v); }
        public int getEntityScanRecords() { return entityScanRecords; }
        public void setEntityScanRecords(int v) { this.entityScanRecords = positive("entity-scan-records", v); }
        public int getChunkMaxTokens() { return chunkMaxTokens; }
        public void setChunkMaxTokens(int v) { this.chunkMaxTokens = positive("chunk-max-tokens", v); }

        private static int positive(String name, int v) {
            if (v <= 0) {
                throw new IllegalArgumentException("app.memory.recall." + name + " must be positive");
            }
            return v;
        }
    }

    /** {@code app.memory.summary.*}: background segment summaries (M2a Task 4, D4/D5). */
    public static class Summary {
        /** Summary generation; off by default (D4). Off: no SUMMARIZE handler, jobs wait unclaimed. */
        private boolean enabled;
        /** Largest transcript one job sends, in estimated tokens (D5 source range). */
        private int maxSourceTokens = 12_000;
        /** {@code max_tokens} of the summary call (D5). */
        private int maxOutputTokens = 800;
        /** Upper bound of one summary call. */
        private java.time.Duration jobTimeout = java.time.Duration.ofSeconds(120);
        /**
         * No new message for this long (and no active turn) means the conversation is idle and may be
         * enqueued (D19, 2026-10-26): default 1 minute. Shared with judgement enqueue (D10).
         */
        private java.time.Duration idleTimeout = java.time.Duration.ofMinutes(1);
        /**
         * Latest fallback (D19): by this age since the last message, pending record/judgement work must still
         * be picked up. Effective cutoff is {@code min(idleTimeout, idleDeadline)} so a mis-set long timeout
         * cannot delay past the deadline. Default 30 minutes.
         */
        private java.time.Duration idleDeadline = java.time.Duration.ofMinutes(30);
        /** How often the idle scan looks for idle conversations (D19): default 30 seconds. */
        private java.time.Duration idleScanInterval = java.time.Duration.ofSeconds(30);
        /** Conversations enqueued per idle scan at most. */
        private int idleScanBatch = 100;
        /** Invalid model outputs for one turn before the turn is skipped (D23). */
        private int maxInvalidAttempts = 3;

        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean v) { this.enabled = v; }
        public int getMaxSourceTokens() { return maxSourceTokens; }
        public void setMaxSourceTokens(int v) {
            if (v <= 0) {
                throw new IllegalArgumentException("app.memory.summary.max-source-tokens must be > 0");
            }
            this.maxSourceTokens = v;
        }
        public int getMaxOutputTokens() { return maxOutputTokens; }
        public void setMaxOutputTokens(int v) {
            if (v <= 0) {
                throw new IllegalArgumentException("app.memory.summary.max-output-tokens must be > 0");
            }
            this.maxOutputTokens = v;
        }
        public java.time.Duration getJobTimeout() { return jobTimeout; }
        public java.time.Duration getIdleTimeout() { return idleTimeout; }
        public void setIdleTimeout(java.time.Duration v) {
            this.idleTimeout = requirePositive("idle-timeout", v);
        }
        public java.time.Duration getIdleDeadline() { return idleDeadline; }
        public void setIdleDeadline(java.time.Duration v) {
            this.idleDeadline = requirePositive("idle-deadline", v);
        }
        /**
         * Cutoff used by the idle scan: the sooner of {@link #getIdleTimeout()} and {@link #getIdleDeadline()},
         * so the 30-minute deadline remains a hard upper bound if timeout is misconfigured longer.
         */
        public java.time.Duration effectiveIdleCutoff() {
            return idleTimeout.compareTo(idleDeadline) <= 0 ? idleTimeout : idleDeadline;
        }
        public java.time.Duration getIdleScanInterval() { return idleScanInterval; }
        public void setIdleScanInterval(java.time.Duration v) {
            this.idleScanInterval = requirePositive("idle-scan-interval", v);
        }
        public int getIdleScanBatch() { return idleScanBatch; }
        public void setIdleScanBatch(int v) { this.idleScanBatch = v; }
        public int getMaxInvalidAttempts() { return maxInvalidAttempts; }
        public void setMaxInvalidAttempts(int v) { this.maxInvalidAttempts = v; }
        public void setJobTimeout(java.time.Duration v) {
            this.jobTimeout = requirePositive("job-timeout", v);
        }

        private static java.time.Duration requirePositive(String name, java.time.Duration v) {
            if (v == null || v.isNegative() || v.isZero()) {
                throw new IllegalArgumentException("app.memory.summary." + name + " must be positive");
            }
            return v;
        }
    }

    /** {@code app.memory.context.*}: per-call context budget (M2a). */
    public static class Context {
        /** Budget enforcement; off means every call gets the full history (M1 behaviour). */
        private boolean enabled = true;
        /** Window used when the provider preset leaves it unset (D2). */
        private int defaultContextWindowTokens = 32_768;
        /** Output reserve used when the provider preset leaves it unset (D2). */
        private int defaultMaxOutputTokens = 4_096;
        /** Safety factor on the heuristic token estimate (D3). */
        private double estimateFactor = 1.3;
        /**
         * Share of a capability call's remaining budget its background (recent conversation) may use, so tool
         * results keep the rest (D5 recent-window share; the two newest turns are kept whenever they fit).
         */
        private double capabilityBackgroundShare = 0.5;

        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean v) { this.enabled = v; }
        public int getDefaultContextWindowTokens() { return defaultContextWindowTokens; }
        public void setDefaultContextWindowTokens(int v) {
            if (v <= 1) {
                throw new IllegalArgumentException("app.memory.context.default-context-window-tokens must be > 1");
            }
            this.defaultContextWindowTokens = v;
        }
        public int getDefaultMaxOutputTokens() { return defaultMaxOutputTokens; }
        public void setDefaultMaxOutputTokens(int v) {
            if (v <= 0) {
                throw new IllegalArgumentException("app.memory.context.default-max-output-tokens must be > 0");
            }
            this.defaultMaxOutputTokens = v;
        }
        public double getEstimateFactor() { return estimateFactor; }
        public void setEstimateFactor(double v) {
            if (!(v >= 1.0)) {
                throw new IllegalArgumentException("app.memory.context.estimate-factor must be >= 1");
            }
            this.estimateFactor = v;
        }
        public double getCapabilityBackgroundShare() { return capabilityBackgroundShare; }
        public void setCapabilityBackgroundShare(double v) {
            if (!(v > 0.0 && v <= 1.0)) {
                throw new IllegalArgumentException("app.memory.context.capability-background-share must be in (0, 1]");
            }
            this.capabilityBackgroundShare = v;
        }
    }

    /** Rejects a serialized message over {@link #getMaxMessageBytes()} with {@link ErrorCode#MESSAGE_TOO_LARGE}. */
    public void checkMessageSize(String serialized) {
        if (serialized != null && serialized.getBytes(StandardCharsets.UTF_8).length > maxMessageBytes) {
            throw new MemoryException(ErrorCode.MESSAGE_TOO_LARGE);
        }
    }

    public String getNamespace() { return namespace; }
    public void setNamespace(String namespace) {
        if (namespace == null || namespace.isBlank()) {
            throw new IllegalArgumentException("app.memory.namespace must not be blank");
        }
        this.namespace = namespace;
    }
    public int getMaxMessageBytes() { return maxMessageBytes; }
    public void setMaxMessageBytes(int maxMessageBytes) {
        if (maxMessageBytes <= 0 || maxMessageBytes > MEDIUMTEXT_MAX_BYTES) {
            throw new IllegalArgumentException("app.memory.max-message-bytes must be within (0, " + MEDIUMTEXT_MAX_BYTES + "]");
        }
        this.maxMessageBytes = maxMessageBytes;
    }
    public Duration getLeaseDuration() { return leaseDuration; }
    public void setLeaseDuration(Duration v) { this.leaseDuration = v; }
    public Duration getLeaseRenewInterval() { return leaseRenewInterval; }
    public void setLeaseRenewInterval(Duration v) { this.leaseRenewInterval = v; }
    public Duration getRecoveryScanInterval() { return recoveryScanInterval; }
    public void setRecoveryScanInterval(Duration v) { this.recoveryScanInterval = v; }
    public Duration getCleanupScanInterval() { return cleanupScanInterval; }
    public void setCleanupScanInterval(Duration v) { this.cleanupScanInterval = v; }
    public Duration getIdempotencyKeyRetention() { return idempotencyKeyRetention; }
    public void setIdempotencyKeyRetention(Duration v) { this.idempotencyKeyRetention = v; }
    public boolean isRecoveryEnabled() { return recoveryEnabled; }
    public void setRecoveryEnabled(boolean v) { this.recoveryEnabled = v; }
    public Duration getCheckpointInterval() { return checkpointInterval; }
    public void setCheckpointInterval(Duration v) { this.checkpointInterval = v; }
    public int getCheckpointBytes() { return checkpointBytes; }
    public void setCheckpointBytes(int v) { this.checkpointBytes = v; }
    public Duration getFinalizerTimeout() { return finalizerTimeout; }
    public void setFinalizerTimeout(Duration v) { this.finalizerTimeout = v; }
    public int getCacheMaxMessages() { return cacheMaxMessages; }
    public void setCacheMaxMessages(int v) { this.cacheMaxMessages = v; }
    public int getCacheMaxBytes() { return cacheMaxBytes; }
    public void setCacheMaxBytes(int v) { this.cacheMaxBytes = v; }
    public Duration getCacheIdleTtl() { return cacheIdleTtl; }
    public void setCacheIdleTtl(Duration v) { this.cacheIdleTtl = v; }
    public Duration getInstanceHeartbeatInterval() { return instanceHeartbeatInterval; }
    public void setInstanceHeartbeatInterval(Duration v) { this.instanceHeartbeatInterval = v; }
    public Duration getInstanceStaleAfter() { return instanceStaleAfter; }
    /** Turns the heartbeat schedule off (tests without memory tables); production keeps it on. */
    public boolean isInstanceGuardEnabled() { return instanceGuardEnabled; }
    public void setInstanceGuardEnabled(boolean v) { this.instanceGuardEnabled = v; }
    public void setInstanceStaleAfter(Duration v) { this.instanceStaleAfter = v; }
}
