package com.kenyarealestate.verification.dto.ownership;

import com.kenyarealestate.verification.enums.OwnershipVerificationStatus;
import com.kenyarealestate.verification.enums.PropertyTypeEnum;
import lombok.*;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class OwnershipVerificationResponse {
    private UUID id;
    private UUID propertyId;
    private UUID sellerIdentityVerificationId;
    private OwnershipVerificationStatus status;
    private PropertyTypeEnum propertyType;
    private String county;
    private String parcelNumber;
    private String titleDeedNumber;
    private String lrNumber;
    private Integer ownershipScore;
    private Boolean ministryLandsConfirmed;
    private Boolean encumbranceClear;
    private String rejectionReason;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private List<OwnershipDocumentResponse> documents;
    private List<DocumentRequirementResponse> missingDocuments;
    private List<DocumentRequirementResponse> allRequiredDocuments;
}
