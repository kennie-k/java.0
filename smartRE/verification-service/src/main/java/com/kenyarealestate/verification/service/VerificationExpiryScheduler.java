package com.kenyarealestate.verification.service;

import com.kenyarealestate.verification.entity.SellerIdentityVerification;
import com.kenyarealestate.verification.enums.IdentityVerificationStatus;
import com.kenyarealestate.verification.repository.SellerIdentityVerificationRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Component
public class VerificationExpiryScheduler {

    private final SellerIdentityVerificationRepository identityRepo;
    private final AuditService auditService;
    private final TrustStatusService trustStatusService;

    public VerificationExpiryScheduler(SellerIdentityVerificationRepository identityRepo,
                                        AuditService auditService,
                                        TrustStatusService trustStatusService) {
        this.identityRepo      = identityRepo;
        this.auditService      = auditService;
        this.trustStatusService = trustStatusService;
    }

    @Scheduled(cron = "0 0 2 * * *")
    @Transactional
    public void expireStaleVerifications() {
        log.info("Running verification expiry job at {}", LocalDateTime.now());

        List<SellerIdentityVerification> expired = identityRepo
                .findByStatusAndExpiresAtBefore(
                        IdentityVerificationStatus.APPROVED,
                        LocalDateTime.now());

        if (expired.isEmpty()) {
            log.info("No verifications to expire");
            return;
        }

        log.info("Expiring {} verifications", expired.size());

        for (SellerIdentityVerification verif : expired) {
            String prev = verif.getStatus().name();
            verif.setStatus(IdentityVerificationStatus.EXPIRED);
            identityRepo.save(verif);

            trustStatusService.evictCache(verif.getUserId());

            auditService.log(verif.getId(), "IDENTITY", "EXPIRED_BY_SCHEDULER",
                    null, "SYSTEM", prev, "EXPIRED",
                    "Verification expired at " + verif.getExpiresAt());

            log.info("Expired verification id={} userId={} expiredAt={}",
                    verif.getId(), verif.getUserId(), verif.getExpiresAt());
        }

        log.info("Expiry job complete: {} verifications marked EXPIRED", expired.size());
    }
}
