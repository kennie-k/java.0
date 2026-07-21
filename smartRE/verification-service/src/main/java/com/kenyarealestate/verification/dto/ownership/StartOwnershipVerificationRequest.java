package com.kenyarealestate.verification.dto.ownership;

import com.kenyarealestate.verification.enums.PropertyTypeEnum;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import java.util.UUID;

@Data
public class StartOwnershipVerificationRequest {
    @NotNull  private UUID propertyId;
    @NotNull  private PropertyTypeEnum propertyType;
    @NotBlank private String county;
    private String parcelNumber;
    private String titleDeedNumber;
    private String lrNumber;
}
