package com.kenyarealestate.viewing.kafka;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

public class Events {

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class VerificationApprovedEvent {
        private String eventType;
        private UUID sellerId;
        private UUID verificationId;
        private String verificationType;
        private UUID propertyId;
        private LocalDateTime approvedAt;
        private LocalDateTime expiresAt;
        private String parcelNumber;
        private String titleDeedNumber;
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class PaymentCompletedEvent {
        private String eventType;
        private UUID paymentId;
        private UUID buyerId;
        private UUID sellerId;
        private UUID propertyId;
        private BigDecimal amount;
        private String currency;
        private String mpesaReceiptNumber;
        private String paymentType;
        private LocalDateTime completedAt;
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class PropertyStatusChangedEvent {
        private String eventType;
        private UUID propertyId;
        private UUID sellerId;
        private String oldStatus;
        private String newStatus;
        private LocalDateTime changedAt;
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class ViewingCompletedEvent {
        private String eventType;
        private UUID viewingId;
        private UUID propertyId;
        private UUID buyerId;
        private UUID sellerId;
        private LocalDateTime completedAt;
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class ReviewPostedEvent {
        private String eventType;
        private UUID reviewId;
        private UUID sellerId;
        private UUID propertyId;
        private UUID reviewerId;
        private Integer rating;
        private LocalDateTime postedAt;
    }
}
