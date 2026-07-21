package com.kenyarealestate.property.security;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.util.UUID;

@Component
public class JwtUtil {

    @Value("${jwt.secret}")
    private String secret;

    private SecretKey key() {
        return Keys.hmacShaKeyFor(secret.getBytes());
    }

    public Claims extractAll(String token) {
        return Jwts.parser().verifyWith(key()).build()
                .parseSignedClaims(token).getPayload();
    }

    public String extractEmail(String token) { return extractAll(token).getSubject(); }

    public String extractRole(String token) {
        return extractAll(token).get("role", String.class);
    }

    public UUID extractUserId(String token) {
        String uid = extractAll(token).get("userId", String.class);
        if (uid != null) return UUID.fromString(uid);
        return UUID.nameUUIDFromBytes(
                extractEmail(token).trim().toLowerCase()
                        .getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    public boolean isValid(String token) {
        try {
            Jwts.parser().verifyWith(key()).build().parseSignedClaims(token);
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}
