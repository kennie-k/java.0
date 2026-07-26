package com.kenyarealestate.user.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class AdminAgentApplicationReviewRequest {
    @NotBlank private String decision;
    private String notes;
}
