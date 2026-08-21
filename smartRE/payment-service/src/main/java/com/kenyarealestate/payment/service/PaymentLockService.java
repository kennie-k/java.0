package com.kenyarealestate.payment.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
public class PaymentLockService {

    private final RedisTemplate<String, Object> redis;

    @Value("${redis.payment-lock-prefix:payment:lock:}")
    private String prefix;

    @Value("${redis.payment-lock-ttl-seconds:30}")
    private long lockTtl;

    private static final RedisScript<Long> RELEASE_SCRIPT = RedisScript.of(
            "if redis.call('get', KEYS[1]) == ARGV[1] then return redis.call('del', KEYS[1]) else return 0 end",
            Long.class);

    public PaymentLockService(RedisTemplate<String, Object> redis) {
        this.redis = redis;
    }

    public String acquireLock(String idempotencyKey) {
        String lockKey = prefix + idempotencyKey;
        String token = UUID.randomUUID().toString();
        Boolean acquired = redis.opsForValue().setIfAbsent(lockKey, token, Duration.ofSeconds(lockTtl));
        if (!Boolean.TRUE.equals(acquired)) {
            log.info("Payment lock already held for key: {}", idempotencyKey);
            return null;
        }
        return token;
    }

    public void releaseLock(String idempotencyKey, String token) {
        if (token == null) return;
        redis.execute(RELEASE_SCRIPT, List.of(prefix + idempotencyKey), token);
    }
}
