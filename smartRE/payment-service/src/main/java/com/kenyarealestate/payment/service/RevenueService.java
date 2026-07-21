package com.kenyarealestate.payment.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kenyarealestate.payment.client.MpesaB2cClient;
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
import java.math.RoundingMode;
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

    @Value("${platform.viewing-fee-kes:200}")
    private BigDecimal viewingFeeKes;

    @Value("${platform.transaction-commission-pct:2.5}")
    private BigDecimal commissionPct;

    public RevenueService(CompanyRevenueRepository revenueRepo,
                          PaymentRepository paymentRepo,
                          MpesaB2cClient b2cClient,
                          PaymentAuditService auditService,
                          ReceiptService receiptService) {
        this.revenueRepo = revenueRepo;
        this.paymentRepo = paymentRepo;
        this.b2cClient = b2cClient;
        this.auditService = auditService;
        this.receiptService = receiptService;
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

    @Transactional
    public RevenueResponse releaseEscrowWithPayout(UUID paymentId, UUID adminId,
                                                    String adminIp, ReleaseEscrowRequest req) {
        Payment payment = paymentRepo.findById(paymentId)
                .orElseThrow(() -> new RuntimeException("Payment not found: " + paymentId));

        if (payment.getStatus() != PaymentStatus.COMPLETED) {
            throw new RuntimeException("Cannot release escrow on payment with status: " + payment.getStatus());
        }
        if (payment.isEscrowReleased()) {
            throw new RuntimeException("Escrow already released for payment: " + paymentId);
        }

        BigDecimal gross = payment.getAmount();
        BigDecimal fee = gross.multiply(commissionPct)
                .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
        BigDecimal payout = gross.subtract(fee);

        PayoutMethod method = PayoutMethod.valueOf(req.getPayoutMethod().toUpperCase());

        String payeeIdentifier = switch (method) {
            case MPESA_B2C     -> req.getSellerPhone();
            case MPESA_PAYBILL -> req.getPaybillNumber() + "|" + req.getAccountNumber();
            case MPESA_TILL    -> req.getTillNumber();
            case BANK_TRANSFER -> req.getBankAccountNumber();
        };

        CompanyRevenue revenue;
        if (revenueRepo.existsByPaymentId(paymentId)) {
            revenue = revenueRepo.findByPaymentId(paymentId).orElseThrow();
        } else {
            revenue = revenueRepo.save(CompanyRevenue.builder()
                    .paymentId(paymentId)
                    .buyerId(payment.getBuyerId())
                    .sellerId(payment.getSellerId())
                    .propertyId(payment.getPropertyId())
                    .revenueType(RevenueType.TRANSACTION_COMMISSION)
                    .grossAmount(gross)
                    .platformFee(fee)
                    .sellerPayout(payout)
                    .feePercentage(commissionPct)
                    .releasedByAdminId(adminId)
                    .releaseNotes(req.getNotes())
                    .payoutMethod(method.name())
                    .payoutIdentifier(payeeIdentifier)
                    .payoutAccountName(req.getAccountName())
                    .build());
        }

        auditService.log(paymentId, revenue.getId(), "ESCROW_RELEASE_INITIATED",
                "HELD", "PAYOUT_INITIATING",
                adminId, "ADMIN", adminIp,
                null, gross,
                "Admin " + adminId + " releasing escrow. Method=" + method.name()
                        + " Payee=" + payeeIdentifier
                        + " Gross=" + gross + " Fee=" + fee + " Payout=" + payout);

        MpesaB2cClient.B2cResult result = switch (method) {
            case MPESA_B2C     -> b2cClient.payToPhone(req.getSellerPhone(), payout.toPlainString(),
                    "SmartRE seller payout " + paymentId, revenue.getId());
            case MPESA_PAYBILL -> b2cClient.payToPaybill(req.getPaybillNumber(), req.getAccountNumber(),
                    payout.toPlainString(), "SmartRE payout " + paymentId, revenue.getId());
            case MPESA_TILL    -> b2cClient.payToTill(req.getTillNumber(), payout.toPlainString(),
                    "SmartRE payout " + paymentId, revenue.getId());
            case BANK_TRANSFER -> {
                log.warn("Bank transfer payout not yet automated paymentId={}", paymentId);
                yield new MpesaB2cClient.B2cResult(false, null, null, "BANK_TRANSFER_MANUAL");
            }
        };

        if (result.success()) {
            revenue.setStatus(RevenueStatus.PAYOUT_INITIATED);
            revenue.setB2cConversationId(result.conversationId());
            revenue.setB2cOriginatorConversationId(result.originatorConversationId());
            auditService.log(paymentId, revenue.getId(), "PAYOUT_INITIATED",
                    "PENDING", "PAYOUT_INITIATED",
                    adminId, "ADMIN", adminIp,
                    null, payout,
                    "B2C initiated. ConversationID=" + result.conversationId()
                            + " OriginatorConversationID=" + result.originatorConversationId());
        } else {
            revenue.setStatus(RevenueStatus.PAYOUT_FAILED);
            revenue.setPayoutFailureReason(result.description());
            auditService.log(paymentId, revenue.getId(), "PAYOUT_FAILED",
                    "PENDING", "PAYOUT_FAILED",
                    adminId, "ADMIN", adminIp,
                    null, payout,
                    "Payout failed: " + result.description());
            log.error("Payout failed paymentId={} reason={}", paymentId, result.description());
        }
        revenueRepo.save(revenue);

        payment.setEscrowReleased(true);
        payment.setEscrowReleasedAt(LocalDateTime.now());
        payment.setEscrowReleasedBy(adminId);
        paymentRepo.save(payment);

        receiptService.issueReceipt(payment, fee, payout, payeeIdentifier, method.name(), req.getAccountName());

        return toResponse(revenue);
    }

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
