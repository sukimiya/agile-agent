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
