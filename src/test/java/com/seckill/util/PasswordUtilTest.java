package com.seckill.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PasswordUtilTest {

    @Test
    void encryptsWithBcryptAndMatches() {
        String encoded = PasswordUtil.encrypt("secret123");

        assertNotEquals("secret123", encoded);
        assertTrue(encoded.startsWith("$2"));
        assertTrue(PasswordUtil.matches("secret123", encoded));
        assertFalse(PasswordUtil.matches("wrong-password", encoded));
    }
}
