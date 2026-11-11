package io.github.sukimiya.agileagent.llmconfig;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDateTime;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.github.sukimiya.agileagent.llmconfig.LlmProviderPresetRepository.LlmProviderPresetRow;

class LlmBalanceParsingTest {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 11, 0, 0);

    @Test
    void placeholdersFillBraceAndDollarFormsAndRejectAMissingTeam() {
        assertThat(BalancePlaceholders.apply("/teams/{teamId}/balance", "team a"))
                .isEqualTo("/teams/team%20a/balance");
        assertThat(BalancePlaceholders.apply("/teams/$TEAM_ID/balance", "team a"))
                .isEqualTo("/teams/team%20a/balance");
        assertThatThrownBy(() -> BalancePlaceholders.apply("/teams/{teamId}/balance", " "))
                .isInstanceOf(LlmBalanceException.class)
                .hasFieldOrPropertyWithValue("status", 400)
                .hasFieldOrPropertyWithValue("code", "LLM_BALANCE_TEAM_REQUIRED");
    }

    @Test
    void balanceBaseUrlWinsAndARelativePathCannotChangeHost() {
        var preset = preset("https://chat.example/v1", "https://billing.example",
                "/v1/billing/teams/$TEAM_ID/prepaid/balance", null, null, null, null, null);
        assertThat(BalanceEndpoint.resolve(preset, "team-9").toString())
                .isEqualTo("https://billing.example/v1/billing/teams/team-9/prepaid/balance");

        var chatOnly = preset("https://chat.example/v1/", null, "balance", null, null, null, null, null);
        assertThat(BalanceEndpoint.resolve(chatOnly, null).toString())
                .isEqualTo("https://chat.example/v1/balance");

        var escaped = preset("https://chat.example/v1", null, "//evil.example/balance", null, null, null, null, null);
        assertThatThrownBy(() -> BalanceEndpoint.resolve(escaped, null))
                .isInstanceOf(LlmBalanceException.class)
                .hasFieldOrPropertyWithValue("status", 502);
    }

    @Test
    void prepaidCentsBecomeDollarsAndLegacyObjectsStayUnchanged() throws Exception {
        var prepaid = preset("https://chat.example/v1", "https://billing.example", "/balance",
                "/total/val", null, "USD", 100, true);
        var rows = BalanceResponseReader.read(
                JSON.readTree("{\"changes\":[],\"total\":{\"val\":\"-1000\"}}"), prepaid, new LlmBalanceProperties());
        assertThat(rows).containsExactly(new LlmBalanceService.BalanceAmount("USD", "10.00"));

        var legacy = preset("https://chat.example/v1", null, "/balance", null, null, null, null, null);
        var plain = BalanceResponseReader.read(
                JSON.readTree("[{\"currency\":\"CNY\",\"balance\":\"3.5\"}]"), legacy, new LlmBalanceProperties());
        assertThat(plain).containsExactly(new LlmBalanceService.BalanceAmount("CNY", "3.5"));
    }

    private static LlmProviderPresetRow preset(String baseUrl, String balanceBaseUrl, String path,
            String amountPointer, String currencyPointer, String currency, Integer divisor, Boolean negate) {
        return new LlmProviderPresetRow("id", "name", baseUrl, "model", path, "admin", NOW, NOW,
                null, null, balanceBaseUrl, amountPointer, currencyPointer, currency, divisor, negate);
    }
}
