package com.kenyarealestate.user.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

@Slf4j
@Service
public class LoginAttemptService {

    private final RedisTemplate<String, Object> redis;

    @Value("${security.login.max-attempts:5}")
    private int maxAttempts;

    @Value("${security.login.lockout-minutes:15}")
    private long lockoutMinutes;

    private static final String PREFIX = "login:fail:";

    public LoginAttemptService(RedisTemplate<String, Object> redis) {
        this.redis = redis;
    }

    public boolean isLocked(String email) {
        Object current = redis.opsForValue().get(PREFIX + email);
        return current instanceof Integer && (Integer) current >= maxAttempts;
    }

    public void recordFailure(String email) {
        String key = PREFIX + email;
        Object current = redis.opsForValue().get(key);
        int attempts = (current instanceof Integer) ? (Integer) current + 1 : 1;
        redis.opsForValue().set(key, attempts, Duration.ofMinutes(lockoutMinutes));
        log.info("Failed login attempt {} of {} recorded", attempts, maxAttempts);
    }

    public void recordSuccess(String email) {
        redis.delete(PREFIX + email);
    }

    public long remainingLockoutSeconds(String email) {
        Long ttl = redis.getExpire(PREFIX + email);
        return ttl != null && ttl > 0 ? ttl : 0;
    }
}
