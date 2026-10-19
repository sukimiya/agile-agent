package io.github.sukimiya.medical;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Operator configuration of the rule/E-call runtime (spec §9, ruling 5).
 * {@code medical.rules.escalation-poll-interval} — reactive poller period, default 30s.
 * {@code medical.rules.escalation-poller-enabled} — kill switch, default true.
 * {@code medical.rules.stale-claim-seconds} — CLAIMED-age threshold for lightweight crash
 * recovery, default 1800 (spec §8; not a full lease).
 * {@code medical.rules.stale-claim-release-enabled} — when false, stale claims are only
 * audited, never released back to PENDING (detect-only mode, spec §8).
 */
@ConfigurationProperties(prefix = "medical.rules")
public class MedicalRulesProperties {

    private Duration escalationPollInterval = Duration.ofSeconds(30);
    private boolean escalationPollerEnabled = true;
    private long staleClaimSeconds = 1800L;
    private boolean staleClaimReleaseEnabled = true;

    public Duration getEscalationPollInterval() {
        return escalationPollInterval;
    }

    public void setEscalationPollInterval(Duration escalationPollInterval) {
        this.escalationPollInterval = escalationPollInterval;
    }

    public boolean isEscalationPollerEnabled() {
        return escalationPollerEnabled;
    }

    public void setEscalationPollerEnabled(boolean escalationPollerEnabled) {
        this.escalationPollerEnabled = escalationPollerEnabled;
    }

    public long getStaleClaimSeconds() {
        return staleClaimSeconds;
    }

    public void setStaleClaimSeconds(long staleClaimSeconds) {
        this.staleClaimSeconds = staleClaimSeconds;
    }

    public boolean isStaleClaimReleaseEnabled() {
        return staleClaimReleaseEnabled;
    }

    public void setStaleClaimReleaseEnabled(boolean staleClaimReleaseEnabled) {
        this.staleClaimReleaseEnabled = staleClaimReleaseEnabled;
    }
}
