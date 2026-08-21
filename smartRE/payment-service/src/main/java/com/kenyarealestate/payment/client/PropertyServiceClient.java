package com.kenyarealestate.payment.client;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Recover;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.Map;
import java.util.UUID;

@Slf4j
@Component
public class PropertyServiceClient {

    private final RestTemplate restTemplate = new RestTemplate();

    @Value("${services.property-url}")
    private String propertyUrl;

    @Value("${services.internal-secret}")
    private String internalSecret;

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

    @CircuitBreaker(name = "property-service", fallbackMethod = "getSellerIdFallback")
    public UUID getSellerId(UUID propertyId) {
        Map<?, ?> body = restTemplate.getForObject(
                propertyUrl + "/api/properties/" + propertyId, Map.class);
        if (body == null || body.get("sellerId") == null) return null;
        return UUID.fromString(String.valueOf(body.get("sellerId")));
    }

    @SuppressWarnings("unused")
    private UUID getSellerIdFallback(UUID propertyId, Exception ex) {
        log.warn("Could not fetch seller for property {}: {}. Skipping seller cross-check.", propertyId, ex.getMessage());
        return null;
    }

    @CircuitBreaker(name = "property-service", fallbackMethod = "recoverMarkTransactionComplete")
    @Retryable(
        retryFor = RestClientException.class,
        maxAttempts = 3,
        backoff = @Backoff(delay = 1000, multiplier = 2)
    )
    public void markTransactionComplete(UUID propertyId) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Internal-Secret", internalSecret);
        restTemplate.exchange(
                propertyUrl + "/api/properties/internal/" + propertyId + "/mark-transaction-complete",
                HttpMethod.PUT, new HttpEntity<>(headers), Void.class);
        log.info("Marked property {} transaction complete (delisted)", propertyId);
    }

    @Recover
    public void recoverMarkTransactionComplete(RestClientException ex, UUID propertyId) {
        log.error("ALERT: Failed to delist property {} after escrow release, after 3 retries. " +
                "It remains payable by another buyer. Manual intervention required. Error: {}",
                propertyId, ex.getMessage());
    }
}
