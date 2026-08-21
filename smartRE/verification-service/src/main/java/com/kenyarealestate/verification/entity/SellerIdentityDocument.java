package com.kenyarealestate.verification.entity;

import com.kenyarealestate.verification.enums.IdentityDocumentCategory;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "seller_identity_documents")
@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class SellerIdentityDocument {

    @Id @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "seller_identity_verification_id", nullable = false)
    @ToString.Exclude
    private SellerIdentityVerification sellerIdentityVerification;

    @Enumerated(EnumType.STRING)
    @Column(name = "document_category", nullable = false)
    private IdentityDocumentCategory documentCategory;

    @Column(name = "document_url", nullable = false, columnDefinition = "TEXT")
    private String documentUrl;

    @Column(name = "file_hash_sha256") private String fileHashSha256;
    @Column(name = "file_size_bytes")  private Long fileSizeBytes;
    @Column(name = "mime_type")        private String mimeType;

    @Builder.Default
    @Column(name = "is_required")
    private Boolean isRequired = true;

    @Column(name = "ai_authenticity_score") private Integer aiAuthenticityScore;
    @Builder.Default @Column(name = "ai_tamper_detected") private Boolean aiTamperDetected = false;
    @Column(name = "ai_metadata_clean")     private Boolean aiMetadataClean;
    @Column(name = "ai_font_consistency")   private Boolean aiFontConsistency;
    @Column(name = "ai_signature_detected") private Boolean aiSignatureDetected;
    @Column(name = "ai_seal_detected")      private Boolean aiSealDetected;
    @Column(name = "ai_screening_notes", columnDefinition = "TEXT") private String aiScreeningNotes;
    @Column(name = "ai_screened_at")        private LocalDateTime aiScreenedAt;

    // Populated only for categories in ID_NUMBER_EXTRACTABLE (see SellerIdentityVerificationService) —
    // stays null for document types that don't carry a printed ID number (selfies, utility bills, etc.),
    // and also stays null if the OCR model returned UNREADABLE or an implausible result.
    @Column(name = "extracted_id_number") private String extractedIdNumber;

    @Column(name = "human_verified")        private Boolean humanVerified;
    @Column(name = "human_reviewer_id")     private UUID humanReviewerId;
    @Column(name = "human_review_notes", columnDefinition = "TEXT") private String humanReviewNotes;
    @Column(name = "human_reviewed_at")     private LocalDateTime humanReviewedAt;

    @CreationTimestamp private LocalDateTime uploadedAt;
}