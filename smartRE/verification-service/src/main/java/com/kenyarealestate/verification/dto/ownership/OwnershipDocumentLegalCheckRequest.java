package com.kenyarealestate.verification.dto.ownership;

import lombok.Data;
import java.util.UUID;

@Data
public class OwnershipDocumentLegalCheckRequest {
    private UUID documentId;
    private Integer aiAuthenticityScore;
    private Boolean aiTamperDetected;
    private Boolean aiAlterationDetected;
    private Boolean aiFontConsistency;
    private Boolean aiDateSequenceValid;
    private Boolean aiMetadataClean;
    private String aiScreeningNotes;
    private Boolean lcAdvocateStampPresent;
    private Boolean lcAdvocateSignaturePresent;
    private Boolean lcCommissionerOathsPresent;
    private Boolean lcOfficialSealPresent;
    private Boolean lcOwnerSignaturePresent;
    private Boolean lcWitnessSignaturesPresent;
    private Boolean lcDatePresent;
    private Boolean lcParcelNumberMatches;
    private Boolean lcOriginalDocumentConfirmed;
    private Boolean humanLegalApproved;
    private String humanReviewNotes;
}
