package com.kenyarealestate.payment.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

@Slf4j
@Component
public class StartupChecks implements CommandLineRunner {

    @Value("${mpesa.callback-allowed-ips:}")
    private String callbackAllowedIps;

    @Value("${mpesa.callback-url:}")
    private String callbackUrl;

    @Override
    public void run(String... args) {
        checkCallbackIpAllowlist();
        checkCallbackUrlReachable();
    }

    private void checkCallbackIpAllowlist() {
        if (!StringUtils.hasText(callbackAllowedIps)) {
            log.warn("############################################################");
            log.warn("# PAYMENT-SERVICE: M-PESA CALLBACK IP ALLOWLIST IS EMPTY");
            log.warn("# The M-Pesa callback endpoint accepts requests from ANY");
            log.warn("# source IP - only the path secret protects it. Set");
            log.warn("# MPESA_CALLBACK_ALLOWED_IPS to Safaricom's published IP");
            log.warn("# ranges before any production use.");
            log.warn("############################################################");
        }
    }

    /**
     * Safaricom confirms STK pushes via an async webhook to mpesa.callback-url. If that URL
     * doesn't actually route back to this service (dev tunnel not started, stale/reserved
     * domain gone unbound, still the placeholder default), payments silently never move past
     * STK_PUSHED and nothing else in the app surfaces an error — the reconciliation job papers
     * over it after a delay, but the underlying misconfiguration goes unnoticed indefinitely.
     * One best-effort probe at boot, short timeout, never fails startup.
     */
    private void checkCallbackUrlReachable() {
        if (!StringUtils.hasText(callbackUrl) || callbackUrl.contains("your-domain.com")) {
            log.warn("############################################################");
            log.warn("# PAYMENT-SERVICE: MPESA_CALLBACK_URL IS NOT CONFIGURED");
            log.warn("# It is unset or still the placeholder default ({})", callbackUrl);
            log.warn("# M-Pesa will never be able to confirm payments until this");
            log.warn("# points at a publicly reachable URL for this service.");
            log.warn("############################################################");
            return;
        }

        SimpleClientHttpRequestFactory f = new SimpleClientHttpRequestFactory();
        f.setConnectTimeout(4_000);
        f.setReadTimeout(4_000);
        RestTemplate probe = new RestTemplate(f);

        try {
            ResponseEntity<String> resp = probe.getForEntity(callbackUrl, String.class);
            if (looksLikeNgrokTunnelDown(resp.getHeaders().toString(), resp.getBody())) {
                warnTunnelDown();
            }
        } catch (HttpStatusCodeException e) {
            // Any HTTP status returned by OUR OWN gateway/app proves the tunnel is routing
            // through correctly — only the ngrok edge's own "no tunnel bound" page is a problem.
            String headers = e.getResponseHeaders() == null ? "" : e.getResponseHeaders().toString();
            if (looksLikeNgrokTunnelDown(headers, e.getResponseBodyAsString())) {
                warnTunnelDown();
            }
        } catch (RestClientException e) {
            log.warn("############################################################");
            log.warn("# PAYMENT-SERVICE: MPESA_CALLBACK_URL LOOKS UNREACHABLE");
            log.warn("# Could not reach {}: {}", callbackUrl, e.getMessage());
            log.warn("# M-Pesa will not be able to confirm payments until this");
            log.warn("# URL is reachable from the public internet.");
            log.warn("############################################################");
        }
    }

    private boolean looksLikeNgrokTunnelDown(String headers, String body) {
        String h = headers == null ? "" : headers.toLowerCase();
        String b = body == null ? "" : body;
        return h.contains("ngrok-error-code") || b.contains("ERR_NGROK");
    }

    private void warnTunnelDown() {
        log.warn("############################################################");
        log.warn("# PAYMENT-SERVICE: MPESA_CALLBACK_URL TUNNEL IS NOT RUNNING");
        log.warn("# {} resolves but no tunnel is bound to it (ngrok ERR_NGROK_3200).", callbackUrl);
        log.warn("# Start it, e.g.: ngrok http --domain=<your-domain> 8080");
        log.warn("# Until then, M-Pesa payments will never move past STK_PUSHED via");
        log.warn("# the callback (the reconciliation job may still resolve them");
        log.warn("# after a delay via the STK status query API).");
        log.warn("############################################################");
    }
}
