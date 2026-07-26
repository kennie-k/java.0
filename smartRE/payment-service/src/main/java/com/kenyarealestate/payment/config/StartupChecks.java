package com.kenyarealestate.payment.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Slf4j
@Component
public class StartupChecks implements CommandLineRunner {

    @Value("${mpesa.callback-allowed-ips:}")
    private String callbackAllowedIps;

    @Override
    public void run(String... args) {
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
}
