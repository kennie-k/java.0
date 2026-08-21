package com.kenyarealestate.user.client;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.UUID;

@Slf4j
@Component
public class PropertyServiceClient {

    private final RestTemplate restTemplate = new RestTemplate();

    @Value("${services.property-url}")
    private String propertyUrl;

    @Value("${services.internal-secret}")
    private String internalSecret;

    @CircuitBreaker(name = "property-service", fallbackMethod = "suspendAllFallback")
    public void suspendAllListingsForSeller(UUID sellerId, String reason) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Internal-Secret", internalSecret);
        String url = UriComponentsBuilder.fromHttpUrl(
                        propertyUrl + "/api/properties/internal/seller/" + sellerId + "/suspend-all")
                .queryParam("reason", reason)
                .toUriString();
        restTemplate.exchange(url, HttpMethod.PUT, new HttpEntity<>(headers), Void.class);
        log.info("Suspended all listings for banned sellerId={}", sellerId);
    }

    @SuppressWarnings("unused")
    private void suspendAllFallback(UUID sellerId, String reason, Exception ex) {
        log.error("ALERT: Failed to suspend listings for banned sellerId={}. " +
                "Their listings remain active. Manual intervention required. Error: {}", sellerId, ex.getMessage());
    }
}
