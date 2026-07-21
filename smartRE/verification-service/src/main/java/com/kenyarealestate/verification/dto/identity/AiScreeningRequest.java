package com.kenyarealestate.verification.dto.identity;

import lombok.Data;
import java.util.UUID;

@Data
public class AiScreeningRequest {
    private UUID documentId;
    private Integer aiAuthenticityScore;
    private Boolean aiTamperDetected;
    private Boolean aiMetadataClean;
    private Boolean aiFontConsistency;
    private Boolean aiSignatureDetected;
    private Boolean aiSealDetected;
    private String aiScreeningNotes;
}
