package io.github.sukimiya.agileagent.llmconfig;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.core.JsonPointer;
import com.fasterxml.jackson.databind.JsonNode;

import io.github.sukimiya.agileagent.llmconfig.LlmProviderPresetRepository.LlmProviderPresetRow;

/**
 * Turns a provider balance body into currency/amount rows. With no pointers, an object or array of
 * {@code {currency, balance}} is used. Pointers select one amount; currency comes from a pointer, a preset
 * literal, or {@link LlmBalanceProperties#getDefaultCurrency()}.
 */
public final class BalanceResponseReader {

    private BalanceResponseReader() {}

    public static List<LlmBalanceService.BalanceAmount> read(JsonNode body, LlmProviderPresetRow preset,
            LlmBalanceProperties defaults) {
        if (body == null || body.isNull()) {
            throw LlmBalanceException.providerError();
        }
        if (preset.balanceAmountPointer() == null || preset.balanceAmountPointer().isBlank()) {
            return legacy(body);
        }
        JsonNode amountNode = body.at(pointer(preset.balanceAmountPointer()));
        if (amountNode.isMissingNode() || amountNode.isNull()) {
            throw LlmBalanceException.providerError();
        }
        BigDecimal amount = decimal(amountNode);
        if (Boolean.TRUE.equals(preset.balanceNegate())) {
            amount = amount.negate();
        }
        int divisor = preset.balanceDivisor() != null ? preset.balanceDivisor() : defaults.getDefaultDivisor();
        if (divisor < 1) {
            throw LlmBalanceException.providerError();
        }
        if (divisor != 1) {
            amount = amount.divide(BigDecimal.valueOf(divisor), 2, RoundingMode.HALF_UP);
        }
        return List.of(new LlmBalanceService.BalanceAmount(currency(body, preset, defaults), plain(amount, divisor)));
    }

    private static List<LlmBalanceService.BalanceAmount> legacy(JsonNode body) {
        if (body.isArray()) {
            List<LlmBalanceService.BalanceAmount> rows = new ArrayList<>();
            body.forEach(node -> rows.add(legacyOne(node)));
            if (rows.isEmpty()) {
                throw LlmBalanceException.providerError();
            }
            return rows;
        }
        if (body.isObject() && body.has("balance")) {
            return List.of(legacyOne(body));
        }
        throw LlmBalanceException.providerError();
    }

    private static LlmBalanceService.BalanceAmount legacyOne(JsonNode node) {
        if (!node.hasNonNull("balance")) {
            throw LlmBalanceException.providerError();
        }
        String currency = node.path("currency").asText("");
        return new LlmBalanceService.BalanceAmount(currency, node.get("balance").asText());
    }

    private static String currency(JsonNode body, LlmProviderPresetRow preset, LlmBalanceProperties defaults) {
        if (preset.balanceCurrencyPointer() != null && !preset.balanceCurrencyPointer().isBlank()) {
            JsonNode node = body.at(pointer(preset.balanceCurrencyPointer()));
            if (!node.isMissingNode() && !node.isNull() && !node.asText().isBlank()) {
                return node.asText();
            }
        }
        if (preset.balanceCurrency() != null && !preset.balanceCurrency().isBlank()) {
            return preset.balanceCurrency().trim();
        }
        return defaults.getDefaultCurrency();
    }

    private static JsonPointer pointer(String value) {
        try {
            return JsonPointer.compile(value.trim());
        } catch (IllegalArgumentException exception) {
            throw LlmBalanceException.providerError();
        }
    }

    private static BigDecimal decimal(JsonNode node) {
        try {
            return new BigDecimal(node.asText().trim());
        } catch (NumberFormatException exception) {
            throw LlmBalanceException.providerError();
        }
    }

    private static String plain(BigDecimal amount, int divisor) {
        if (divisor == 1) {
            return amount.stripTrailingZeros().toPlainString();
        }
        return amount.toPlainString();
    }
}
