package com.kenyarealestate.payment.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kenyarealestate.payment.client.MpesaClient;
import com.kenyarealestate.payment.dto.*;
import com.kenyarealestate.payment.entity.*;
import com.kenyarealestate.payment.kafka.PaymentEventPublisher;
import com.kenyarealestate.payment.repository.MpesaRawCallbackRepository;
import com.kenyarealestate.payment.repository.PaymentRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
public class PaymentService {

    private final PaymentRepository repo;
    private final MpesaClient mpesaClient;
    private final PaymentLockService lockService;
    private final PaymentEventPublisher eventPublisher;
    private final PaymentAuditService auditService;
    private final MpesaRawCallbackRepository rawCallbackRepo;
    private final ReceiptService receiptService;
    private final org.springframework.data.redis.core.RedisTemplate<String, Object> redis;
    private final RevenueService revenueService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private static final String PROFILE_ACCESS_PREFIX = "profile:access:";

    @org.springframework.beans.factory.annotation.Value("${platform.viewing-fee-kes:200}")
    private BigDecimal viewingFeeKes;

    @org.springframework.beans.factory.annotation.Value("${platform.profile-access-fee-kes:500}")
    private BigDecimal profileAccessFeeKes;

    @org.springframework.beans.factory.annotation.Value("${platform.transaction-commission-pct:2.5}")
    private BigDecimal commissionPct;

    public BigDecimal getViewingFeeKes() { return viewingFeeKes; }
    public BigDecimal getProfileAccessFeeKes() { return profileAccessFeeKes; }
    public BigDecimal getCommissionPct() { return commissionPct; }

    private final com.kenyarealestate.payment.client.PropertyServiceClient propertyServiceClient;

    public PaymentService(PaymentRepository repo,
                          MpesaClient mpesaClient,
                          PaymentLockService lockService,
                          PaymentEventPublisher eventPublisher,
                          PaymentAuditService auditService,
                          MpesaRawCallbackRepository rawCallbackRepo,
                          ReceiptService receiptService,
                          org.springframework.data.redis.core.RedisTemplate<String, Object> redis,
                          com.kenyarealestate.payment.client.PropertyServiceClient propertyServiceClient,
                          RevenueService revenueService) {
        this.repo = repo;
        this.mpesaClient = mpesaClient;
        this.lockService = lockService;
        this.eventPublisher = eventPublisher;
        this.auditService = auditService;
        this.rawCallbackRepo = rawCallbackRepo;
        this.receiptService = receiptService;
        this.redis = redis;
        this.propertyServiceClient = propertyServiceClient;
        this.revenueService = revenueService;
    }

    private void grantProfileAccess(UUID buyerId, UUID sellerId) {
        String key = PROFILE_ACCESS_PREFIX + buyerId + ":" + sellerId;
        redis.opsForValue().set(key, "granted", java.time.Duration.ofDays(180));
    }

    @Transactional(readOnly = true)
    public boolean hasProfileAccess(UUID buyerId, UUID sellerId) {
        String key = PROFILE_ACCESS_PREFIX + buyerId + ":" + sellerId;
        return Boolean.TRUE.equals(redis.hasKey(key));
    }

