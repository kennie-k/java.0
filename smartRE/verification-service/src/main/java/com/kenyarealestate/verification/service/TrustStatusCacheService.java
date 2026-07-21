package com.kenyarealestate.verification.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kenyarealestate.verification.dto.TrustStatusResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.UUID;

@Slf4j
@Service
public class TrustStatusCacheService {

    private final RedisTemplate<String, Object> redis;
    private final ObjectMapper objectMapper;

    @Value("${redis.trust-status-prefix:trust:}")
    private String prefix;

    @Value("${redis.trust-status-ttl-seconds:300}")
    private long ttlSeconds;

    public TrustStatusCacheService(RedisTemplate<String, Object> redis) {
        this.redis = redis;
        this.objectMapper = new ObjectMapper();
        this.objectMapper.findAndRegisterModules();
    }

    public TrustStatusResponse get(UUID userId) {
        try {
            Object val = redis.opsForValue().get(prefix + userId);
            if (val == null) return null;
            return objectMapper.convertValue(val, TrustStatusResponse.class);
        } catch (Exception e) {
            log.warn("Redis trust status cache read error: {}", e.getMessage());
            return null;
        }
    }

    public TrustStatusResponse getForProperty(UUID userId, UUID propertyId) {
        try {
            Object val = redis.opsForValue().get(prefix + userId + ":" + propertyId);
            if (val == null) return null;
            return objectMapper.convertValue(val, TrustStatusResponse.class);
        } catch (Exception e) {
            log.warn("Redis trust status cache read error: {}", e.getMessage());
            return null;
        }
    }

    public void put(UUID userId, TrustStatusResponse response) {
        try {
            redis.opsForValue().set(prefix + userId, response, Duration.ofSeconds(ttlSeconds));
        } catch (Exception e) {
            log.warn("Redis trust status cache write error: {}", e.getMessage());
        }
    }

    public void putForProperty(UUID userId, UUID propertyId, TrustStatusResponse response) {
        try {
            redis.opsForValue().set(prefix + userId + ":" + propertyId,
                    response, Duration.ofSeconds(ttlSeconds));
        } catch (Exception e) {
            log.warn("Redis trust status cache write error: {}", e.getMessage());
        }
    }

    public void evict(UUID userId) {
        try {

            var keys = redis.keys(prefix + userId + "*");
            if (keys != null && !keys.isEmpty()) {
                redis.delete(keys);
                log.info("Evicted {} trust status cache entries for seller {}", keys.size(), userId);
            }
        } catch (Exception e) {
            log.warn("Redis trust status cache eviction error: {}", e.getMessage());
        }
    }
}
