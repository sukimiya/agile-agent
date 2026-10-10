package io.github.sukimiya.agileagent.llmconfig;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Base64;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

class ApiKeyCryptoTest {

    private static final String VALID_SECRET = Base64.getEncoder().encodeToString(new byte[32]);

    @Test
    void encryptAndDecryptRoundTripUtf8ApiKey() {
        ApiKeyCrypto crypto = new ApiKeyCrypto(VALID_SECRET);
        String apiKey = "sk-测试-🔐";

        String encrypted = crypto.encrypt(apiKey);

        assertThat(encrypted).isNotEqualTo(apiKey);
        assertThat(Base64.getDecoder().decode(encrypted)).hasSize(12 + apiKey.getBytes(java.nio.charset.StandardCharsets.UTF_8).length + 16);
        assertThat(crypto.decrypt(encrypted)).isEqualTo(apiKey);
    }

    @Test
    void encryptUsesANewRandomIvForEachEncryption() {
        ApiKeyCrypto crypto = new ApiKeyCrypto(VALID_SECRET);

        assertThat(crypto.encrypt("same API key")).isNotEqualTo(crypto.encrypt("same API key"));
    }

    @Test
    void constructorRejectsMissingBlankMalformedAndWrongLengthSecretsWithoutEchoingThem() {
        for (String secret : new String[] { null, "   ", "not-base64?", Base64.getEncoder().encodeToString(new byte[31]) }) {
            var failure = assertThatThrownBy(() -> new ApiKeyCrypto(secret))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("LLM_CONFIG_SECRET must be valid Base64 encoding of exactly 32 bytes")
                    .hasNoCause();
            if (secret != null) failure.hasMessageNotContaining(secret);
        }
    }

    @Test
    void cryptoBeanFailsFastInSpringContextWhenSecretIsMissingOrInvalid() {
        new ApplicationContextRunner().withUserConfiguration(CryptoConfiguration.class)
                .withPropertyValues("llm.config.secret=")
                .run(context -> assertThat(context).hasFailed());

        new ApplicationContextRunner().withUserConfiguration(CryptoConfiguration.class)
                .withPropertyValues("llm.config.secret=not-base64?")
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void cryptoBeanStartsInSpringContextWithValidSecret() {
        new ApplicationContextRunner().withUserConfiguration(CryptoConfiguration.class)
                .withPropertyValues("llm.config.secret=" + VALID_SECRET)
                .run(context -> assertThat(context).hasNotFailed().hasSingleBean(ApiKeyCrypto.class));
    }

    @Test
    void decryptRejectsPayloadShorterThanIvAndGcmTagWithoutEchoingCiphertext() {
        String truncated = Base64.getEncoder().encodeToString(new byte[27]);
        ApiKeyCrypto crypto = new ApiKeyCrypto(VALID_SECRET);

        assertThatThrownBy(() -> crypto.decrypt(truncated))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Encrypted API key payload is invalid")
                .hasNoCause()
                .hasMessageNotContaining(truncated);
    }

    @Test
    void wrongKeyAndTamperedCiphertextFailWithoutEchoingSecrets() {
        String plaintext = "sk-super-secret-value";
        ApiKeyCrypto writer = new ApiKeyCrypto(VALID_SECRET);
        String ciphertext = writer.encrypt(plaintext);
        byte[] differentKey = new byte[32];
        differentKey[0] = 1;
        ApiKeyCrypto reader = new ApiKeyCrypto(Base64.getEncoder().encodeToString(differentKey));

        assertThatThrownBy(() -> reader.decrypt(ciphertext))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Encrypted API key payload is invalid")
                .hasNoCause()
                .hasMessageNotContaining(plaintext)
                .hasMessageNotContaining(ciphertext);
    }

    @Test
    void nullInputsFailClosedWithoutEchoingValues() {
        ApiKeyCrypto crypto = new ApiKeyCrypto(VALID_SECRET);

        assertThatThrownBy(() -> crypto.encrypt(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("API key is required")
                .hasNoCause();
        assertThatThrownBy(() -> crypto.decrypt(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Encrypted API key payload is invalid")
                .hasNoCause();
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(io.github.sukimiya.agileagent.config.LlmSecretProperties.class)
    @Import(ApiKeyCrypto.class)
    static class CryptoConfiguration {
    }
}
