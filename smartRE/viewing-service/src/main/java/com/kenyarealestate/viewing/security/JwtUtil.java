package com.kenyarealestate.viewing.security;
import io.jsonwebtoken.*; import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value; import org.springframework.stereotype.Component;
import javax.crypto.SecretKey; import java.util.UUID;
@Component
public class JwtUtil {
    @Value("${jwt.secret}") private String secret;
    private SecretKey key() { return Keys.hmacShaKeyFor(secret.getBytes()); }
    public Claims extractAll(String t) { return Jwts.parser().verifyWith(key()).build().parseSignedClaims(t).getPayload(); }
    public String extractEmail(String t) { return extractAll(t).getSubject(); }
    public String extractRole(String t)  { return extractAll(t).get("role",String.class); }
    public UUID extractUserId(String t) { String uid=extractAll(t).get("userId",String.class); return uid!=null?UUID.fromString(uid):UUID.nameUUIDFromBytes(extractEmail(t).trim().toLowerCase().getBytes(java.nio.charset.StandardCharsets.UTF_8)); }
    public boolean isValid(String t) { try{Jwts.parser().verifyWith(key()).build().parseSignedClaims(t);return true;}catch(Exception e){return false;} }
}
