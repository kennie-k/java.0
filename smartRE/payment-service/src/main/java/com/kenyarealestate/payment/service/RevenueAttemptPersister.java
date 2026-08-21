package com.kenyarealestate.payment.service;

import com.kenyarealestate.payment.entity.CompanyRevenue;
import com.kenyarealestate.payment.entity.Payment;
import com.kenyarealestate.payment.entity.PaymentStatus;
import com.kenyarealestate.payment.entity.PaymentType;
import com.kenyarealestate.payment.entity.RevenueStatus;
import com.kenyarealestate.payment.entity.RevenueType;
import com.kenyarealestate.payment.repository.CompanyRevenueRepository;
import com.kenyarealestate.payment.repository.PaymentRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.EnumSet;
import java.util.Set;
import java.util.UUID;

/**
 * Owns every short, lock-scoped database transition of the escrow-release / payout
 * state machine. Each method here is its own REQUIRES_NEW transaction so that:
 *   - the pessimistic row lock on `payments` is only ever held for fast, local
 *     validation/state-transition work, never across a slow synchronous M-Pesa
 *     B2C HTTP call, and
 *   - the "claim" of a payout attempt (PENDING/FAILED -> PAYOUT_INITIATING) is
 *     committed durably and atomically with that same lock, so a concurrent or
 *     retried release call can never slip through and trigger a second real
 *     B2C transfer for the same payment.
 */
@Slf4j
@Service
public class RevenueAttemptPersister {

    private static final Set<RevenueStatus> IN_FLIGHT_OR_DONE = EnumSet.of(
            RevenueStatus.PAYOUT_INITIATING, RevenueStatus.PAYOUT_INITIATED, RevenueStatus.PAYOUT_COMPLETED);

    private final CompanyRevenueRepository revenueRepo;
    private final PaymentRepository paymentRepo;

    public RevenueAttemptPersister(CompanyRevenueRepository revenueRepo, PaymentRepository paymentRepo) {
        this.revenueRepo = revenueRepo;
        this.paymentRepo = paymentRepo;
    }

    public record PreparedRelease(
            boolean shortCircuited,
            CompanyRevenue revenue,
            BigDecimal gross,
            BigDecimal fee,
            BigDecimal payout,
            UUID buyerId,
            UUID sellerId,
            UUID propertyId,
            PaymentType paymentType,
            String phoneNumber) {
    }

    /**
     * Locks the payment row (PESSIMISTIC_WRITE), validates it's payable, and atomically
     * claims (or re-claims, after a prior failure) the payout attempt by moving the
     * revenue row to PAYOUT_INITIATING — all within one short transaction. The lock is
     * released as soon as this method returns (transaction commits), i.e. BEFORE any
     * external M-Pesa call is made.
     *
     * If a payout for this payment is already in flight or done (PAYOUT_INITIATING,
     * PAYOUT_INITIATED or PAYOUT_COMPLETED), returns shortCircuited=true and the caller
     * MUST NOT call B2C again.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public PreparedRelease lockValidateAndClaim(UUID paymentId, BigDecimal commissionPct,
                                                 String payoutMethod, String payeeIdentifier,
                                                 UUID adminId, String notes, String accountName) {
        Payment payment = paymentRepo.findByIdForUpdate(paymentId)
                .orElseThrow(() -> new RuntimeException("Payment not found: " + paymentId));

        if (payment.getStatus() != PaymentStatus.COMPLETED) {
            throw new RuntimeException("Cannot release escrow on payment with status: " + payment.getStatus());
        }
        if (payment.isEscrowReleased()) {
            throw new RuntimeException("Escrow already released for payment: " + paymentId);
        }

        BigDecimal gross = payment.getAmount();
        BigDecimal fee = gross.multiply(commissionPct).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
        BigDecimal payout = gross.subtract(fee);

        CompanyRevenue revenue = revenueRepo.findByPaymentId(paymentId).orElse(null);

        if (revenue != null && IN_FLIGHT_OR_DONE.contains(revenue.getStatus())) {
            log.warn("Escrow release short-circuited for paymentId={}: payout already {} (revenueId={})",
                    paymentId, revenue.getStatus(), revenue.getId());
            return new PreparedRelease(true, revenue, gross, fee, payout,
                    payment.getBuyerId(), payment.getSellerId(), payment.getPropertyId(),
                    payment.getPaymentType(), payment.getPhoneNumber());
        }

        if (revenue == null) {
            revenue = CompanyRevenue.builder()
                    .paymentId(paymentId)
                    .buyerId(payment.getBuyerId())
                    .sellerId(payment.getSellerId())
                    .propertyId(payment.getPropertyId())
                    .revenueType(RevenueType.TRANSACTION_COMMISSION)
                    .grossAmount(gross).platformFee(fee).sellerPayout(payout).feePercentage(commissionPct)
                    .releasedByAdminId(adminId).releaseNotes(notes)
                    .payoutMethod(payoutMethod).payoutIdentifier(payeeIdentifier).payoutAccountName(accountName)
                    .status(RevenueStatus.PAYOUT_INITIATING)
                    .build();
        } else {
            // Previous attempt was PENDING or PAYOUT_FAILED -- safe to reclaim and retry.
            revenue.setStatus(RevenueStatus.PAYOUT_INITIATING);
            revenue.setPayoutMethod(payoutMethod);
            revenue.setPayoutIdentifier(payeeIdentifier);
            revenue.setPayoutAccountName(accountName);
            revenue.setReleasedByAdminId(adminId);
            revenue.setReleaseNotes(notes);
            revenue.setPayoutFailureReason(null);
        }
        revenue = revenueRepo.save(revenue);

        return new PreparedRelease(false, revenue, gross, fee, payout,
                payment.getBuyerId(), payment.getSellerId(), payment.getPropertyId(),
                payment.getPaymentType(), payment.getPhoneNumber());
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markFailed(UUID revenueId, String failureReason) {
        CompanyRevenue r = revenueRepo.findById(revenueId).orElseThrow();
        r.setStatus(RevenueStatus.PAYOUT_FAILED);
        r.setPayoutFailureReason(failureReason);
        revenueRepo.save(r);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public CompanyRevenue markInitiated(UUID revenueId, String conversationId, String originatorConversationId) {
        CompanyRevenue r = revenueRepo.findById(revenueId).orElseThrow();
        r.setStatus(RevenueStatus.PAYOUT_INITIATED);
        r.setB2cConversationId(conversationId);
        r.setB2cOriginatorConversationId(originatorConversationId);
        return revenueRepo.save(r);
    }

    /**
     * Flips the payment's escrowReleased flag in its own short, locked transaction.
     * Called immediately after markInitiated() succeeds so the "unreconciled" window
     * (B2C accepted but payment not yet flagged) is as small as possible.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void finalizeEscrowRelease(UUID paymentId, UUID adminId) {
        Payment payment = paymentRepo.findByIdForUpdate(paymentId)
                .orElseThrow(() -> new RuntimeException("Payment not found: " + paymentId));
        if (payment.isEscrowReleased()) return;
        payment.setEscrowReleased(true);
        payment.setEscrowReleasedAt(LocalDateTime.now());
        payment.setEscrowReleasedBy(adminId);
        paymentRepo.save(payment);
    }
}