    @Transactional
    public PaymentResponse initiate(UUID buyerId, InitiatePaymentRequest req, String clientIp) {
        String upperType = req.getPaymentType().toUpperCase();
        if (upperType.equals("FULL_PAYMENT") || upperType.equals("DEPOSIT")) {
            if (!propertyServiceClient.isPropertyVerified(req.getPropertyId())) {
                throw new RuntimeException("This property has not completed identity and title verification and cannot be paid for yet");
            }
        }
        if (upperType.equals("VIEWING_FEE") && req.getAmount().compareTo(viewingFeeKes) != 0) {
            throw new RuntimeException("Viewing fee must be exactly KES " + viewingFeeKes);
        }
        if (upperType.equals("PROFILE_ACCESS") && req.getAmount().compareTo(profileAccessFeeKes) != 0) {
            throw new RuntimeException("Profile access fee must be exactly KES " + profileAccessFeeKes);
        }

        String idemKey = (req.getIdempotencyKey() != null && !req.getIdempotencyKey().isBlank())
                ? req.getIdempotencyKey()
                : buyerId + ":" + req.getPropertyId() + ":" + req.getAmount();

        if (repo.existsByIdempotencyKey(idemKey)) {
            return toResponse(repo.findByIdempotencyKey(idemKey).orElseThrow());
        }

        if (!lockService.acquireLock(idemKey)) {
            try { Thread.sleep(200); } catch (InterruptedException ignored) {}
            if (repo.existsByIdempotencyKey(idemKey)) {
                return toResponse(repo.findByIdempotencyKey(idemKey).orElseThrow());
            }
        }

        try {
            Payment payment = repo.save(Payment.builder()
                    .buyerId(buyerId)
                    .sellerId(req.getSellerId())
                    .propertyId(req.getPropertyId())
                    .paymentType(PaymentType.valueOf(req.getPaymentType().toUpperCase()))
                    .amount(req.getAmount())
                    .phoneNumber(req.getPhoneNumber())
                    .initiatedByIp(clientIp)
                    .idempotencyKey(idemKey)
                    .build());

            auditService.log(payment.getId(), null, "PAYMENT_INITIATED",
                    null, PaymentStatus.PENDING.name(),
                    buyerId, "BUYER", clientIp,
                    null, req.getAmount(),
                    "STK push initiated to phone=" + req.getPhoneNumber()
                            + " type=" + req.getPaymentType()
                            + " idemKey=" + idemKey);

            MpesaClient.StkPushResult result = mpesaClient.initiateSTKPush(
                    req.getPhoneNumber(),
                    req.getAmount().toPlainString(),
                    "SMARTRE-" + req.getPropertyId().toString().substring(0, 8).toUpperCase(),
                    "SmartRE " + req.getPaymentType());

            if (result.success()) {
                payment.setStatus(PaymentStatus.STK_PUSHED);
                payment.setMpesaCheckoutRequestId(result.checkoutRequestId());
                payment.setMpesaMerchantRequestId(result.merchantRequestId());
                auditService.log(payment.getId(), null, "STK_PUSH_SENT",
                        PaymentStatus.PENDING.name(), PaymentStatus.STK_PUSHED.name(),
                        null, "SYSTEM", null,
                        null, req.getAmount(),
                        "CheckoutRequestID=" + result.checkoutRequestId()
                                + " MerchantRequestID=" + result.merchantRequestId());
            } else {
                payment.setStatus(PaymentStatus.FAILED);
                payment.setFailureReason(result.responseDescription());
                auditService.log(payment.getId(), null, "STK_PUSH_FAILED",
                        PaymentStatus.PENDING.name(), PaymentStatus.FAILED.name(),
                        null, "SYSTEM", null,
                        null, req.getAmount(),
                        "STK push rejected: " + result.responseDescription());
            }

            return toResponse(repo.save(payment));
        } finally {
            lockService.releaseLock(idemKey);
        }
    }

