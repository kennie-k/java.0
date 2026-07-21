package com.kenyarealestate.payment.service;

import com.kenyarealestate.payment.dto.PaymentAuditResponse;
import com.kenyarealestate.payment.entity.PaymentAuditLog;
import com.kenyarealestate.payment.repository.PaymentAuditLogRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
public class PaymentAuditService {

    private final PaymentAuditLogRepository repo;

    public PaymentAuditService(PaymentAuditLogRepository repo) {
        this.repo = repo;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void log(UUID paymentId, UUID revenueId, String eventType,
                    String previousStatus, String newStatus,
                    UUID actorId, String actorRole, String actorIp,
                    String mpesaReceipt, BigDecimal amountKes, String detail) {
        try {
            repo.save(PaymentAuditLog.builder()
                    .paymentId(paymentId)
                    .revenueId(revenueId)
                    .eventType(eventType)
                    .previousStatus(previousStatus)
                    .newStatus(newStatus)
                    .actorId(actorId)
                    .actorRole(actorRole)
                    .actorIp(actorIp)
                    .mpesaReceipt(mpesaReceipt)
                    .amountKes(amountKes)
                    .detail(detail)
                    .build());
        } catch (Exception e) {
            log.error("CRITICAL: AUDIT LOG FAILURE paymentId={} event={}: {}", paymentId, eventType, e.getMessage());
        }
    }

    @Transactional(readOnly = true)
    public List<PaymentAuditResponse> getAuditTrail(UUID paymentId) {
        return repo.findByPaymentIdOrderByCreatedAtAsc(paymentId)
                .stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<PaymentAuditResponse> getRevenueAuditTrail(UUID revenueId) {
        return repo.findByRevenueIdOrderByCreatedAtAsc(revenueId)
                .stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    private PaymentAuditResponse toResponse(PaymentAuditLog l) {
        return PaymentAuditResponse.builder()
                .id(l.getId())
                .paymentId(l.getPaymentId())
                .revenueId(l.getRevenueId())
                .eventType(l.getEventType())
                .previousStatus(l.getPreviousStatus())
                .newStatus(l.getNewStatus())
                .actorId(l.getActorId())
                .actorRole(l.getActorRole())
                .actorIp(l.getActorIp())
                .mpesaReceipt(l.getMpesaReceipt())
                .amountKes(l.getAmountKes())
                .detail(l.getDetail())
                .createdAt(l.getCreatedAt())
                .build();
    }
}
