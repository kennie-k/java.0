package com.kenyarealestate.viewing.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

/**
 * Reads the X-Correlation-Id header set by the api-gateway (see
 * TraceIdGlobalFilter in api-gateway) and puts it into the logging MDC so log
 * lines emitted while handling this request can be correlated across
 * services in Loki/Grafana (search/filter logs by correlationId across
 * user-service, payment-service, viewing-service, etc.).
 *
 * Falls back to a locally generated id for requests that reach this service
 * without going through the gateway (direct internal calls, local dev),
 * so every request still gets a correlation id even if one wasn't handed in.
 *
 * Registered explicitly (not via @Component) with HIGHEST_PRECEDENCE order
 * in {@link com.kenyarealestate.viewing.config.CorrelationIdFilterConfig} so
 * the MDC value is set before Spring Security's filter chain runs, covering
 * log lines emitted by auth failures too.
 */
public class CorrelationIdFilter extends OncePerRequestFilter {

    public static final String HEADER = "X-Correlation-Id";
    public static final String MDC_KEY = "correlationId";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String correlationId = request.getHeader(HEADER);
        if (!StringUtils.hasText(correlationId)) {
            correlationId = UUID.randomUUID().toString();
        }
        MDC.put(MDC_KEY, correlationId);
        response.setHeader(HEADER, correlationId);
        try {
            filterChain.doFilter(request, response);
        } finally {
            MDC.remove(MDC_KEY);
        }
    }
}