    @Transactional
    public void handleCallback(MpesaCallbackRequest req, String rawJson) {
        var callback = req.getBody().getStkCallback();
        String checkoutId = callback.getCheckoutRequestId();

        Payment payment = repo.findByMpesaCheckoutRequestId(checkoutId).orElse(null);

        rawCallbackRepo.save(MpesaRawCallback.builder()
                .callbackType("STK_PUSH")
                .checkoutRequestId(checkoutId)
                .resultCode(callback.getResultCode())
                .resultDesc(callback.getResultDesc())
                .rawPayload(rawJson)
                .paymentId(payment != null ? payment.getId() : null)
                .build());

        if (payment == null) {
            log.warn("M-Pesa callback: no payment for checkoutId={}", checkoutId);
            return;
        }

        if (payment.getStatus() == PaymentStatus.COMPLETED
                || payment.getStatus() == PaymentStatus.FAILED) {
            log.info("Duplicate M-Pesa callback for checkoutId={} — ignoring", checkoutId);
            auditService.log(payment.getId(), null, "DUPLICATE_CALLBACK_IGNORED",
                    payment.getStatus().name(), payment.getStatus().name(),
                    null, "MPESA_CALLBACK", null,
                    null, payment.getAmount(),
                    "Duplicate callback received and ignored. checkoutId=" + checkoutId);
            return;
        }

        String prevStatus = payment.getStatus().name();

        if (callback.getResultCode() == 0) {
            String receipt = null;
            String callbackAmount = null;
            String callbackPhone = null;
            if (callback.getCallbackMetadata() != null
                    && callback.getCallbackMetadata().getItems() != null) {
                for (var item : callback.getCallbackMetadata().getItems()) {
                    if ("MpesaReceiptNumber".equals(item.getName())) {
                        receipt = String.valueOf(item.getValue());
                    }
                    if ("Amount".equals(item.getName())) {
                        callbackAmount = String.valueOf(item.getValue());
                    }
                    if ("PhoneNumber".equals(item.getName())) {
                        callbackPhone = String.valueOf(item.getValue());
                    }
                }
            }

            boolean amountMismatch = callbackAmount != null
                    && new BigDecimal(callbackAmount).subtract(payment.getAmount()).abs()
                    .compareTo(BigDecimal.ONE) > 0;
            boolean phoneMismatch = callbackPhone != null
                    && payment.getPhoneNumber() != null
                    && !callbackPhone.replaceAll("[^0-9]", "").endsWith(
                    payment.getPhoneNumber().replaceAll("[^0-9]", "").substring(
                            Math.max(0, payment.getPhoneNumber().replaceAll("[^0-9]", "").length() - 9)));

            if (amountMismatch || phoneMismatch) {
                payment.setStatus(PaymentStatus.FAILED);
                payment.setFailureReason("Callback amount/phone did not match the original request");
                repo.save(payment);
                auditService.log(payment.getId(), null, "PAYMENT_FAILED",
                        prevStatus, PaymentStatus.FAILED.name(),
                        null, "MPESA_CALLBACK", null,
                        receipt, payment.getAmount(),
                        "Callback rejected: amountMismatch=" + amountMismatch + " phoneMismatch=" + phoneMismatch
                                + " callbackAmount=" + callbackAmount + " callbackPhone=" + callbackPhone);
                log.warn("M-Pesa callback rejected for paymentId={} due to amount/phone mismatch", payment.getId());
                return;
            }

            payment.setStatus(PaymentStatus.COMPLETED);
            if (callback.getCallbackMetadata() != null
                    && callback.getCallbackMetadata().getItems() != null) {
                for (var item : callback.getCallbackMetadata().getItems()) {
                    if ("MpesaReceiptNumber".equals(item.getName())) {
                        payment.setMpesaReceiptNumber(receipt);
                    }
                    if ("TransactionDate".equals(item.getName())) {
                        payment.setMpesaTransactionDate(String.valueOf(item.getValue()));
                    }
                }
            }
            repo.save(payment);

            auditService.log(payment.getId(), null, "PAYMENT_COMPLETED",
                    prevStatus, PaymentStatus.COMPLETED.name(),
                    null, "MPESA_CALLBACK", null,
                    receipt, payment.getAmount(),
                    "M-Pesa payment confirmed. Receipt=" + receipt
                            + " checkoutId=" + checkoutId);

            receiptService.issueReceipt(payment, BigDecimal.ZERO, payment.getAmount(),
                    payment.getPhoneNumber(), "MPESA", null);

            eventPublisher.publishPaymentCompleted(payment);
            if (payment.getPaymentType() == PaymentType.PROFILE_ACCESS) {
                grantProfileAccess(payment.getBuyerId(), payment.getSellerId());
            }
            if (payment.getPaymentType() == PaymentType.VIEWING_FEE) {
                revenueService.recordViewingFee(payment.getId(), payment.getBuyerId(),
                        payment.getSellerId(), payment.getPropertyId());
            }
            log.info("Payment COMPLETED paymentId={} receipt={}", payment.getId(), receipt);
        } else {
            payment.setStatus(PaymentStatus.FAILED);
            payment.setFailureReason(callback.getResultDesc());
            repo.save(payment);
            auditService.log(payment.getId(), null, "PAYMENT_FAILED",
                    prevStatus, PaymentStatus.FAILED.name(),
                    null, "MPESA_CALLBACK", null,
                    null, payment.getAmount(),
                    "M-Pesa payment failed. ResultCode=" + callback.getResultCode()
                            + " Reason=" + callback.getResultDesc());
            log.warn("Payment FAILED paymentId={} reason={}", payment.getId(), callback.getResultDesc());
        }
    }

