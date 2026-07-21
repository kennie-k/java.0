package com.kenyarealestate.user.security;

import io.jsonwebtoken.*; import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value; import org.springframework.stereotype.Component;
import javax.crypto.SecretKey; import java.util.*; import java.util.UUID;

@Component
public class JwtUtil {
    @Value("${jwt.secret}") private String secret;
    @Value("${jwt.expiration:86400000}") private long expiration;

    private SecretKey key() { return Keys.hmacShaKeyFor(secret.getBytes()); }

    public String generateToken(String email, String role, UUID userId) {
        return Jwts.builder()
                .subject(email)
                .claim("role", role)
                .claim("userId", userId.toString())
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + expiration))
                .signWith(key()).compact();
    }

    public Claims extractAll(String t) { return Jwts.parser().verifyWith(key()).build().parseSignedClaims(t).getPayload(); }
    public String extractEmail(String t) { return extractAll(t).getSubject(); }
    public String extractRole(String t)  { return extractAll(t).get("role", String.class); }
    public UUID extractUserId(String t) {
        String uid = extractAll(t).get("userId", String.class);
        return uid != null ? UUID.fromString(uid) : null;
    }
    public boolean isValid(String t) {
        try { Jwts.parser().verifyWith(key()).build().parseSignedClaims(t); return true; }
        catch (Exception e) { return false; }
    }
}
