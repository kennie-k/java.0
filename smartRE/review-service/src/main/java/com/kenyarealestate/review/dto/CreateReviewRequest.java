package com.kenyarealestate.review.dto;

import jakarta.validation.constraints.*;
import lombok.Data;
import java.util.UUID;

@Data
public class CreateReviewRequest {
    @NotNull private UUID sellerId;
    @NotNull private UUID propertyId;
    @NotNull private UUID paymentId;
    @NotNull @Min(1) @Max(5) private Integer rating;
    private String comment;
}
