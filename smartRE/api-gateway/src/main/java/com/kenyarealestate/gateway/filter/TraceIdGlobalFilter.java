package com.kenyarealestate.gateway.filter;

import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.UUID;

@Slf4j
@Component
public class TraceIdGlobalFilter implements GlobalFilter, Ordered {

    public static final String TRACE_HEADER = "X-Correlation-Id";

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String existingTraceId = exchange.getRequest().getHeaders().getFirst(TRACE_HEADER);
        String traceId = StringUtils.hasText(existingTraceId) ? existingTraceId : UUID.randomUUID().toString();

        ServerHttpRequest request = exchange.getRequest().mutate()
                .header(TRACE_HEADER, traceId)
                .build();

        exchange.getResponse().getHeaders().add(TRACE_HEADER, traceId);

        return chain.filter(exchange.mutate().request(request).build());
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }
}
