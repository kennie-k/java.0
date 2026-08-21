package com.kenyarealestate.gateway.filter;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.Map;

/**
 * Local target for the CircuitBreaker gateway filter's fallbackUri (see
 * application.yaml route definitions, e.g. "forward:/fallback/user-service").
 * Reached when a downstream service's circuit breaker is open or a call to it
 * times out/fails past the configured threshold. Returns a clean, uniform 503
 * instead of letting the caller see a hung connection or a raw connection-reset
 * error, and gives the Grafana "Circuit Breakers Open" panel / CircuitBreakerOpen
 * alert something real to reflect.
 */
@Slf4j
@RestController
public class FallbackController {

    @RequestMapping("/fallback/{service}")
    public Mono<ResponseEntity<Map<String, Object>>> fallback(@PathVariable String service) {
        log.warn("Circuit breaker fallback triggered for downstream service: {}", service);
        Map<String, Object> body = Map.of(
                "error", "SERVICE_UNAVAILABLE",
                "service", service,
                "message", service + " is temporarily unavailable. Please try again shortly.",
                "timestamp", Instant.now().toString()
        );
        return Mono.just(ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(body));
    }
}
