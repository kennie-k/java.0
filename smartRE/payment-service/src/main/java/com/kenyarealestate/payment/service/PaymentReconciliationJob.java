package com.kenyarealestate.payment.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Slf4j
@Component
public class PaymentReconciliationJob {

    private final PaymentService paymentService;

    private static final int GRACE_SECONDS = 25;
    private static final int TIMEOUT_SECONDS = 180;

    public PaymentReconciliationJob(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    @Scheduled(fixedDelay = 15_000, initialDelay = 20_000)
    public void reconcile() {
        var due = paymentService.findStaleStkPushed(GRACE_SECONDS);
        var timedOut = paymentService.findStaleStkPushed(TIMEOUT_SECONDS);
        for (UUID id : due) {
            try {
                paymentService.reconcileOne(id, timedOut.contains(id));
            } catch (Exception e) {
                log.error("Payment reconciliation failed for paymentId={}: {}", id, e.getMessage());
            }
        }
    }
}
