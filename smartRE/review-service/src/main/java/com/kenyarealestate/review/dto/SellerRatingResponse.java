package com.kenyarealestate.review.dto;
import lombok.*; import java.util.UUID;
@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class SellerRatingResponse {
    private UUID sellerId; private Double averageRating; private long reviewCount;
}
