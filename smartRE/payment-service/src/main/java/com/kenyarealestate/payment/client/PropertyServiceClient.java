package com.kenyarealestate.payment.client;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.Map;
import java.util.UUID;

@Slf4j
@Component
public class PropertyServiceClient {

    private final RestTemplate restTemplate = new RestTemplate();

    @Value("${services.property-url}")
    private String propertyUrl;

    @CircuitBreaker(name = "property-service", fallbackMethod = "isPropertyVerifiedFallback")
    public boolean isPropertyVerified(UUID propertyId) {
        Map<?, ?> body = restTemplate.getForObject(
                propertyUrl + "/api/properties/" + propertyId, Map.class);
        if (body == null) return false;
        Object identity = body.get("sellerIdentityVerified");
        Object ownership = body.get("propertyOwnershipVerified");
        return Boolean.TRUE.equals(identity) && Boolean.TRUE.equals(ownership);
    }

    @SuppressWarnings("unused")
    private boolean isPropertyVerifiedFallback(UUID propertyId, Exception ex) {
        log.warn("Could not confirm verification status for property {}: {}. Failing closed.", propertyId, ex.getMessage());
        return false;
    }
}
