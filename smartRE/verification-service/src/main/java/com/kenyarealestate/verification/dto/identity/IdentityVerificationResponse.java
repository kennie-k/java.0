package com.kenyarealestate.verification.dto.identity;

import com.kenyarealestate.verification.enums.BadgeLevel;
import com.kenyarealestate.verification.enums.IdentityVerificationStatus;
import lombok.*;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class IdentityVerificationResponse {
    private UUID id;
    private UUID userId;
    private IdentityVerificationStatus status;
    private Integer identityScore;
    private BadgeLevel badgeLevel;
    private String rejectionReason;
    private String resubmissionNotes;
    private LocalDateTime expiresAt;
    private boolean expired;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private List<IdentityDocumentResponse> documents;
    private List<String> missingRequiredDocuments;
    private Integer fraudStrikeCount;
    private boolean permanentlyBanned;
}
