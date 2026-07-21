package com.kenyarealestate.user.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

@Slf4j
@Service
public class TokenBlacklistService {

    private final RedisTemplate<String, Object> redis;

    @Value("${redis.jwt-blacklist-prefix:jwt:blacklist:}")
    private String prefix;

    @Value("${jwt.expiration:86400000}")
    private long expirationMs;

    public TokenBlacklistService(RedisTemplate<String, Object> redis) {
        this.redis = redis;
    }

    public void blacklist(String token) {
        String key = prefix + token;
        redis.opsForValue().set(key, "blacklisted",
                Duration.ofMillis(expirationMs));
        log.info("Token blacklisted");
    }

    public boolean isBlacklisted(String token) {
        return Boolean.TRUE.equals(redis.hasKey(prefix + token));
    }
}
