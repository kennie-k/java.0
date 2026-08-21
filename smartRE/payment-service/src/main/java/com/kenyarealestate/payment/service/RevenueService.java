package com.kenyarealestate.payment.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kenyarealestate.payment.client.MpesaB2cClient;
import com.kenyarealestate.payment.client.PropertyServiceClient;
import com.kenyarealestate.payment.dto.ReleaseEscrowRequest;
import com.kenyarealestate.payment.dto.PaymentReceiptResponse;
import com.kenyarealestate.payment.dto.RevenueResponse;
import com.kenyarealestate.payment.dto.RevenueSummaryResponse;
import com.kenyarealestate.payment.entity.*;
import com.kenyarealestate.payment.repository.CompanyRevenueRepository;
import com.kenyarealestate.payment.repository.PaymentRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.temporal.TemporalAdjusters;
import java.util.UUID;

@Slf4j
@Service
public class RevenueService {

    private final CompanyRevenueRepository revenueRepo;
    private final PaymentRepository paymentRepo;
    private final MpesaB2cClient b2cClient;
    private final PaymentAuditService auditService;
    private final ReceiptService receiptService;
    private final RevenueAttemptPersister revenueAttemptPersister;
    private final PropertyServiceClient propertyServiceClient;

    @Value("${platform.viewing-fee-kes:200}")
    private BigDecimal viewingFeeKes;

    @Value("${platform.transaction-commission-pct:2.5}")
    private BigDecimal commissionPct;

    public RevenueService(CompanyRevenueRepository revenueRepo,
                          PaymentRepository paymentRepo,
                          MpesaB2cClient b2cClient,
                          PaymentAuditService auditService,
                          ReceiptService receiptService,
                          RevenueAttemptPersister revenueAttemptPersister,
                          PropertyServiceClient propertyServiceClient) {
        this.revenueRepo = revenueRepo;
        this.paymentRepo = paymentRepo;
        this.b2cClient = b2cClient;
        this.auditService = auditService;
        this.receiptService = receiptService;
        this.revenueAttemptPersister = revenueAttemptPersister;
        this.propertyServiceClient = propertyServiceClient;
    }

    @Transactional
    public CompanyRevenue recordViewingFee(UUID paymentId, UUID buyerId,
                                            UUID sellerId, UUID propertyId) {
        if (revenueRepo.existsByPaymentId(paymentId)) {
            return revenueRepo.findByPaymentId(paymentId).orElseThrow();
        }
        CompanyRevenue rev = revenueRepo.save(CompanyRevenue.builder()
                .paymentId(paymentId)
                .buyerId(buyerId)
                .sellerId(sellerId)
                .propertyId(propertyId)
                .revenueType(RevenueType.VIEWING_FEE)
                .grossAmount(viewingFeeKes)
                .platformFee(viewingFeeKes)
                .sellerPayout(BigDecimal.ZERO)
                .feePercentage(BigDecimal.valueOf(100))
                .payoutMethod("MPESA_B2C")
                .status(RevenueStatus.PAYOUT_COMPLETED)
                .build());

        auditService.log(paymentId, rev.getId(), "VIEWING_FEE_RECORDED",
                null, "PAYOUT_COMPLETED",
                null, "SYSTEM", null,
                null, viewingFeeKes,
                "Viewing fee KES " + viewingFeeKes + " collected in full by platform");
        return rev;
    }

