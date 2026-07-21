package com.kenyarealestate.review.dto;
import lombok.*; import java.time.LocalDateTime; import java.util.UUID;
@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class ReviewResponse {
    private UUID id,reviewerId,sellerId,propertyId,paymentId;
    private Integer rating; private String comment; private boolean isVerified;
    private LocalDateTime createdAt;
}
