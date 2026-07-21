package com.kenyarealestate.payment.dto;
import lombok.*; import java.math.BigDecimal; import java.time.LocalDateTime; import java.util.UUID;
@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class PaymentResponse {
    private UUID id, buyerId, sellerId, propertyId;
    private String paymentType, status, currency, phoneNumber;
    private BigDecimal amount; private String mpesaCheckoutRequestId, mpesaReceiptNumber;
    private boolean escrowReleased; private String failureReason;
    private LocalDateTime createdAt, updatedAt;
}
