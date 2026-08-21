package com.kenyarealestate.payment.service;

import com.kenyarealestate.payment.dto.PaymentReceiptResponse;
import com.kenyarealestate.payment.entity.Payment;
import com.kenyarealestate.payment.entity.PaymentReceipt;
import com.kenyarealestate.payment.repository.PaymentReceiptRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

@Slf4j
@Service
public class ReceiptService {

    private final PaymentReceiptRepository repo;

    public ReceiptService(PaymentReceiptRepository repo) {
        this.repo = repo;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public PaymentReceipt issueReceipt(Payment payment, BigDecimal platformFee,
                                        BigDecimal sellerPayout, String payeeIdentifier,
                                        String payeeType, String payerName) {
        if (repo.existsByPaymentId(payment.getId())) {
            return repo.findByPaymentId(payment.getId()).orElseThrow();
        }
        String receiptNumber = generateReceiptNumber(payment);
        try {
            return repo.save(PaymentReceipt.builder()
                    .receiptNumber(receiptNumber)
                    .paymentId(payment.getId())
                    .buyerId(payment.getBuyerId())
                    .sellerId(payment.getSellerId())
                    .propertyId(payment.getPropertyId())
                    .paymentType(payment.getPaymentType().name())
                    .grossAmount(payment.getAmount())
                    .platformFee(platformFee != null ? platformFee : BigDecimal.ZERO)
                    .sellerPayout(sellerPayout != null ? sellerPayout : payment.getAmount())
                    .currency(payment.getCurrency())
                    .mpesaReceipt(payment.getMpesaReceiptNumber())
                    .payerPhone(payment.getPhoneNumber())
                    .payerName(payerName)
                    .payeeIdentifier(payeeIdentifier)
                    .payeeType(payeeType != null ? payeeType : "MPESA")
                    .build());
        } catch (Exception e) {
            log.error("RECEIPT ISSUANCE FAILURE paymentId={}: {}", payment.getId(), e.getMessage());
            throw e;
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void voidReceiptIfExists(UUID paymentId, String reason) {
        repo.findByPaymentId(paymentId).ifPresent(r -> {
            r.setStatus("VOIDED");
            r.setVoidReason(reason);
            r.setVoidedAt(LocalDateTime.now());
            repo.save(r);
        });
    }

    @Transactional(readOnly = true)
    public PaymentReceiptResponse getByPaymentId(UUID paymentId) {
        return toResponse(repo.findByPaymentId(paymentId)
                .orElseThrow(() -> new RuntimeException("Receipt not found for payment " + paymentId)));
    }

    @Transactional(readOnly = true)
    public PaymentReceiptResponse getByReceiptNumber(String receiptNumber) {
        return toResponse(repo.findByReceiptNumber(receiptNumber)
                .orElseThrow(() -> new RuntimeException("Receipt not found: " + receiptNumber)));
    }

    private String generateReceiptNumber(Payment payment) {
        String date = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        String shortId = payment.getId().toString().substring(0, 8).toUpperCase();
        return "SMR-" + date + "-" + shortId;
    }

    private PaymentReceiptResponse toResponse(PaymentReceipt r) {
        return PaymentReceiptResponse.builder()
                .id(r.getId())
                .receiptNumber(r.getReceiptNumber())
                .paymentId(r.getPaymentId())
                .buyerId(r.getBuyerId())
                .sellerId(r.getSellerId())
                .propertyId(r.getPropertyId())
                .paymentType(r.getPaymentType())
                .grossAmount(r.getGrossAmount())
                .platformFee(r.getPlatformFee())
                .sellerPayout(r.getSellerPayout())
                .currency(r.getCurrency())
                .mpesaReceipt(r.getMpesaReceipt())
                .payerPhone(r.getPayerPhone())
                .payerName(r.getPayerName())
                .payeeIdentifier(r.getPayeeIdentifier())
                .payeeType(r.getPayeeType())
                .status(r.getStatus())
                .issuedAt(r.getIssuedAt())
                .build();
    }
}
