package com.kenyarealestate.user.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Date;
import java.util.UUID;

/**
 * Owns JWT revocation state in Redis:
 *  - a per-token blacklist, populated on logout.
 *  - a per-user "tokens valid after" timestamp, populated on password change/reset, which
 *    invalidates every token issued before that moment (covers tokens the user never explicitly
 *    logged out of, e.g. sessions on other devices).
 *
 * Both are consulted by JwtAuthenticationFilter on every request.
 */
@Slf4j
@Service
public class TokenBlacklistService {

    private final RedisTemplate<String, Object> redis;

    @Value("${redis.jwt-blacklist-prefix:jwt:blacklist:}")
    private String blacklistPrefix;

    @Value("${redis.tokens-valid-after-prefix:user:tokens-valid-after:}")
    private String validAfterPrefix;

    @Value("${jwt.expiration:86400000}")
    private long expirationMs;

    public TokenBlacklistService(RedisTemplate<String, Object> redis) {
        this.redis = redis;
    }

    public void blacklist(String token) {
        String key = blacklistPrefix + token;
        redis.opsForValue().set(key, "blacklisted",
                Duration.ofMillis(expirationMs));
        log.info("Token blacklisted");
    }

    public boolean isBlacklisted(String token) {
        return Boolean.TRUE.equals(redis.hasKey(blacklistPrefix + token));
    }

    /**
     * Marks every token issued for this user before "now" as invalid. Call this whenever the
     * user's credentials change (password change/reset) or all sessions should be revoked.
     */
    public void invalidateTokensBefore(UUID userId) {
        redis.opsForValue().set(validAfterPrefix + userId,
                String.valueOf(System.currentTimeMillis()), Duration.ofDays(7));
    }

    /**
     * True if the given token's issuedAt predates the last invalidation for that user, meaning
     * it must be rejected even though it hasn't expired and isn't individually blacklisted.
     */
    public boolean isIssuedBeforeInvalidation(UUID userId, Date issuedAt) {
        if (userId == null || issuedAt == null) return false;
        Object raw = redis.opsForValue().get(validAfterPrefix + userId);
        if (raw == null) return false;
        try {
            long validAfterMs = Long.parseLong(raw.toString());
            return issuedAt.getTime() < validAfterMs;
        } catch (NumberFormatException e) {
            log.warn("Malformed tokens-valid-after value for userId={}", userId);
            return false;
        }
    }
}
