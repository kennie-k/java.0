package com.kenyarealestate.verification.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class StartupChecks implements CommandLineRunner {

    @Value("${services.smile-identity-enabled}")
    private boolean smileIdentityEnabled;

    @Value("${services.ardhisasa-enabled}")
    private boolean ardhisasaEnabled;

    @Value("${services.document-analysis-enabled}")
    private boolean documentAnalysisEnabled;

    @Override
    public void run(String... args) {
        if (!smileIdentityEnabled || !ardhisasaEnabled || !documentAnalysisEnabled) {
            log.warn("############################################################");
            log.warn("# VERIFICATION-SERVICE: MANUAL-REVIEW-ONLY MODE");
            log.warn("# One or more automated verification providers are disabled:");
            log.warn("#   smile-identity (biometric ID check): {}", smileIdentityEnabled ? "enabled" : "DISABLED");
            log.warn("#   ardhisasa (land registry check):     {}", ardhisasaEnabled ? "enabled" : "DISABLED");
            log.warn("#   document-analysis (forgery AI):      {}", documentAnalysisEnabled ? "enabled" : "DISABLED");
            log.warn("# All submissions with a disabled check route straight to");
            log.warn("# HUMAN_REVIEW with no automated screening. This is NOT");
            log.warn("# production-ready trust assurance until real credentials");
            log.warn("# are configured for these providers.");
            log.warn("############################################################");
        }
    }
}
