package com.kenyarealestate.verification.dto.report;

import com.kenyarealestate.verification.enums.ReportReason;
import com.kenyarealestate.verification.enums.ReportStatus;
import com.kenyarealestate.verification.enums.ReportTargetType;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
public class ReportResponse {
    private UUID id;
    private UUID reporterId;
    private ReportTargetType targetType;
    private UUID targetId;
    private ReportReason reason;
    private String details;
    private ReportStatus status;
    private String adminNotes;
    private UUID resolvedBy;
    private LocalDateTime resolvedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
