package com.kenyarealestate.payment.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.env.Environment;
import org.springframework.core.io.Resource;
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

    private final Environment environment;

    @Value("${mpesa.callback-allowed-ips:}")
    private String callbackAllowedIps;

    @Value("${mpesa.callback-insecure-allow-all-ips:false}")
    private boolean insecureAllowAll;

    @Value("${mpesa.callback-url:}")
    private String callbackUrl;

    @Value("${mpesa.security-credential-cert-path}")
    private Resource securityCredentialCert;

    public StartupChecks(Environment environment) {
        this.environment = environment;
    }

    @Override
    public void run(String... args) {
        checkCallbackIpAllowlist();
        checkCallbackUrlReachable();
        checkSecurityCredentialCert();
    }

    private void checkSecurityCredentialCert() {
        if (securityCredentialCert.isReadable()) return;
        log.warn("############################################################");
        log.warn("# PAYMENT-SERVICE: MPESA B2C CERTIFICATE NOT CONFIGURED");
        log.warn("# mpesa.security-credential-cert-path does not point at a");
        log.warn("# readable file. Every escrow release and refund payout will");
        log.warn("# fail until MPESA_SECURITY_CREDENTIAL_CERT_PATH points at a");
        log.warn("# real Safaricom public certificate (see .env.example).");
        log.warn("############################################################");
    }

    private void checkCallbackIpAllowlist() {
        if (StringUtils.hasText(callbackAllowedIps)) return;

        boolean insecureModeActive = insecureAllowAll || isLocalOrDevProfile();
        if (insecureModeActive) {
            log.warn("############################################################");
            log.warn("# PAYMENT-SERVICE: M-PESA CALLBACK IP ALLOWLIST IS EMPTY");
            log.warn("# Running in insecure/local mode: the callback endpoints accept");
            log.warn("# requests from ANY source IP - only the path secret protects");
            log.warn("# them. This is only acceptable for local development.");
            log.warn("############################################################");
        } else {
            log.error("############################################################");
            log.error("# PAYMENT-SERVICE: M-PESA CALLBACK IP ALLOWLIST IS EMPTY");
            log.error("# The callback endpoints will now REJECT every request (fail");
            log.error("# closed) because MPESA_CALLBACK_ALLOWED_IPS is unset and this");
            log.error("# is not a local/dev profile. M-Pesa payment confirmations and");
            log.error("# B2C payout callbacks will never be accepted until you set");
            log.error("# MPESA_CALLBACK_ALLOWED_IPS to Safaricom's published IP ranges.");
            log.error("############################################################");
        }
    }

    private boolean isLocalOrDevProfile() {
        for (String p : environment.getActiveProfiles()) {
            if ("local".equalsIgnoreCase(p) || "dev".equalsIgnoreCase(p) || "test".equalsIgnoreCase(p)) return true;
        }
        return false;
    }

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
