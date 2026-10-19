package io.github.sukimiya.medical.rules;

import java.util.List;

/**
 * E-call action: task priority, initial SLA window, per-level escalation delays (seconds),
 * and the two pre-approved fixed scripts (等待接管 / 已接管). Scripts are user-facing
 * Chinese, screened at validation time (Ruling R2).
 */
public record EmergencyAction(
        int priority, int slaSeconds, List<Integer> escalationLevels,
        String awaitingTakeoverScript, String takenOverScript) {
}
