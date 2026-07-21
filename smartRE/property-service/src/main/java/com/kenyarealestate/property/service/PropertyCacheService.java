package com.kenyarealestate.property.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.UUID;

@Slf4j
@Service
public class PropertyCacheService {

    private final RedisTemplate<String, Object> redis;

    @Value("${redis.search-cache-prefix:property:search:}")
    private String searchPrefix;

    @Value("${redis.search-cache-ttl-seconds:120}")
    private long searchTtl;

    @Value("${redis.property-cache-prefix:property:detail:}")
    private String detailPrefix;

    @Value("${redis.property-detail-ttl-seconds:300}")
    private long detailTtl;

    public PropertyCacheService(RedisTemplate<String, Object> redis) {
        this.redis = redis;
    }

    public Object getSearch(String cacheKey) {
        try { return redis.opsForValue().get(searchPrefix + cacheKey); }
        catch (Exception e) { log.warn("Search cache read error: {}", e.getMessage()); return null; }
    }

    public void putSearch(String cacheKey, Object result) {
        try { redis.opsForValue().set(searchPrefix + cacheKey, result, Duration.ofSeconds(searchTtl)); }
        catch (Exception e) { log.warn("Search cache write error: {}", e.getMessage()); }
    }

    public Object getDetail(UUID propertyId) {
        try { return redis.opsForValue().get(detailPrefix + propertyId); }
        catch (Exception e) { log.warn("Detail cache read error: {}", e.getMessage()); return null; }
    }

    public void putDetail(UUID propertyId, Object result) {
        try { redis.opsForValue().set(detailPrefix + propertyId, result, Duration.ofSeconds(detailTtl)); }
        catch (Exception e) { log.warn("Detail cache write error: {}", e.getMessage()); }
    }

    public void evictDetail(UUID propertyId) {
        try { redis.delete(detailPrefix + propertyId); }
        catch (Exception e) { log.warn("Detail cache eviction error: {}", e.getMessage()); }
    }

    public void evictAllSearches() {
        try {
            var keys = redis.keys(searchPrefix + "*");
            if (keys != null && !keys.isEmpty()) redis.delete(keys);
        } catch (Exception e) { log.warn("Search cache eviction error: {}", e.getMessage()); }
    }
}
