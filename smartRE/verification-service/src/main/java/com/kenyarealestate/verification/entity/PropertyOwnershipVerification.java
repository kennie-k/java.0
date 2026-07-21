package com.kenyarealestate.verification.entity;

import com.kenyarealestate.verification.enums.OwnershipVerificationStatus;
import com.kenyarealestate.verification.enums.PropertyTypeEnum;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "property_ownership_verifications")
@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class PropertyOwnershipVerification {

    @Id @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "property_id", nullable = false)
    private UUID propertyId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "seller_identity_verif_id", nullable = false)
    @ToString.Exclude
    private SellerIdentityVerification sellerIdentityVerification;

    @Enumerated(EnumType.STRING)
    @Builder.Default
    private OwnershipVerificationStatus status = OwnershipVerificationStatus.DRAFT;

    @Enumerated(EnumType.STRING)
    @Column(name = "property_type", nullable = false)
    private PropertyTypeEnum propertyType;

    private String county;

    @Column(name = "parcel_number")     private String parcelNumber;
    @Column(name = "title_deed_number") private String titleDeedNumber;
    @Column(name = "lr_number")         private String lrNumber;

    @Builder.Default
    @Column(name = "ownership_score")
    private Integer ownershipScore = 0;

    @Builder.Default
    @Column(name = "ministry_lands_confirmed")
    private Boolean ministryLandsConfirmed = false;

    @Builder.Default
    @Column(name = "encumbrance_clear")
    private Boolean encumbranceClear = false;

    @Column(name = "rejection_reason", columnDefinition = "TEXT")
    private String rejectionReason;

    @Column(name = "reviewed_by")   private UUID reviewedBy;
    @Column(name = "reviewed_at")   private LocalDateTime reviewedAt;

    @Builder.Default
    @Column(name = "fraud_strike_count")
    private Integer fraudStrikeCount = 0;

    @OneToMany(mappedBy = "propertyOwnershipVerification", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    @ToString.Exclude
    private List<PropertyOwnershipDocument> documents = new ArrayList<>();

    @CreationTimestamp private LocalDateTime createdAt;
    @UpdateTimestamp   private LocalDateTime updatedAt;
}
