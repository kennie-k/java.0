package com.kenyarealestate.payment.dto;
import jakarta.validation.constraints.*; import lombok.Data;
import java.math.BigDecimal; import java.util.UUID;
@Data public class InitiatePaymentRequest {
    @NotNull private UUID propertyId; @NotNull private UUID sellerId;
    @NotNull private BigDecimal amount; @NotBlank private String phoneNumber;
    @NotBlank private String paymentType; private String idempotencyKey;
}
