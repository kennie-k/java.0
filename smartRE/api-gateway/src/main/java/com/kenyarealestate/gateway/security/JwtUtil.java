package com.kenyarealestate.gateway.security;
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
    public String extractUserId(String t) { Object uid=extractAll(t).get("userId"); return uid!=null?uid.toString():null; }
    public long extractIssuedAtMillis(String t) { var iat=extractAll(t).getIssuedAt(); return iat!=null?iat.getTime():0L; }
    public boolean isValid(String t) { try{Jwts.parser().verifyWith(key()).build().parseSignedClaims(t);return true;}catch(Exception e){return false;} }
}
