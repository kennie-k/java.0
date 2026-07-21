package com.kenyarealestate.viewing.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
public class ScheduleViewingRequest {
    @NotNull  private UUID propertyId;
    @NotNull  private UUID sellerId;
    @NotNull  private LocalDateTime scheduledAt;
    @NotBlank private String buyerPhone;
    private String notes;
}
