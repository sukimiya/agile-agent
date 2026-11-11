package io.github.sukimiya.agileagent.llmconfig;

import java.net.URI;

import io.github.sukimiya.agileagent.llmconfig.LlmProviderPresetRepository.LlmProviderPresetRow;

/** Resolves a preset's balance URL. A balance base URL, when set, replaces the chat base URL. */
public final class BalanceEndpoint {

    private BalanceEndpoint() {}

    public static URI resolve(LlmProviderPresetRow preset, String teamId) {
        if (preset.balancePath() == null || preset.balancePath().isBlank()) {
            throw LlmBalanceException.unsupported();
        }
        String path = BalancePlaceholders.apply(preset.balancePath().trim(), teamId);
        String base = preset.balanceBaseUrl() == null || preset.balanceBaseUrl().isBlank()
                ? preset.baseUrl() : preset.balanceBaseUrl();
        URI baseUri = URI.create(base.endsWith("/") ? base : base + "/");
        URI relative = URI.create(path);
        if (relative.isAbsolute() || relative.getRawAuthority() != null || relative.getRawFragment() != null) {
            throw LlmBalanceException.providerError();
        }
        URI target = baseUri.resolve(relative);
        if (!sameAuthority(baseUri, target)) {
            throw LlmBalanceException.providerError();
        }
        return target;
    }

    private static boolean sameAuthority(URI base, URI target) {
        return base.getScheme() != null && base.getScheme().equalsIgnoreCase(target.getScheme())
                && base.getHost() != null && base.getHost().equalsIgnoreCase(target.getHost())
                && base.getPort() == target.getPort();
    }
}
