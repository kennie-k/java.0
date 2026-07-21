package com.kenyarealestate.property.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.UUID;

@Slf4j
@Component
public class VerificationClient {

    private final RestTemplate restTemplate = new RestTemplate();

    @Value("${services.verification-url}")
    private String verifUrl;

    @JsonIgnoreProperties(ignoreUnknown = true)
    @Data
    public static class TrustStatus {
        private boolean identityVerified;
        private boolean ownershipVerified;
        private boolean fullyTrusted;
        private boolean identityExpired;
        private String message;
    }

    @CircuitBreaker(name = "verification-service", fallbackMethod = "isIdentityVerifiedFallback")
    @Retry(name = "verification-service")
    public boolean isIdentityVerified(UUID userId) {
        try {
            TrustStatus ts = restTemplate.getForObject(
                    verifUrl + "/api/verification/trust-status/" + userId,
                    TrustStatus.class);
            return ts != null && ts.isIdentityVerified() && !ts.isIdentityExpired();
        } catch (Exception e) {
            log.warn("Verification service call failed for userId {}: {}", userId, e.getMessage());
            throw e;
        }
    }

    public boolean isIdentityVerifiedFallback(UUID userId, Exception ex) {
        log.error("Circuit breaker open for verification-service. " +
                "Defaulting to NOT verified for userId={}. Error: {}", userId, ex.getMessage());
        return false;
    }

    @CircuitBreaker(name = "verification-service", fallbackMethod = "getFullTrustStatusFallback")
    @Retry(name = "verification-service")
    public TrustStatus getFullTrustStatus(UUID userId, UUID propertyId) {
        try {
            return restTemplate.getForObject(
                    verifUrl + "/api/verification/trust-status/" + userId + "/property/" + propertyId,
                    TrustStatus.class);
        } catch (Exception e) {
            log.warn("Verification trust-status call failed: {}", e.getMessage());
            throw e;
        }
    }

    public TrustStatus getFullTrustStatusFallback(UUID userId, UUID propertyId, Exception ex) {
        log.error("Circuit breaker: returning null trust status for userId={}", userId);
        TrustStatus fallback = new TrustStatus();
        fallback.setMessage("Verification service temporarily unavailable");
        return fallback;
    }
}
