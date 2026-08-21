package com.kenyarealestate.gateway.filter;

import com.kenyarealestate.gateway.security.JwtUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.data.redis.core.ReactiveValueOperations;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Behavioral tests for AuthFilter: token extraction/validation, the
 * blacklist/ban/password-change-revocation checks backed by Redis, and the
 * fail-closed behavior when Redis itself is unavailable or too slow.
 */
class AuthFilterTest {

    private JwtUtil jwtUtil;
    @SuppressWarnings("unchecked")
    private ReactiveRedisTemplate<String, String> redis;
    @SuppressWarnings("unchecked")
    private ReactiveValueOperations<String, String> valueOps;
    private GatewayFilterChain chain;
    private AuthFilter authFilter;

    private static final String TOKEN = "test.jwt.token";
    private static final String USER_ID = "42";

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        jwtUtil = mock(JwtUtil.class);
        redis = mock(ReactiveRedisTemplate.class);
        valueOps = mock(ReactiveValueOperations.class);
        chain = mock(GatewayFilterChain.class);

        when(redis.opsForValue()).thenReturn(valueOps);
        when(chain.filter(any())).thenReturn(Mono.empty());

        authFilter = new AuthFilter(jwtUtil, redis);
        ReflectionTestUtils.setField(authFilter, "signingSecret", "unit-test-signing-secret");

        // Sane defaults: valid, non-blacklisted, non-banned token, issued now.
        when(jwtUtil.isValid(TOKEN)).thenReturn(true);
        when(jwtUtil.extractUserId(TOKEN)).thenReturn(USER_ID);
        when(jwtUtil.extractIssuedAtMillis(TOKEN)).thenReturn(System.currentTimeMillis());
        when(jwtUtil.extractEmail(TOKEN)).thenReturn("buyer@example.com");
        when(jwtUtil.extractRole(TOKEN)).thenReturn("BUYER");

        when(redis.hasKey(anyString())).thenReturn(Mono.just(false));
        when(valueOps.get(anyString())).thenReturn(Mono.empty());
    }

    private ServerWebExchange exchangeWithBearerToken(String token) {
        MockServerHttpRequest request = MockServerHttpRequest.get("/api/properties/my")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .build();
        return MockServerWebExchange.from(request);
    }

    private GatewayFilter filter() {
        return authFilter.apply(new AuthFilter.Config());
    }

    @Test
    void missingToken_isDeniedWithUnauthorized() {
        ServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/api/properties/my").build());

        StepVerifier.create(filter().filter(exchange, chain)).verifyComplete();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(exchange.getResponse().getHeaders().getFirst("X-Error-Reason")).isEqualTo("Missing token");
        verifyNoInteractions(chain);
    }

    @Test
    void invalidToken_isDeniedWithUnauthorized() {
        when(jwtUtil.isValid(TOKEN)).thenReturn(false);
        ServerWebExchange exchange = exchangeWithBearerToken(TOKEN);

        StepVerifier.create(filter().filter(exchange, chain)).verifyComplete();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(exchange.getResponse().getHeaders().getFirst("X-Error-Reason")).isEqualTo("Invalid or expired token");
        verifyNoInteractions(chain);
    }

    @Test
    void blacklistedToken_isDeniedWithUnauthorized() {
        when(redis.hasKey("jwt:blacklist:" + TOKEN)).thenReturn(Mono.just(true));
        ServerWebExchange exchange = exchangeWithBearerToken(TOKEN);

        StepVerifier.create(filter().filter(exchange, chain)).verifyComplete();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(exchange.getResponse().getHeaders().getFirst("X-Error-Reason")).isEqualTo("Token has been revoked");
        verifyNoInteractions(chain);
    }

    @Test
    void bannedUser_isDeniedWithForbidden() {
        when(redis.hasKey("user:banned:" + USER_ID)).thenReturn(Mono.just(true));
        ServerWebExchange exchange = exchangeWithBearerToken(TOKEN);

        StepVerifier.create(filter().filter(exchange, chain)).verifyComplete();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(exchange.getResponse().getHeaders().getFirst("X-Error-Reason")).isEqualTo("Account has been banned");
        verifyNoInteractions(chain);
    }

    @Test
    void tokenIssuedBeforePasswordChange_isDeniedWithUnauthorized() {
        long issuedAt = System.currentTimeMillis() - Duration.ofHours(1).toMillis();
        when(jwtUtil.extractIssuedAtMillis(TOKEN)).thenReturn(issuedAt);
        // "tokens valid after" timestamp is newer than the token's issued-at -> revoked.
        when(valueOps.get("user:tokens-valid-after:" + USER_ID))
                .thenReturn(Mono.just(String.valueOf(System.currentTimeMillis())));
        ServerWebExchange exchange = exchangeWithBearerToken(TOKEN);

        StepVerifier.create(filter().filter(exchange, chain)).verifyComplete();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(exchange.getResponse().getHeaders().getFirst("X-Error-Reason")).isEqualTo("Token has been revoked");
        verifyNoInteractions(chain);
    }

    @Test
    void validToken_isForwardedDownstreamWithSignedAuthHeaders() {
        ServerWebExchange exchange = exchangeWithBearerToken(TOKEN);

        StepVerifier.create(filter().filter(exchange, chain)).verifyComplete();

        ArgumentCaptor<ServerWebExchange> captor = ArgumentCaptor.forClass(ServerWebExchange.class);
        verify(chain).filter(captor.capture());
        HttpHeaders forwardedHeaders = captor.getValue().getRequest().getHeaders();
        assertThat(forwardedHeaders.getFirst("X-Auth-Email")).isEqualTo("buyer@example.com");
        assertThat(forwardedHeaders.getFirst("X-Auth-Role")).isEqualTo("BUYER");
        assertThat(forwardedHeaders.getFirst("X-Auth-UserId")).isEqualTo(USER_ID);
        assertThat(forwardedHeaders.getFirst("X-Auth-Signature")).isNotBlank();
    }

    @Test
    void redisTimeout_failsClosedWithServiceUnavailable() {
        // Simulate a Redis outage: the calls never emit, so the .timeout() on the
        // Mono.zip in AuthFilter must trip and the request must be denied - NOT
        // forwarded downstream unauthenticated (see the fail-closed comment in
        // AuthFilter for the reasoning).
        when(redis.hasKey(anyString())).thenReturn(Mono.never());
        when(valueOps.get(anyString())).thenReturn(Mono.never());
        ServerWebExchange exchange = exchangeWithBearerToken(TOKEN);

        StepVerifier.withVirtualTime(() -> filter().filter(exchange, chain))
                .thenAwait(Duration.ofSeconds(5))
                .verifyComplete();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(exchange.getResponse().getHeaders().getFirst("X-Error-Reason"))
                .isEqualTo("Auth service temporarily unavailable");
        verifyNoInteractions(chain);
    }

    @Test
    void redisError_failsClosedWithServiceUnavailable() {
        when(redis.hasKey(anyString())).thenReturn(Mono.error(new RuntimeException("connection refused")));
        ServerWebExchange exchange = exchangeWithBearerToken(TOKEN);

        StepVerifier.create(filter().filter(exchange, chain)).verifyComplete();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        verifyNoInteractions(chain);
    }
}