    @Transactional(readOnly = true)
    public PaymentResponse getByIdAndBuyer(UUID paymentId, UUID buyerId) {
        Payment p = repo.findById(paymentId)
                .orElseThrow(() -> new RuntimeException("Payment not found"));
        if (!p.getBuyerId().equals(buyerId)) throw new RuntimeException("Access denied");
        return toResponse(p);
    }

    @Transactional(readOnly = true)
    public Page<PaymentResponse> getByBuyer(UUID buyerId, Pageable p) {
        return repo.findByBuyerId(buyerId, p).map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public List<PaymentAuditResponse> getAuditTrail(UUID paymentId, UUID requesterId) {
        Payment p = repo.findById(paymentId).orElseThrow(() -> new RuntimeException("Payment not found"));
        if (!p.getBuyerId().equals(requesterId) && !p.getSellerId().equals(requesterId)) {
            throw new RuntimeException("Access denied");
        }
        return auditService.getAuditTrail(paymentId);
    }

    @Transactional(readOnly = true)
    public PaymentReceiptResponse getReceipt(UUID paymentId, UUID requesterId) {
        Payment p = repo.findById(paymentId).orElseThrow(() -> new RuntimeException("Payment not found"));
        if (!p.getBuyerId().equals(requesterId) && !p.getSellerId().equals(requesterId)) {
            throw new RuntimeException("Access denied");
        }
        return receiptService.getByPaymentId(paymentId);
    }

    @Transactional(readOnly = true)
    public org.springframework.data.domain.Page<PaymentResponse> getPendingEscrowReview(int page, int size) {
        var pageable = org.springframework.data.domain.PageRequest.of(page, size,
                org.springframework.data.domain.Sort.by(org.springframework.data.domain.Sort.Direction.DESC, "createdAt"));
        return repo.findByStatusAndEscrowReleasedFalseAndPaymentTypeIn(
                PaymentStatus.COMPLETED,
                java.util.List.of(PaymentType.FULL_PAYMENT, PaymentType.DEPOSIT),
                pageable
        ).map(this::toResponse);
    }

    private PaymentResponse toResponse(Payment p) {
        return PaymentResponse.builder()
                .id(p.getId()).buyerId(p.getBuyerId())
                .sellerId(p.getSellerId()).propertyId(p.getPropertyId())
                .paymentType(p.getPaymentType().name())
                .status(p.getStatus().name()).currency(p.getCurrency())
                .amount(p.getAmount()).phoneNumber(p.getPhoneNumber())
                .mpesaCheckoutRequestId(p.getMpesaCheckoutRequestId())
                .mpesaReceiptNumber(p.getMpesaReceiptNumber())
                .escrowReleased(p.isEscrowReleased())
                .failureReason(p.getFailureReason())
                .createdAt(p.getCreatedAt()).updatedAt(p.getUpdatedAt())
                .build();
    }
}