    /**
     * Deliberately NOT @Transactional at this level. Every database mutation this method
     * performs is delegated to a short, independently-committed REQUIRES_NEW transaction
     * (RevenueAttemptPersister / ReceiptService), so:
     *   1. the pessimistic lock on the payment row is only held for fast local
     *      validation/state-transition work (lockValidateAndClaim), never across the
     *      slow synchronous M-Pesa B2C HTTP call, and
     *   2. a retried call (double-click, client timeout-and-retry, redelivered request)
     *      can never trigger a second real B2C transfer: lockValidateAndClaim() atomically
     *      claims the payout by moving the revenue row to PAYOUT_INITIATING under the
     *      payment row lock, and any concurrent/retried caller that observes
     *      PAYOUT_INITIATING/PAYOUT_INITIATED/PAYOUT_COMPLETED short-circuits instead of
     *      calling B2C again.
     */
    public RevenueResponse releaseEscrowWithPayout(UUID paymentId, UUID adminId,
                                                    String adminIp, ReleaseEscrowRequest req) {

        PayoutMethod method = PayoutMethod.valueOf(req.getPayoutMethod().toUpperCase());
        String payeeIdentifier = switch (method) {
            case MPESA_B2C     -> req.getSellerPhone();
            case MPESA_PAYBILL -> req.getPaybillNumber() + "|" + req.getAccountNumber();
            case MPESA_TILL    -> req.getTillNumber();
            case BANK_TRANSFER -> req.getBankAccountNumber();
        };

        RevenueAttemptPersister.PreparedRelease prepared = revenueAttemptPersister.lockValidateAndClaim(
                paymentId, commissionPct, method.name(), payeeIdentifier, adminId, req.getNotes(), req.getAccountName());

        if (prepared.shortCircuited()) {
            CompanyRevenue existing = prepared.revenue();
            auditService.log(paymentId, existing.getId(), "ESCROW_RELEASE_DUPLICATE_SKIPPED",
                    existing.getStatus().name(), existing.getStatus().name(),
                    adminId, "ADMIN", adminIp,
                    null, existing.getSellerPayout(),
                    "Retried/duplicate escrow-release request for paymentId=" + paymentId
                            + " ignored: a payout is already " + existing.getStatus()
                            + " (revenueId=" + existing.getId() + "). No second B2C call was made.");
            log.warn("Duplicate escrow release request ignored for paymentId={} (revenue already {})",
                    paymentId, existing.getStatus());

            // Reconcile: if the payout actually completed but the payment flag was never
            // set (e.g. the process crashed between markInitiated and finalizeEscrowRelease
            // on the original attempt), fix that up now rather than leaving it inconsistent.
            if (existing.getStatus() == RevenueStatus.PAYOUT_COMPLETED) {
                revenueAttemptPersister.finalizeEscrowRelease(paymentId, adminId);
            }
            return toResponse(existing);
        }

        UUID revenueId = prepared.revenue().getId();
        BigDecimal fee = prepared.fee();
        BigDecimal payout = prepared.payout();

        auditService.log(paymentId, revenueId, "ESCROW_RELEASE_INITIATED",
                "HELD", "PAYOUT_INITIATING",
                adminId, "ADMIN", adminIp,
                null, prepared.gross(),
                "Admin " + adminId + " releasing escrow. Method=" + method.name()
                        + " Payee=" + payeeIdentifier
                        + " Gross=" + prepared.gross() + " Fee=" + fee + " Payout=" + payout);

        MpesaB2cClient.B2cResult result = switch (method) {
            case MPESA_B2C     -> b2cClient.payToPhone(req.getSellerPhone(), payout.toPlainString(),
                    "SmartRE seller payout " + paymentId, revenueId);
            case MPESA_PAYBILL -> b2cClient.payToPaybill(req.getPaybillNumber(), req.getAccountNumber(),
                    payout.toPlainString(), "SmartRE payout " + paymentId, revenueId);
            case MPESA_TILL    -> b2cClient.payToTill(req.getTillNumber(), payout.toPlainString(),
                    "SmartRE payout " + paymentId, revenueId);
            case BANK_TRANSFER -> {
                log.warn("Bank transfer payout not yet automated paymentId={}", paymentId);
                yield new MpesaB2cClient.B2cResult(false, null, null, "BANK_TRANSFER_MANUAL");
            }
        };

        if (!result.success()) {
            revenueAttemptPersister.markFailed(revenueId, result.description());
            auditService.log(paymentId, revenueId, "PAYOUT_FAILED",
                    "PAYOUT_INITIATING", "PAYOUT_FAILED",
                    adminId, "ADMIN", adminIp,
                    null, payout,
                    "Payout failed: " + result.description());
            log.error("Payout failed paymentId={} reason={}", paymentId, result.description());
            throw new RuntimeException("Payout failed: " + result.description()
                    + ". Escrow was not released — correct the payout details and try again.");
        }

        CompanyRevenue revenue = revenueAttemptPersister.markInitiated(
                revenueId, result.conversationId(), result.originatorConversationId());
        auditService.log(paymentId, revenueId, "PAYOUT_INITIATED",
                "PAYOUT_INITIATING", "PAYOUT_INITIATED",
                adminId, "ADMIN", adminIp,
                null, payout,
                "B2C initiated. ConversationID=" + result.conversationId()
                        + " OriginatorConversationID=" + result.originatorConversationId());

        // Flip the escrow flag immediately so the unreconciled window is as small as
        // possible — everything after this point is best-effort follow-up, not a
        // condition for whether the payout itself is considered "released".
        revenueAttemptPersister.finalizeEscrowRelease(paymentId, adminId);

        Payment payment = paymentRepo.findById(paymentId).orElseThrow();
        receiptService.issueReceipt(payment, fee, payout, payeeIdentifier, method.name(), req.getAccountName());

        if (prepared.paymentType() == PaymentType.FULL_PAYMENT || prepared.paymentType() == PaymentType.DEPOSIT) {
            propertyServiceClient.markTransactionComplete(prepared.propertyId());
        }

        return toResponse(revenue);
    }

