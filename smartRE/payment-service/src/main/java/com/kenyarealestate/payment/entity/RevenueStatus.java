package com.kenyarealestate.payment.entity;

public enum RevenueStatus {
    PENDING,
    /**
     * Claimed by an in-flight releaseEscrowWithPayout() call: the payment row lock has
     * already validated this is the only caller allowed to talk to M-Pesa for this
     * payment, and we are about to (or are currently) calling the B2C API. Any concurrent
     * or retried call that observes this status MUST NOT call B2C again — it should
     * short-circuit. This is what makes the escrow-release flow idempotent under retries.
     */
    PAYOUT_INITIATING,
    PAYOUT_INITIATED,
    PAYOUT_COMPLETED,
    PAYOUT_FAILED
}
