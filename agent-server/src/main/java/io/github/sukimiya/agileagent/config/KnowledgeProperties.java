package io.github.sukimiya.agileagent.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "app.knowledge")
public class KnowledgeProperties {
    private String tempDir;
    private String libDir;
    private int maxSizeMb = 5;
    private Duration summaryTimeout = Duration.ofSeconds(30);
    private Duration approvalWorkflowTimeout = Duration.ofMinutes(5);
    private Duration approvalCompensationTimeout = Duration.ofMinutes(3);
    private Duration approvalClaimLease = Duration.ofMinutes(15);
    private Duration approvalClaimRenewalInterval = Duration.ofMinutes(1);
    private Duration approvalClaimSafetyMargin = Duration.ofMinutes(2);
    private int approvalClaimReleaseMaxAttempts = 3;
    private Duration approvalClaimReleaseRetryDelay = Duration.ofMillis(100);

    public String getTempDir() { return tempDir; }
    public void setTempDir(String v) { this.tempDir = v; }
    public String getLibDir() { return libDir; }
    public void setLibDir(String v) { this.libDir = v; }
    public int getMaxSizeMb() { return maxSizeMb; }
    public void setMaxSizeMb(int v) { this.maxSizeMb = v; }
    public Duration getSummaryTimeout() { return summaryTimeout; }
    public void setSummaryTimeout(Duration v) { this.summaryTimeout = v; }
    public Duration getApprovalWorkflowTimeout() { return approvalWorkflowTimeout; }
    public void setApprovalWorkflowTimeout(Duration v) { this.approvalWorkflowTimeout = v; }
    public Duration getApprovalCompensationTimeout() { return approvalCompensationTimeout; }
    public void setApprovalCompensationTimeout(Duration v) { this.approvalCompensationTimeout = v; }
    public Duration getApprovalClaimLease() { return approvalClaimLease; }
    public void setApprovalClaimLease(Duration v) { this.approvalClaimLease = v; }
    public Duration getApprovalClaimRenewalInterval() { return approvalClaimRenewalInterval; }
    public void setApprovalClaimRenewalInterval(Duration v) { this.approvalClaimRenewalInterval = v; }
    public Duration getApprovalClaimSafetyMargin() { return approvalClaimSafetyMargin; }
    public void setApprovalClaimSafetyMargin(Duration v) { this.approvalClaimSafetyMargin = v; }
    public int getApprovalClaimReleaseMaxAttempts() { return approvalClaimReleaseMaxAttempts; }
    public void setApprovalClaimReleaseMaxAttempts(int v) { this.approvalClaimReleaseMaxAttempts = v; }
    public Duration getApprovalClaimReleaseRetryDelay() { return approvalClaimReleaseRetryDelay; }
    public void setApprovalClaimReleaseRetryDelay(Duration v) { this.approvalClaimReleaseRetryDelay = v; }

    public long getMaxSizeBytes() { return maxSizeMb * 1024L * 1024L; }

    public void validateApprovalClaimSettings() {
        if (!positive(approvalWorkflowTimeout) || !positive(approvalCompensationTimeout)
                || !positive(approvalClaimLease) || !positive(approvalClaimRenewalInterval)
                || !positive(approvalClaimSafetyMargin)
                || !positive(approvalClaimReleaseRetryDelay) || approvalClaimReleaseMaxAttempts < 1
                || approvalClaimReleaseMaxAttempts > 10) {
            throw new IllegalArgumentException(
                    "knowledge approval claim durations must be positive and release attempts must be between 1 and 10");
        }
        var minimumLease = approvalWorkflowTimeout.plus(approvalCompensationTimeout)
                .plus(approvalClaimSafetyMargin);
        if (approvalClaimLease.compareTo(minimumLease) <= 0) {
            throw new IllegalArgumentException(
                    "knowledge approval claim lease must exceed workflow and compensation timeouts plus safety margin");
        }
        if (approvalClaimRenewalInterval.compareTo(approvalClaimLease.dividedBy(2)) >= 0) {
            throw new IllegalArgumentException(
                    "knowledge approval claim renewal interval must be less than half the lease");
        }
        long leaseSeconds = approvalClaimLease.getSeconds() + (approvalClaimLease.getNano() > 0 ? 1 : 0);
        if (leaseSeconds > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("knowledge approval claim lease must not exceed the supported range");
        }
    }

    private static boolean positive(Duration value) {
        return value != null && !value.isZero() && !value.isNegative();
    }
}
