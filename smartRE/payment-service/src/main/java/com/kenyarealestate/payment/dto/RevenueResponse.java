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
public class RevenueResponse {
    private UUID id;
    private UUID paymentId;
    private UUID buyerId;
    private UUID sellerId;
    private UUID propertyId;
    private String revenueType;
    private BigDecimal grossAmount;
    private BigDecimal platformFee;
    private BigDecimal sellerPayout;
    private BigDecimal feePercentage;
    private String currency;
    private String status;
    private String sellerPayoutReceipt;
    private LocalDateTime sellerPayoutAt;
    private String payoutFailureReason;
    private LocalDateTime createdAt;
}
