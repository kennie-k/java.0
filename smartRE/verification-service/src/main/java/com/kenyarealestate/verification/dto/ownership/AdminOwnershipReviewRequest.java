package com.kenyarealestate.verification.dto.ownership;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class AdminOwnershipReviewRequest {
    @NotBlank private String decision;
    private String notes;
    private Boolean ministryLandsConfirmed;
    private Boolean encumbranceClear;
}
