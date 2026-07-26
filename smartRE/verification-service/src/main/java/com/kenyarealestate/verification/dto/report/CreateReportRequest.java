package com.kenyarealestate.verification.dto.report;

import com.kenyarealestate.verification.enums.ReportReason;
import com.kenyarealestate.verification.enums.ReportTargetType;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.UUID;

@Data
public class CreateReportRequest {
    @NotNull private ReportTargetType targetType;
    @NotNull private UUID targetId;
    @NotNull private ReportReason reason;
    private String details;
}
