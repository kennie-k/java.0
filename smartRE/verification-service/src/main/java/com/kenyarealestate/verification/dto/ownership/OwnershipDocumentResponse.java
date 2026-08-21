package com.kenyarealestate.verification.dto.ownership;

import com.kenyarealestate.verification.enums.OwnershipDocumentCategory;
import lombok.*;
import java.time.LocalDateTime;
import java.util.UUID;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class OwnershipDocumentResponse {
    private UUID id;
    private OwnershipDocumentCategory documentCategory;
    private String documentUrl;
    private Boolean isRequired;
    private LocalDateTime humanReviewedAt;
    private Boolean lcAdvocateStampPresent;
    private Boolean lcAdvocateSignaturePresent;
    private Boolean lcCommissionerOathsPresent;
    private Boolean lcOfficialSealPresent;
    private Boolean lcOwnerSignaturePresent;
    private Boolean lcWitnessSignaturesPresent;
    private Boolean lcDatePresent;
    private Boolean lcParcelNumberMatches;
    private Boolean lcOriginalDocumentConfirmed;
    private Integer aiAuthenticityScore;
    private Boolean aiTamperDetected;
    private Boolean aiAlterationDetected;
    private Boolean aiFontConsistency;
    private Boolean aiDateSequenceValid;
    private String aiScreeningNotes;
    private Boolean humanLegalApproved;
    private String humanReviewNotes;
    private LocalDateTime uploadedAt;
}