    @Transactional
    public void rejectAndRefund(UUID paymentId, UUID adminId, String adminIp, String reason) {
        Payment payment = paymentRepo.findById(paymentId)
                .orElseThrow(() -> new RuntimeException("Payment not found: " + paymentId));

        if (payment.getStatus() != PaymentStatus.COMPLETED) {
            throw new RuntimeException("Cannot refund payment with status: " + payment.getStatus());
        }
        if (payment.isEscrowReleased()) {
            throw new RuntimeException("Cannot refund: escrow has already been released to the seller for payment " + paymentId);
        }

        auditService.log(paymentId, null, "ESCROW_RELEASE_INITIATED",
                "HELD", "REFUND_INITIATING",
                adminId, "ADMIN", adminIp,
                null, payment.getAmount(),
                "Admin " + adminId + " rejecting deal and refunding buyer. Reason=" + reason);

        MpesaB2cClient.B2cResult result = b2cClient.payToPhone(
                payment.getPhoneNumber(), payment.getAmount().toPlainString(),
                "SmartRE refund " + paymentId, paymentId);

        if (result.success()) {
            payment.setStatus(PaymentStatus.REFUNDED);
            paymentRepo.save(payment);
            receiptService.voidReceiptIfExists(paymentId, "Payment refunded: " + reason);
            auditService.log(paymentId, null, "PAYOUT_INITIATED",
                    "REFUND_INITIATING", "REFUNDED",
                    adminId, "ADMIN", adminIp,
                    null, payment.getAmount(),
                    "Refund initiated. ConversationID=" + result.conversationId()
                            + " OriginatorConversationID=" + result.originatorConversationId()
                            + " Reason=" + reason);
            log.info("Refund initiated paymentId={} conversationId={}", paymentId, result.conversationId());
        } else {
            auditService.log(paymentId, null, "PAYOUT_FAILED",
                    "REFUND_INITIATING", "COMPLETED",
                    adminId, "ADMIN", adminIp,
                    null, payment.getAmount(),
                    "Refund initiation failed: " + result.description());
            throw new RuntimeException("Failed to initiate refund: " + result.description());
        }
    }

