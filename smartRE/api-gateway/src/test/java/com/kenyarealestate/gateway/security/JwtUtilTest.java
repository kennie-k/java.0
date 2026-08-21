package com.kenyarealestate.gateway.security;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import javax.crypto.SecretKey;
import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests JwtUtil's parsing/validation logic in isolation from the gateway
 * filter chain: valid tokens, tampered/garbage tokens, expired tokens, and
 * claim extraction (email, role, userId, issuedAt).
 */
class JwtUtilTest {

    private static final String SECRET = "unit-test-jwt-secret-must-be-at-least-256-bits-long-for-hs256!!";

    private JwtUtil jwtUtil;
    private SecretKey key;

    @BeforeEach
    void setUp() {
        jwtUtil = new JwtUtil();
        ReflectionTestUtils.setField(jwtUtil, "secret", SECRET);
        key = Keys.hmacShaKeyFor(SECRET.getBytes());
    }

    private String token(String subject, String role, String userId, Date issuedAt, Date expiration) {
        return Jwts.builder()
                .subject(subject)
                .claim("role", role)
                .claim("userId", userId)
                .issuedAt(issuedAt)
                .expiration(expiration)
                .signWith(key)
                .compact();
    }

    @Test
    void validToken_isValidAndClaimsAreExtracted() {
        long now = System.currentTimeMillis();
        String token = token("buyer@example.com", "BUYER", "42",
                new Date(now), new Date(now + 60_000));

        assertThat(jwtUtil.isValid(token)).isTrue();
        assertThat(jwtUtil.extractEmail(token)).isEqualTo("buyer@example.com");
        assertThat(jwtUtil.extractRole(token)).isEqualTo("BUYER");
        assertThat(jwtUtil.extractUserId(token)).isEqualTo("42");
        assertThat(jwtUtil.extractIssuedAtMillis(token)).isEqualTo((now / 1000) * 1000);
    }

    @Test
    void expiredToken_isNotValid() {
        long now = System.currentTimeMillis();
        String token = token("buyer@example.com", "BUYER", "42",
                new Date(now - 120_000), new Date(now - 60_000));

        assertThat(jwtUtil.isValid(token)).isFalse();
    }

    @Test
    void garbageToken_isNotValid() {
        assertThat(jwtUtil.isValid("this.is.not-a-jwt")).isFalse();
    }

    @Test
    void tokenSignedWithDifferentSecret_isNotValid() {
        SecretKey otherKey = Keys.hmacShaKeyFor(
                "a-completely-different-secret-that-is-also-long-enough-256bit".getBytes());
        String token = Jwts.builder()
                .subject("attacker@example.com")
                .claim("role", "ADMIN")
                .claim("userId", "1")
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + 60_000))
                .signWith(otherKey)
                .compact();

        assertThat(jwtUtil.isValid(token)).isFalse();
    }

    @Test
    void missingUserIdClaim_extractsNull() {
        String token = Jwts.builder()
                .subject("noid@example.com")
                .claim("role", "BUYER")
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + 60_000))
                .signWith(key)
                .compact();

        assertThat(jwtUtil.extractUserId(token)).isNull();
    }
}
