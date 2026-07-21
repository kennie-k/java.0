package com.kenyarealestate.verification.dto;

import com.kenyarealestate.verification.enums.BadgeLevel;
import com.kenyarealestate.verification.enums.IdentityVerificationStatus;
import com.kenyarealestate.verification.enums.OwnershipVerificationStatus;
import lombok.*;
import java.time.LocalDateTime;
import java.util.UUID;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class TrustStatusResponse {
    private UUID userId;
    private boolean identityVerified;
    private IdentityVerificationStatus identityStatus;
    private BadgeLevel badgeLevel;
    private Integer identityScore;
    private boolean identityExpired;
    private LocalDateTime identityExpiresAt;
    private UUID propertyId;
    private boolean ownershipVerified;
    private OwnershipVerificationStatus ownershipStatus;
    private Integer ownershipScore;
    private Boolean ministryLandsConfirmed;
    private Boolean encumbranceClear;
    private boolean fullyTrusted;
    private String message;
}
