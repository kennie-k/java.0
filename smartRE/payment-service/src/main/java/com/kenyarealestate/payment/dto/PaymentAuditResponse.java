package com.kenyarealestate.payment.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentAuditResponse {
    private UUID id;
    private UUID paymentId;
    private UUID revenueId;
    private String eventType;
    private String previousStatus;
    private String newStatus;
    private UUID actorId;
    private String actorRole;
    private String actorIp;
    private String mpesaReceipt;
    private BigDecimal amountKes;
    private String detail;
    private LocalDateTime createdAt;
}
