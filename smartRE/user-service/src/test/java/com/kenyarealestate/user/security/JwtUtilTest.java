package com.kenyarealestate.user.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Date;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class JwtUtilTest {

    private JwtUtil jwtUtil;

    @BeforeEach
    void setup() {
        jwtUtil = new JwtUtil();
        ReflectionTestUtils.setField(jwtUtil, "secret",
                "test-only-secret-key-must-be-at-least-256-bits-long-for-hs256!!");
        ReflectionTestUtils.setField(jwtUtil, "expiration", 86_400_000L);
    }

    @Test
    void generateToken_roundTripsEmailRoleAndUserId() {
        UUID userId = UUID.randomUUID();
        String token = jwtUtil.generateToken("jane@smartre.co.ke", "SELLER", userId);

        assertTrue(jwtUtil.isValid(token));
        assertEquals("jane@smartre.co.ke", jwtUtil.extractEmail(token));
        assertEquals("SELLER", jwtUtil.extractRole(token));
        assertEquals(userId, jwtUtil.extractUserId(token));
    }

    @Test
    void extractIssuedAt_returnsATimestampCloseToNow() {
        String token = jwtUtil.generateToken("jane@smartre.co.ke", "SELLER", UUID.randomUUID());

        Date issuedAt = jwtUtil.extractIssuedAt(token);

        assertNotNull(issuedAt);
        long deltaMs = Math.abs(System.currentTimeMillis() - issuedAt.getTime());
        assertTrue(deltaMs < 5_000, "issuedAt should be within a few seconds of now");
    }

    @Test
    void isValid_rejectsGarbageToken() {
        assertFalse(jwtUtil.isValid("not-a-real-jwt"));
    }

    @Test
    void isValid_rejectsTokenSignedWithDifferentSecret() {
        JwtUtil otherIssuer = new JwtUtil();
        ReflectionTestUtils.setField(otherIssuer, "secret",
                "a-completely-different-secret-key-also-256-bits-long-enough!!!!");
        ReflectionTestUtils.setField(otherIssuer, "expiration", 86_400_000L);

        String tokenFromOtherIssuer = otherIssuer.generateToken("jane@smartre.co.ke", "SELLER", UUID.randomUUID());

        assertFalse(jwtUtil.isValid(tokenFromOtherIssuer));
    }

    @Test
    void isValid_rejectsExpiredToken() {
        ReflectionTestUtils.setField(jwtUtil, "expiration", -1000L);
        String expiredToken = jwtUtil.generateToken("jane@smartre.co.ke", "SELLER", UUID.randomUUID());

        assertFalse(jwtUtil.isValid(expiredToken));
    }
}
