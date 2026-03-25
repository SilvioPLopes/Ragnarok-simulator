package com.ragnarok.infrastructure.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class JwtUtilTest {

    private JwtUtil jwtUtil;

    @BeforeEach
    void setUp() {
        jwtUtil = new JwtUtil("test-secret-key-that-is-at-least-256-bits-long-for-hs256");
    }

    @Test
    void generateAndValidateToken() {
        String token = jwtUtil.generateToken(42L);
        assertTrue(jwtUtil.isValid(token));
        assertEquals(42L, jwtUtil.extractAccountId(token));
    }

    @Test
    void invalidTokenReturnsFalse() {
        assertFalse(jwtUtil.isValid("token.invalido.aqui"));
    }

    @Test
    void expiredTokenReturnsFalse() {
        JwtUtil shortLived = new JwtUtil("test-secret-key-that-is-at-least-256-bits-long-for-hs256", -1L);
        String token = shortLived.generateToken(1L);
        assertFalse(shortLived.isValid(token));
    }
}
