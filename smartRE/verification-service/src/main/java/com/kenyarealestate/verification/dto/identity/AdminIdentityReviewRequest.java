package com.kenyarealestate.verification.dto.identity;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class AdminIdentityReviewRequest {
    @NotBlank private String decision;
    private String notes;
    private String resubmissionNotes;
}
