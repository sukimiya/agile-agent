package io.github.sukimiya.agileagent.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.security.MessageDigest;
import java.util.Base64;

import org.junit.jupiter.api.Test;

class PkceTest {

    @Test
    void codeVerifierIsUrlSafeAndLongEnough() {
        String verifier = Pkce.newCodeVerifier();
        assertThat(verifier).matches("[A-Za-z0-9\\-_]+");
        assertThat(verifier.length()).isGreaterThanOrEqualTo(43);
    }

    @Test
    void twoVerifiersAreDifferent() {
        assertThat(Pkce.newCodeVerifier()).isNotEqualTo(Pkce.newCodeVerifier());
    }

    @Test
    void challengeIsBase64UrlSha256OfVerifier() throws Exception {
        String verifier = "test-verifier-1234567890-abcdefghijklmno";
        String expected = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(MessageDigest.getInstance("SHA-256").digest(verifier.getBytes("UTF-8")));

        assertThat(Pkce.challenge(verifier)).isEqualTo(expected);
    }
}
