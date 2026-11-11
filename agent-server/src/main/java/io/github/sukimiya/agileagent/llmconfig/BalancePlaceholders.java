package io.github.sukimiya.agileagent.llmconfig;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/** Fills {@code {teamId}} and {@code $TEAM_ID} in a balance path from the tenant's team id. */
public final class BalancePlaceholders {

    public static final String BRACE = "{teamId}";
    public static final String DOLLAR = "$TEAM_ID";

    private BalancePlaceholders() {}

    public static boolean needsTeamId(String path) {
        return path != null && (path.contains(BRACE) || path.contains(DOLLAR));
    }

    public static String apply(String path, String teamId) {
        if (!needsTeamId(path)) {
            return path;
        }
        if (teamId == null || teamId.isBlank()) {
            throw LlmBalanceException.teamRequired();
        }
        String encoded = URLEncoder.encode(teamId.trim(), StandardCharsets.UTF_8).replace("+", "%20");
        return path.replace(BRACE, encoded).replace(DOLLAR, encoded);
    }
}
