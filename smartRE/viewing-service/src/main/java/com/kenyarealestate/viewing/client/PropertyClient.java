package com.kenyarealestate.viewing.client;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Component
public class PropertyClient {

    private final RestTemplate rt;

    @Value("${services.property-url:http://property-service:8083}")
    private String propertyUrl;

    public PropertyClient() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(3));
        factory.setReadTimeout(Duration.ofSeconds(3));
        this.rt = new RestTemplate(factory);
    }

    @CircuitBreaker(name = "property-service", fallbackMethod = "getSellerIdFallback")
    public UUID getSellerId(UUID propertyId) {
        Map<?, ?> body = rt.getForObject(propertyUrl + "/api/properties/" + propertyId, Map.class);
        if (body == null || body.get("sellerId") == null) return null;
        return UUID.fromString(String.valueOf(body.get("sellerId")));
    }

    @SuppressWarnings("unused")
    private UUID getSellerIdFallback(UUID propertyId, Exception e) {
        log.error("Failed to fetch property {} for seller validation: {}. Skipping seller cross-check.", propertyId, e.getMessage());
        return null;
    }

    @CircuitBreaker(name = "property-service", fallbackMethod = "isSellerIdentityVerifiedFallback")
    public boolean isSellerIdentityVerified(UUID propertyId) {
        Map<?, ?> body = rt.getForObject(propertyUrl + "/api/properties/" + propertyId, Map.class);
        if (body == null) return false;
        return Boolean.TRUE.equals(body.get("sellerIdentityVerified"));
    }

    @SuppressWarnings("unused")
    private boolean isSellerIdentityVerifiedFallback(UUID propertyId, Exception e) {
        log.warn("Could not confirm seller identity verification for property {}: {}. Failing closed.", propertyId, e.getMessage());
        return false;
    }
}
