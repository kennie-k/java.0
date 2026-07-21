package com.kenyarealestate.verification.client;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Recover;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.UUID;

@Slf4j
@Component
public class PropertyServiceClient {

    private final RestTemplate restTemplate = new RestTemplate();

    @Value("${services.property-url}")
    private String propertyUrl;

    @Value("${services.internal-secret}")
    private String internalSecret;

    @CircuitBreaker(name = "property-service", fallbackMethod = "recoverActivateAll")
    @Retryable(
        retryFor = RestClientException.class,
        maxAttempts = 3,
        backoff = @Backoff(delay = 1000, multiplier = 2)
    )
    public void activateAllListingsForSeller(UUID sellerId, String callerJwt) {
        HttpHeaders headers = new HttpHeaders();
        String token = (callerJwt != null && !callerJwt.equals("null"))
                ? callerJwt : internalSecret;
        headers.set("X-Internal-Secret", internalSecret);
        if (callerJwt != null && !callerJwt.equals("null")) {
            headers.set("Authorization", "Bearer " + callerJwt);
        }
        restTemplate.exchange(
                propertyUrl + "/api/properties/internal/seller/" + sellerId + "/activate-all",
                HttpMethod.PUT, new HttpEntity<>(headers), Void.class);
        log.info("Activated all listings for seller {}", sellerId);
    }

    @Recover
    public void recoverActivateAll(RestClientException ex, UUID sellerId, String jwt) {
        log.error("ALERT: Failed to activate listings for seller {} after 3 retries. " +
                "Manual intervention required. Error: {}", sellerId, ex.getMessage());
    }

    @CircuitBreaker(name = "property-service", fallbackMethod = "recoverMarkOwnership")
    @Retryable(
        retryFor = RestClientException.class,
        maxAttempts = 3,
        backoff = @Backoff(delay = 1000, multiplier = 2)
    )
    public void markPropertyOwnershipVerified(UUID propertyId, String callerJwt, String parcelNumber, String titleDeedNumber) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Internal-Secret", internalSecret);
        if (callerJwt != null && !callerJwt.equals("null")) {
            headers.set("Authorization", "Bearer " + callerJwt);
        }
        String url = propertyUrl + "/api/properties/internal/" + propertyId + "/mark-ownership-verified"
                + "?parcelNumber=" + java.net.URLEncoder.encode(parcelNumber == null ? "" : parcelNumber, java.nio.charset.StandardCharsets.UTF_8)
                + "&titleDeedNumber=" + java.net.URLEncoder.encode(titleDeedNumber == null ? "" : titleDeedNumber, java.nio.charset.StandardCharsets.UTF_8);
        restTemplate.exchange(url, HttpMethod.PUT, new HttpEntity<>(headers), Void.class);
        log.info("Marked ownership verified for property {}", propertyId);
    }

    @Recover
    public void recoverMarkOwnership(RestClientException ex, UUID propertyId, String jwt, String parcelNumber, String titleDeedNumber) {
        log.error("ALERT: Failed to mark ownership verified for property {} after 3 retries. " +
                "Manual intervention required. Error: {}", propertyId, ex.getMessage());
    }
}
