package io.github.sukimiya.agileagent.mockoauth;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PkceVerifierTest {
    @Test
    void acceptsVerifierWhoseS256ChallengeMatches() {
        String verifier = "dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk";
        assertTrue(PkceVerifier.matches(verifier,
                "E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM", "S256"));
    }

    @Test
    void rejectsWrongVerifierAndPlainChallengeMethod() {
        assertFalse(PkceVerifier.matches("wrong-verifier", "not-a-match", "S256"));
        assertFalse(PkceVerifier.matches("anything", "anything", "plain"));
        assertFalse(PkceVerifier.matches("short", "E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM", "S256"));
    }
}
