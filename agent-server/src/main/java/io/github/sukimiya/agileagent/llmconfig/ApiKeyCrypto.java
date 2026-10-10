package io.github.sukimiya.agileagent.llmconfig;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import io.github.sukimiya.agileagent.config.LlmSecretProperties;

@Component
public final class ApiKeyCrypto {

    private static final int KEY_BYTES = 32;
    private static final int IV_BYTES = 12;
    private static final int TAG_BITS = 128;
    private static final int TAG_BYTES = TAG_BITS / 8;
    private static final SecureRandom RANDOM = new SecureRandom();

    private final byte[] key;

    @Autowired
    public ApiKeyCrypto(LlmSecretProperties properties) {
        this(properties.getSecret());
    }

    ApiKeyCrypto(String encodedKey) {
        byte[] decoded;
        try {
            if (encodedKey == null || encodedKey.isBlank()) throw new IllegalArgumentException();
            decoded = Base64.getDecoder().decode(encodedKey);
            if (decoded.length != KEY_BYTES || !Base64.getEncoder().encodeToString(decoded).equals(encodedKey)) {
                Arrays.fill(decoded, (byte) 0);
                throw new IllegalArgumentException();
            }
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("LLM_CONFIG_SECRET must be valid Base64 encoding of exactly 32 bytes");
        }
        this.key = decoded;
    }

    public String encrypt(String plaintext) {
        if (plaintext == null) throw new IllegalArgumentException("API key is required");
        byte[] iv = new byte[IV_BYTES];
        RANDOM.nextBytes(iv);
        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key, "AES"), new GCMParameterSpec(TAG_BITS, iv));
            byte[] encrypted = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(ByteBuffer.allocate(iv.length + encrypted.length)
                    .put(iv).put(encrypted).array());
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("API key encryption failed");
        }
    }

    public String decrypt(String ciphertext) {
        if (ciphertext == null) throw invalidPayload();
        byte[] payload;
        try {
            payload = Base64.getDecoder().decode(ciphertext);
        } catch (IllegalArgumentException exception) {
            throw invalidPayload();
        }
        if (!Base64.getEncoder().encodeToString(payload).equals(ciphertext)) {
            Arrays.fill(payload, (byte) 0);
            throw invalidPayload();
        }
        if (payload.length < IV_BYTES + TAG_BYTES) throw invalidPayload();

        byte[] iv = Arrays.copyOfRange(payload, 0, IV_BYTES);
        byte[] encrypted = Arrays.copyOfRange(payload, IV_BYTES, payload.length);
        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(key, "AES"), new GCMParameterSpec(TAG_BITS, iv));
            return new String(cipher.doFinal(encrypted), StandardCharsets.UTF_8);
        } catch (GeneralSecurityException exception) {
            throw invalidPayload();
        } finally {
            Arrays.fill(payload, (byte) 0);
            Arrays.fill(iv, (byte) 0);
            Arrays.fill(encrypted, (byte) 0);
        }
    }

    private static IllegalArgumentException invalidPayload() {
        return new IllegalArgumentException("Encrypted API key payload is invalid");
    }
}
