package com.kenyarealestate.review.client;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

/**
 * Falls back to a direct, synchronous check against payment-service's own record of a payment
 * when the Redis eligibility key written by PaymentEventConsumer is missing (evicted under Redis's
 * allkeys-lru policy, or the Kafka event never arrived/dead-lettered) - review eligibility should
 * not depend solely on a cache entry surviving long enough for the buyer to get around to reviewing.
 */
@Slf4j
@Component
public class PaymentServiceClient {

    private final RestTemplate restTemplate;

    @Value("${services.payment-url}")
    private String paymentUrl;

    @Value("${services.internal-secret}")
    private String internalSecret;

    private static final List<String> ELIGIBLE_PAYMENT_TYPES = List.of("FULL_PAYMENT", "DEPOSIT");

    public PaymentServiceClient() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(3));
        factory.setReadTimeout(Duration.ofSeconds(3));
        this.restTemplate = new RestTemplate(factory);
    }

    @Data
    private static class PaymentLookupResponse {
        private UUID id, buyerId, sellerId, propertyId;
        private String paymentType, status;
    }

    // Both annotations are resilience4j's own (retry, not Spring Retry) so their composition
    // order is well-defined and documented: CircuitBreaker wraps Retry. That means transient
    // failures get retried first, and the circuit breaker only sees - and trips on - the final
    // outcome of each logical call, so it can actually open once payment-service is really down
    // instead of every request paying the full retry+backoff cost forever.
    @CircuitBreaker(name = "payment-service", fallbackMethod = "recoverCheckEligible")
    @Retry(name = "payment-service", fallbackMethod = "recoverCheckEligible")
    public boolean checkEligible(UUID paymentId, UUID buyerId, UUID sellerId, UUID propertyId) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Internal-Secret", internalSecret);
        PaymentLookupResponse payment = restTemplate.exchange(
                paymentUrl + "/api/payments/internal/" + paymentId,
                HttpMethod.GET, new HttpEntity<>(headers), PaymentLookupResponse.class).getBody();

        boolean eligible = payment != null
                && "COMPLETED".equals(payment.getStatus())
                && ELIGIBLE_PAYMENT_TYPES.contains(payment.getPaymentType())
                && buyerId.equals(payment.getBuyerId())
                && sellerId.equals(payment.getSellerId())
                && propertyId.equals(payment.getPropertyId());
        log.info("Fell back to payment-service for eligibility check on paymentId={}: eligible={}", paymentId, eligible);
        return eligible;
    }

    // Resilience4j fallback convention: same params as the guarded method, plus the
    // exception last. One fallback covers both a RestClientException surviving all retries
    // and a CallNotPermittedException thrown immediately once the breaker is open.
    private boolean recoverCheckEligible(UUID paymentId, UUID buyerId, UUID sellerId, UUID propertyId, Throwable ex) {
        log.error("ALERT: Could not reach payment-service to verify eligibility for paymentId={}. " +
                "Failing closed (review rejected). Error: {}", paymentId, ex.getMessage());
        return false;
    }
}