    @Transactional
    public void refundViewingFee(UUID paymentId, String reason) {
        Payment payment = paymentRepo.findById(paymentId)
                .orElseThrow(() -> new RuntimeException("Payment not found: " + paymentId));

        if (payment.getPaymentType() != PaymentType.VIEWING_FEE) {
            throw new RuntimeException("Not a viewing fee payment: " + paymentId);
        }
        if (payment.getStatus() != PaymentStatus.COMPLETED) {
            throw new RuntimeException("Cannot refund payment with status: " + payment.getStatus());
        }

        MpesaB2cClient.B2cResult result = b2cClient.payToPhone(
                payment.getPhoneNumber(), payment.getAmount().toPlainString(),
                "SmartRE viewing fee refund " + paymentId, paymentId);

        if (!result.success()) {
            auditService.log(paymentId, null, "VIEWING_FEE_REFUND_FAILED",
                    "COMPLETED", "COMPLETED", null, "SYSTEM", null,
                    null, payment.getAmount(),
                    "Refund initiation failed: " + result.description() + " Reason=" + reason);
            throw new RuntimeException("Failed to initiate refund: " + result.description());
        }

        payment.setStatus(PaymentStatus.REFUNDED);
        paymentRepo.save(payment);
        revenueRepo.findByPaymentId(paymentId).ifPresent(revenueRepo::delete);
        receiptService.voidReceiptIfExists(paymentId, "Viewing fee refunded: " + reason);

        auditService.log(paymentId, null, "VIEWING_FEE_REFUNDED",
                "COMPLETED", "REFUNDED", null, "SYSTEM", null,
                null, payment.getAmount(),
                "Viewing fee refunded. Reason=" + reason + " ConversationID=" + result.conversationId());
        log.info("Viewing fee refund initiated paymentId={} conversationId={}", paymentId, result.conversationId());
    }

    @Transactional
    public void handleB2cCallback(String originatorConversationId, String rawPayload,
                                   boolean success, String receipt, String failureReason,
                                   UUID revenueId) {
        revenueRepo.findByB2cOriginatorConversationId(originatorConversationId)
                .ifPresent(revenue -> {
                    if (success) {
                        revenue.setStatus(RevenueStatus.PAYOUT_COMPLETED);
                        revenue.setSellerPayoutReceipt(receipt);
                        revenue.setSellerPayoutAt(LocalDateTime.now());
                        revenueRepo.save(revenue);
                        auditService.log(revenue.getPaymentId(), revenue.getId(), "PAYOUT_COMPLETED",
                                "PAYOUT_INITIATED", "PAYOUT_COMPLETED",
                                null, "MPESA_CALLBACK", null,
                                receipt, revenue.getSellerPayout(),
                                "Safaricom B2C confirmed. Receipt=" + receipt);
                        log.info("Seller payout completed revenueId={} receipt={}", revenue.getId(), receipt);
                    } else {
                        revenue.setStatus(RevenueStatus.PAYOUT_FAILED);
                        revenue.setPayoutFailureReason(failureReason);
                        revenueRepo.save(revenue);
                        auditService.log(revenue.getPaymentId(), revenue.getId(), "PAYOUT_FAILED_CALLBACK",
                                "PAYOUT_INITIATED", "PAYOUT_FAILED",
                                null, "MPESA_CALLBACK", null,
                                null, revenue.getSellerPayout(),
                                "B2C callback failed: " + failureReason);
                        log.error("Seller payout failed revenueId={} reason={}", revenue.getId(), failureReason);
                    }
                });
    }

    @Transactional
    public void recordStatusQuerySent(UUID revenueId, String queryConversationId) {
        revenueRepo.findById(revenueId).ifPresent(r -> {
            r.setStatusQueryConversationId(queryConversationId);
            r.setStatusQuerySentAt(LocalDateTime.now());
            revenueRepo.save(r);
        });
    }

