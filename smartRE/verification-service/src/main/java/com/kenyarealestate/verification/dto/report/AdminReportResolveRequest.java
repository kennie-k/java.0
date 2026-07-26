package com.kenyarealestate.verification.dto.report;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class AdminReportResolveRequest {
    @NotBlank private String decision;
    private String notes;
}
