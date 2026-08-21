package com.kenyarealestate.payment.security;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * Decides whether an M-Pesa callback request's source IP is acceptable.
 *
 * Behaviour:
 *  - If mpesa.callback-allowed-ips is set, the caller IP must match it (delegates to
 *    {@link CallbackSecurity#ipAllowed}).
 *  - If it is blank, this used to silently allow ANY IP (the only real protection being
 *    the secret embedded in the callback URL path, which can leak via proxy/access logs).
 *    Now it fails CLOSED by default: the request is rejected unless we're running under
 *    the "local" or "dev" Spring profile, or the operator has explicitly opted into
 *    insecure mode via mpesa.callback-insecure-allow-all-ips=true (documented as
 *    local-development-only in .env.example).
 */
@Slf4j
@Component
public class CallbackIpPolicy {

    private final Environment environment;

    @Value("${mpesa.callback-allowed-ips:}")
    private String allowedIpsCsv;

    @Value("${mpesa.callback-insecure-allow-all-ips:false}")
    private boolean insecureAllowAll;

    public CallbackIpPolicy(Environment environment) {
        this.environment = environment;
    }

    public boolean isAllowed(String callerIp) {
        if (StringUtils.hasText(allowedIpsCsv)) {
            return CallbackSecurity.ipAllowed(callerIp, allowedIpsCsv);
        }
        if (isInsecureModePermitted()) {
            log.warn("M-Pesa callback IP check bypassed (no allowlist configured, insecure/local mode " +
                    "permitted) for caller IP {}. Do not run this way in production.", callerIp);
            return true;
        }
        log.error("M-Pesa callback REJECTED from IP {}: no IP allowlist is configured " +
                "(MPESA_CALLBACK_ALLOWED_IPS is blank) and this profile does not permit insecure mode. " +
                "Set MPESA_CALLBACK_ALLOWED_IPS to Safaricom's published ranges, or, for local development " +
                "only, set MPESA_CALLBACK_INSECURE_ALLOW_ALL_IPS=true or run with the 'local'/'dev' profile.",
                callerIp);
        return false;
    }

    private boolean isInsecureModePermitted() {
        if (insecureAllowAll) return true;
        for (String profile : environment.getActiveProfiles()) {
            if ("local".equalsIgnoreCase(profile) || "dev".equalsIgnoreCase(profile) || "test".equalsIgnoreCase(profile)) {
                return true;
            }
        }
        return false;
    }
}