    /**
     * Handles the async result of a TransactionStatusQuery fired by B2cReconciliationJob for
     * a payout stuck in PAYOUT_INITIATED with no primary B2C callback. Deliberately only acts
     * on a clear "completed" signal — an inconclusive or failed status query is logged for
     * manual review rather than auto-marking real money movement as failed, since a false
     * PAYOUT_FAILED on a transfer that actually succeeded would be worse than staying stuck.
     */
    @Transactional
    public void handleStatusQueryCallback(String queryConversationId, String transactionStatus, String rawPayload) {
        revenueRepo.findByStatusQueryConversationId(queryConversationId).ifPresent(revenue -> {
            if (revenue.getStatus() != RevenueStatus.PAYOUT_INITIATED) {
                log.info("Ignoring status-query callback for revenueId={}: status is already {}",
                        revenue.getId(), revenue.getStatus());
                return;
            }
            boolean confirmedComplete = transactionStatus != null
                    && transactionStatus.toLowerCase().contains("complet");
            if (confirmedComplete) {
                revenue.setStatus(RevenueStatus.PAYOUT_COMPLETED);
                revenue.setSellerPayoutAt(LocalDateTime.now());
                revenueRepo.save(revenue);
                auditService.log(revenue.getPaymentId(), revenue.getId(), "PAYOUT_COMPLETED_VIA_STATUS_QUERY",
                        "PAYOUT_INITIATED", "PAYOUT_COMPLETED",
                        null, "RECONCILIATION_JOB", null,
                        null, revenue.getSellerPayout(),
                        "Resolved via TransactionStatusQuery after the primary B2C callback never arrived. "
                                + "TransactionStatus=" + transactionStatus);
                log.info("Seller payout confirmed via TransactionStatusQuery revenueId={}", revenue.getId());
            } else {
                auditService.log(revenue.getPaymentId(), revenue.getId(), "STATUS_QUERY_INCONCLUSIVE",
                        "PAYOUT_INITIATED", "PAYOUT_INITIATED",
                        null, "RECONCILIATION_JOB", null,
                        null, revenue.getSellerPayout(),
                        "TransactionStatusQuery callback did not confirm completion (status="
                                + transactionStatus + "). Still requires manual verification against Safaricom.");
                log.warn("TransactionStatusQuery inconclusive for revenueId={} status={}",
                        revenue.getId(), transactionStatus);
            }
        });
    }

    @Transactional(readOnly = true)
    public Page<RevenueResponse> getAll(Pageable pageable) {
        return revenueRepo.findAllByOrderByCreatedAtDesc(pageable).map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public RevenueSummaryResponse getSummary() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime startOfMonth = now.with(TemporalAdjusters.firstDayOfMonth())
                .withHour(0).withMinute(0).withSecond(0).withNano(0);
        LocalDateTime startOfLastMonth = startOfMonth.minusMonths(1);

        return RevenueSummaryResponse.builder()
                .totalPlatformFees(revenueRepo.sumTotalPlatformFees())
                .viewingFeeRevenue(revenueRepo.sumPlatformFeesByType(RevenueType.VIEWING_FEE))
                .commissionRevenue(revenueRepo.sumPlatformFeesByType(RevenueType.TRANSACTION_COMMISSION))
                .thisMonthRevenue(revenueRepo.sumPlatformFeesBetween(startOfMonth, now))
                .lastMonthRevenue(revenueRepo.sumPlatformFeesBetween(startOfLastMonth, startOfMonth))
                .totalTransactions(revenueRepo.count())
                .thisMonthTransactions(revenueRepo.countTransactionsBetween(startOfMonth, now))
                .currency("KES")
                .build();
    }

    private RevenueResponse toResponse(CompanyRevenue r) {
        return RevenueResponse.builder()
                .id(r.getId()).paymentId(r.getPaymentId())
                .buyerId(r.getBuyerId()).sellerId(r.getSellerId()).propertyId(r.getPropertyId())
                .revenueType(r.getRevenueType().name())
                .grossAmount(r.getGrossAmount()).platformFee(r.getPlatformFee())
                .sellerPayout(r.getSellerPayout()).feePercentage(r.getFeePercentage())
                .currency(r.getCurrency()).status(r.getStatus().name())
                .sellerPayoutReceipt(r.getSellerPayoutReceipt())
                .sellerPayoutAt(r.getSellerPayoutAt())
                .payoutFailureReason(r.getPayoutFailureReason())
                .createdAt(r.getCreatedAt())
                .build();
    }
}
