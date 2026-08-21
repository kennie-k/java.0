package com.kenyarealestate.gateway.filter;

import com.kenyarealestate.gateway.security.JwtUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.factory.AbstractGatewayFilterFactory;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.http.HttpCookie;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import reactor.util.function.Tuple3;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;

@Slf4j
@Component
public class AuthFilter extends AbstractGatewayFilterFactory<AuthFilter.Config> {

    private final JwtUtil jwtUtil;
    private final ReactiveRedisTemplate<String, String> redis;
    private static final String BLACKLIST_PREFIX = "jwt:blacklist:";
    private static final String BANNED_USER_PREFIX = "user:banned:";
    private static final String TOKENS_VALID_AFTER_PREFIX = "user:tokens-valid-after:";

    // How long we'll wait on Redis for the blacklist/ban/password-change checks
    // before treating it as unavailable.
    private static final Duration REDIS_CHECK_TIMEOUT = Duration.ofSeconds(2);

    @Value("${gateway.signing-secret}")
    private String signingSecret;

    public AuthFilter(JwtUtil jwtUtil,
                      @Qualifier("reactiveRedisTemplate") ReactiveRedisTemplate<String, String> redis) {
        super(Config.class);
        this.jwtUtil = jwtUtil;
        this.redis = redis;
    }

    @Override
    public GatewayFilter apply(Config config) {
        return (exchange, chain) -> {
            String token = extractToken(exchange.getRequest());
            if (!StringUtils.hasText(token))
                return deny(exchange, HttpStatus.UNAUTHORIZED, "Missing token");
            if (!jwtUtil.isValid(token))
                return deny(exchange, HttpStatus.UNAUTHORIZED, "Invalid or expired token");

            String userId = jwtUtil.extractUserId(token);
            long issuedAtMillis = jwtUtil.extractIssuedAtMillis(token);

            return Mono.zip(
                    redis.hasKey(BLACKLIST_PREFIX + token).defaultIfEmpty(false),
                    redis.hasKey(BANNED_USER_PREFIX + userId).defaultIfEmpty(false),
                    redis.opsForValue().get(TOKENS_VALID_AFTER_PREFIX + userId).defaultIfEmpty("")
            )
                    .timeout(REDIS_CHECK_TIMEOUT)
                    .flatMap(
                            flags -> onRedisChecksResolved(exchange, chain, token, userId, issuedAtMillis, flags),
                            // DELIBERATE FAIL-CLOSED DECISION: if Redis is unreachable or too slow
                            // to answer the blacklist/ban/password-change checks, we deny the
                            // request with 503 rather than let it through unchecked. The
                            // alternative (fail-open) would let a revoked/blacklisted token or a
                            // banned user bypass revocation for the duration of the Redis outage,
                            // which we consider worse than a full auth outage. This means: if
                            // Redis is down, NO authenticated route works. If this trade-off
                            // should differ per-route, that must be an explicit, reviewed change
                            // here - do not flip it silently.
                            ex -> {
                                log.error("Redis unavailable/slow during auth checks, failing closed: {}", ex.toString());
                                return deny(exchange, HttpStatus.SERVICE_UNAVAILABLE, "Auth service temporarily unavailable");
                            },
                            Mono::empty
                    );
        };
    }

    private Mono<Void> onRedisChecksResolved(ServerWebExchange exchange, GatewayFilterChain chain,
                                              String token, String userId, long issuedAtMillis,
                                              Tuple3<Boolean, Boolean, String> flags) {
        boolean blacklisted = flags.getT1();
        boolean banned = flags.getT2();
        String validAfter = flags.getT3();
        if (blacklisted) {
            log.warn("Blacklisted token attempted");
            return deny(exchange, HttpStatus.UNAUTHORIZED, "Token has been revoked");
        }
        if (banned) {
            log.warn("Banned user attempted access: {}", userId);
            return deny(exchange, HttpStatus.FORBIDDEN, "Account has been banned");
        }
        String validAfterRaw = StringUtils.hasText(validAfter)
                ? validAfter.replaceAll("^\"|\"$", "") : validAfter;
        if (StringUtils.hasText(validAfterRaw) && issuedAtMillis < Long.parseLong(validAfterRaw)) {
            log.warn("Token issued before last password change attempted: {}", userId);
            return deny(exchange, HttpStatus.UNAUTHORIZED, "Token has been revoked");
        }

        String email  = jwtUtil.extractEmail(token);
        String role   = jwtUtil.extractRole(token);
        String signature = sign(email, role, userId);

        ServerHttpRequest mutated = exchange.getRequest().mutate()
                .header("X-Auth-Email",     email)
                .header("X-Auth-Role",      role != null ? role : "")
                .header("X-Auth-UserId",    userId != null ? userId : "")
                .header("X-Auth-Signature", signature)
                .build();

        return chain.filter(exchange.mutate().request(mutated).build());
    }

    private String sign(String email, String role, String userId) {
        try {
            String message = email + ":" + (role != null ? role : "") + ":" + (userId != null ? userId : "");
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(signingSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return Base64.getEncoder().encodeToString(mac.doFinal(message.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            log.error("Failed to sign auth headers: {}", e.getMessage());
            return "";
        }
    }

    private String extractToken(ServerHttpRequest request) {
        HttpCookie cookie = request.getCookies().getFirst("sre_token");
        if (cookie != null && StringUtils.hasText(cookie.getValue())) return cookie.getValue();
        String h = request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        return (StringUtils.hasText(h) && h.startsWith("Bearer ")) ? h.substring(7) : null;
    }

    private Mono<Void> deny(ServerWebExchange exchange, HttpStatus status, String reason) {
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(status);
        response.getHeaders().add("X-Error-Reason", reason);
        return response.setComplete();
    }

    public static class Config {}
}
