package com.kenyarealestate.payment.service;

import com.kenyarealestate.payment.client.MpesaB2cClient;
import com.kenyarealestate.payment.entity.CompanyRevenue;
import com.kenyarealestate.payment.entity.RevenueStatus;
import com.kenyarealestate.payment.repository.CompanyRevenueRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Component
public class B2cReconciliationJob {

    private final CompanyRevenueRepository revenueRepo;
    private final PaymentAuditService auditService;
    private final MpesaB2cClient b2cClient;
    private final RevenueService revenueService;

    private static final int STUCK_GRACE_MINUTES = 10;
    // Don't re-fire a TransactionStatusQuery more often than this, even though the sweep
    // itself runs every 5 minutes — Safaricom's answer arrives asynchronously via callback,
    // so hammering the query endpoint on every sweep tick wouldn't get us an answer any
    // faster and would just spam Safaricom's API.
    private static final int MIN_MINUTES_BETWEEN_QUERIES = 4;

    public B2cReconciliationJob(CompanyRevenueRepository revenueRepo, PaymentAuditService auditService,
                                 MpesaB2cClient b2cClient, RevenueService revenueService) {
        this.revenueRepo = revenueRepo;
        this.auditService = auditService;
        this.b2cClient = b2cClient;
        this.revenueService = revenueService;
    }

    @Scheduled(fixedDelay = 300_000, initialDelay = 60_000)
    public void sweep() {
        LocalDateTime cutoff = LocalDateTime.now().minusMinutes(STUCK_GRACE_MINUTES);
        List<CompanyRevenue> stuck = revenueRepo.findByStatusAndCreatedAtBefore(RevenueStatus.PAYOUT_INITIATED, cutoff);
        for (CompanyRevenue r : stuck) {
            log.error("ALERT: B2C payout stuck in PAYOUT_INITIATED for over {} minutes. revenueId={} paymentId={} "
                    + "sellerPayout={} b2cConversationId={}. Actively re-querying Safaricom for the real status; "
                    + "if this keeps recurring, verify manually via the Safaricom portal before taking any further "
                    + "action on this payment.",
                    STUCK_GRACE_MINUTES, r.getId(), r.getPaymentId(), r.getSellerPayout(), r.getB2cConversationId());
            auditService.log(r.getPaymentId(), r.getId(), "B2C_PAYOUT_STUCK_ALERT",
                    "PAYOUT_INITIATED", "PAYOUT_INITIATED",
                    null, "RECONCILIATION_JOB", null,
                    null, r.getSellerPayout(),
                    "No B2C callback received after " + STUCK_GRACE_MINUTES + " minutes. Attempting active "
                            + "TransactionStatusQuery reconciliation.");

            activelyReconcile(r);
        }
    }

    private void activelyReconcile(CompanyRevenue r) {
        if (r.getB2cOriginatorConversationId() == null) {
            log.warn("Cannot actively reconcile revenueId={}: no b2cOriginatorConversationId recorded", r.getId());
            return;
        }
        if (r.getStatusQuerySentAt() != null
                && r.getStatusQuerySentAt().isAfter(LocalDateTime.now().minusMinutes(MIN_MINUTES_BETWEEN_QUERIES))) {
            log.info("Skipping TransactionStatusQuery for revenueId={}: one was already sent at {}",
                    r.getId(), r.getStatusQuerySentAt());
            return;
        }

        MpesaB2cClient.StatusQueryResult result = b2cClient.queryTransactionStatus(
                r.getB2cOriginatorConversationId(), r.getId());

        if (!result.accepted()) {
            log.warn("TransactionStatusQuery request for revenueId={} was not accepted by Safaricom: {}",
                    r.getId(), result.description());
            return;
        }

        String correlationId = result.queryOriginatorConversationId() != null
                ? result.queryOriginatorConversationId() : result.queryConversationId();
        revenueService.recordStatusQuerySent(r.getId(), correlationId);
        log.info("TransactionStatusQuery accepted for revenueId={}, awaiting async callback (correlationId={})",
                r.getId(), correlationId);
    }
}
