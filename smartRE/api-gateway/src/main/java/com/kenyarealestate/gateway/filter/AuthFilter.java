package com.kenyarealestate.gateway.filter;

import com.kenyarealestate.gateway.security.JwtUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilter;
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

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

@Slf4j
@Component
public class AuthFilter extends AbstractGatewayFilterFactory<AuthFilter.Config> {

    private final JwtUtil jwtUtil;
    private final ReactiveRedisTemplate<String, String> redis;
    private static final String BLACKLIST_PREFIX = "jwt:blacklist:";

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

            return redis.hasKey(BLACKLIST_PREFIX + token)
                    .defaultIfEmpty(false)
                    .flatMap(blacklisted -> {
                        if (blacklisted) {
                            log.warn("Blacklisted token attempted");
                            return deny(exchange, HttpStatus.UNAUTHORIZED, "Token has been revoked");
                        }

                        String email  = jwtUtil.extractEmail(token);
                        String role   = jwtUtil.extractRole(token);
                        String userId = jwtUtil.extractUserId(token);
                        String signature = sign(email, role, userId);

                        ServerHttpRequest mutated = exchange.getRequest().mutate()
                                .header("X-Auth-Email",     email)
                                .header("X-Auth-Role",      role != null ? role : "")
                                .header("X-Auth-UserId",    userId != null ? userId : "")
                                .header("X-Auth-Signature", signature)
                                .build();

                        return chain.filter(exchange.mutate().request(mutated).build());
                    });
        };
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
