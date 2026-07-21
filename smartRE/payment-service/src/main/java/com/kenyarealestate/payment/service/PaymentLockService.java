package com.kenyarealestate.payment.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

@Slf4j
@Service
public class PaymentLockService {

    private final RedisTemplate<String, Object> redis;

    @Value("${redis.payment-lock-prefix:payment:lock:}")
    private String prefix;

    @Value("${redis.payment-lock-ttl-seconds:30}")
    private long lockTtl;

    public PaymentLockService(RedisTemplate<String, Object> redis) {
        this.redis = redis;
    }

    public boolean acquireLock(String idempotencyKey) {
        String lockKey = prefix + idempotencyKey;
        Boolean acquired = redis.opsForValue()
                .setIfAbsent(lockKey, "locked", Duration.ofSeconds(lockTtl));
        boolean result = Boolean.TRUE.equals(acquired);
        if (!result) log.info("Payment lock already held for key: {}", idempotencyKey);
        return result;
    }

    public void releaseLock(String idempotencyKey) {
        redis.delete(prefix + idempotencyKey);
    }
}
