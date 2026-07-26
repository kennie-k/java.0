package com.kenyarealestate.user.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class SubmitAgentApplicationRequest {
    private String businessName;
    @NotBlank private String businessDocUrl;
}
