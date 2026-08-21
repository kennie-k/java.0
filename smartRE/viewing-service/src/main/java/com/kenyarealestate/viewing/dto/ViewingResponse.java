package com.kenyarealestate.viewing.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ViewingResponse {
    private UUID id;
    private UUID propertyId;
    private UUID buyerId;
    private UUID sellerId;
    private LocalDateTime scheduledAt;
    private LocalDateTime completedAt;
    private String status;
    private String notes;
    private boolean buyerConfirmed;
    private boolean sellerConfirmed;
    private UUID viewingFeePaymentId;
    private String viewingFeeStatus;
    private UUID cancelledBy;
    private String cancellationReason;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
