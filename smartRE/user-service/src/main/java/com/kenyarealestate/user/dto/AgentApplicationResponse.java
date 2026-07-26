package com.kenyarealestate.user.dto;

import com.kenyarealestate.user.entity.AgentApplicationStatus;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
public class AgentApplicationResponse {
    private UUID id;
    private UUID userId;
    private AgentApplicationStatus status;
    private String businessName;
    private String businessDocUrl;
    private String rejectionReason;
    private UUID reviewedBy;
    private LocalDateTime reviewedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
