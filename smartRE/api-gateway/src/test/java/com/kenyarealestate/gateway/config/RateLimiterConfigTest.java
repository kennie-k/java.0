package com.kenyarealestate.gateway.config;

import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;
import reactor.test.StepVerifier;

import java.net.InetSocketAddress;

/**
 * Tests the rate-limiter key resolver: authenticated requests must be keyed
 * per-user (so one user can't be starved/boosted by others behind the same
 * IP), and unauthenticated requests must fall back to per-IP keying.
 */
class RateLimiterConfigTest {

    private final KeyResolver resolver = new RateLimiterConfig().userKeyResolver();

    @Test
    void authenticatedRequest_isKeyedByUserId() {
        ServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/properties/my")
                        .header("X-Auth-UserId", "42")
                        .build());

        StepVerifier.create(resolver.resolve(exchange))
                .expectNext("user:42")
                .verifyComplete();
    }

    @Test
    void unauthenticatedRequest_fallsBackToRemoteIp() {
        MockServerHttpRequest request = MockServerHttpRequest.get("/api/auth/register")
                .remoteAddress(new InetSocketAddress("203.0.113.7", 54321))
                .build();
        ServerWebExchange exchange = MockServerWebExchange.from(request);

        StepVerifier.create(resolver.resolve(exchange))
                .expectNext("ip:203.0.113.7")
                .verifyComplete();
    }

    @Test
    void blankUserIdHeader_fallsBackToRemoteIp() {
        MockServerHttpRequest request = MockServerHttpRequest.get("/api/auth/register")
                .header("X-Auth-UserId", "")
                .remoteAddress(new InetSocketAddress("198.51.100.9", 8080))
                .build();
        ServerWebExchange exchange = MockServerWebExchange.from(request);

        StepVerifier.create(resolver.resolve(exchange))
                .expectNext("ip:198.51.100.9")
                .verifyComplete();
    }

    @Test
    void noUserIdAndNoRemoteAddress_resolvesToUnknown() {
        ServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/auth/register").build());

        StepVerifier.create(resolver.resolve(exchange))
                .expectNext("ip:unknown")
                .verifyComplete();
    }
}
