package io.github.sukimiya.agileagent.mockoauth;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;

/** Utilities for OAuth2 PKCE S256 challenges. */
public final class PkceVerifier {
    private PkceVerifier() {
    }

    public static boolean matches(String verifier, String challenge, String method) {
        if (!isValidVerifier(verifier) || !isValidS256Challenge(challenge) || !"S256".equals(method)) {
            return false;
        }
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(verifier.getBytes(StandardCharsets.US_ASCII));
            String actual = Base64.getUrlEncoder().withoutPadding().encodeToString(digest);
            return MessageDigest.isEqual(actual.getBytes(StandardCharsets.US_ASCII),
                    challenge.getBytes(StandardCharsets.US_ASCII));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private static boolean isValidVerifier(String verifier) {
        return verifier != null && verifier.length() >= 43 && verifier.length() <= 128
                && verifier.chars().allMatch(PkceVerifier::isUnreserved);
    }

    static boolean isValidS256Challenge(String challenge) {
        return challenge != null && challenge.length() == 43
                && challenge.chars().allMatch(character -> character >= 'A' && character <= 'Z'
                || character >= 'a' && character <= 'z' || character >= '0' && character <= '9'
                || character == '-' || character == '_');
    }

    private static boolean isUnreserved(int character) {
        return character >= 'A' && character <= 'Z' || character >= 'a' && character <= 'z'
                || character >= '0' && character <= '9' || character == '-' || character == '.'
                || character == '_' || character == '~';
    }
}
