package com.kenyarealestate.review.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReviewAdminStatsResponse {
    private long totalReviews;
    private long visibleReviews;
    private long hiddenReviews;
    private double averageRating;
    private List<RatingCount> ratingDistribution;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RatingCount {
        private int rating;
        private long count;
    }
}
