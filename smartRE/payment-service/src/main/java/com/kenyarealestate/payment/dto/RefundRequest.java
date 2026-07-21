package com.kenyarealestate.payment.dto;
import jakarta.validation.constraints.NotBlank; import lombok.Data;
@Data public class RefundRequest {
    @NotBlank private String reason;
}
