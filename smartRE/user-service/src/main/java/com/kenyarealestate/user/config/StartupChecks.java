package com.kenyarealestate.user.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class StartupChecks implements CommandLineRunner {

    @Value("${s3.enabled}")
    private boolean s3Enabled;

    @Override
    public void run(String... args) {
        if (!s3Enabled) {
            log.info("############################################################");
            log.info("# USER-SERVICE: storing uploads on local disk (storage.local-dir)");
            log.info("# Durable as long as the container's volume persists, but not");
            log.info("# shared across replicas if this service is scaled horizontally.");
            log.info("# Set S3_ENABLED=true with real credentials for a multi-instance");
            log.info("# or multi-region production deployment.");
            log.info("############################################################");
        }
    }
}
