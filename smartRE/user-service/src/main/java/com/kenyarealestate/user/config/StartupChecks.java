package com.kenyarealestate.user.config;

import com.kenyarealestate.user.repository.UserRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class StartupChecks implements CommandLineRunner {

    @Value("${s3.enabled}")
    private boolean s3Enabled;

    private final UserRepository userRepository;

    public StartupChecks(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

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

        if (!userRepository.existsBySuperAdminTrue()) {
            log.warn("############################################################");
            log.warn("# USER-SERVICE: no super admin account exists.");
            log.warn("# V5__super_admin.sql promotes a specific account by email on");
            log.warn("# migration run - if that email doesn't exist in this database,");
            log.warn("# the UPDATE silently matches zero rows and nobody gets promoted.");
            log.warn("# The system currently has no undeletable admin account.");
            log.warn("############################################################");
        }
    }
}
