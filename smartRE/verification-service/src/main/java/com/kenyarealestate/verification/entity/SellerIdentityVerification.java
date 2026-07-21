package com.kenyarealestate.verification.entity;

import com.kenyarealestate.verification.enums.BadgeLevel;
import com.kenyarealestate.verification.enums.IdentityVerificationStatus;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "seller_identity_verifications")
@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class SellerIdentityVerification {

    @Id @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "user_id", unique = true, nullable = false)
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Builder.Default
    private IdentityVerificationStatus status = IdentityVerificationStatus.DRAFT;

    @Builder.Default
    @Column(name = "identity_score")
    private Integer identityScore = 0;

    @Enumerated(EnumType.STRING)
    @Builder.Default
    @Column(name = "badge_level")
    private BadgeLevel badgeLevel = BadgeLevel.NONE;

    @Column(name = "rejection_reason", columnDefinition = "TEXT")
    private String rejectionReason;

    @Column(name = "resubmission_notes", columnDefinition = "TEXT")
    private String resubmissionNotes;

    @Column(name = "kyc_reference")
    private String kycReference;

    @Column(name = "reviewed_by")
    private UUID reviewedBy;

    @Column(name = "reviewed_at")
    private LocalDateTime reviewedAt;

    @Column(name = "expires_at")
    private LocalDateTime expiresAt;

    @Builder.Default
    @Column(name = "fraud_strike_count")
    private Integer fraudStrikeCount = 0;

    @Builder.Default
    @Column(name = "permanently_banned")
    private boolean permanentlyBanned = false;

    @Column(name = "ban_reason", columnDefinition = "TEXT")
    private String banReason;

    @OneToMany(mappedBy = "sellerIdentityVerification", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    @ToString.Exclude
    private List<SellerIdentityDocument> documents = new ArrayList<>();

    @CreationTimestamp private LocalDateTime createdAt;
    @UpdateTimestamp   private LocalDateTime updatedAt;
}
