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
public class PaymentReceiptResponse {
    private UUID id;
    private String receiptNumber;
    private UUID paymentId;
    private UUID buyerId;
    private UUID sellerId;
    private UUID propertyId;
    private String paymentType;
    private BigDecimal grossAmount;
    private BigDecimal platformFee;
    private BigDecimal sellerPayout;
    private String currency;
    private String mpesaReceipt;
    private String payerPhone;
    private String payerName;
    private String payeeIdentifier;
    private String payeeType;
    private String status;
    private LocalDateTime issuedAt;
}
