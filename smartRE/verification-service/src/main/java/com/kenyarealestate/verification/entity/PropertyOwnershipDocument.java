package com.kenyarealestate.verification.entity;

import com.kenyarealestate.verification.enums.OwnershipDocumentCategory;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "property_ownership_documents")
@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class PropertyOwnershipDocument {

    @Id @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "property_ownership_verif_id", nullable = false)
    @ToString.Exclude
    private PropertyOwnershipVerification propertyOwnershipVerification;

    @Enumerated(EnumType.STRING)
    @Column(name = "document_category", nullable = false)
    private OwnershipDocumentCategory documentCategory;

    @Column(name = "document_url", nullable = false, columnDefinition = "TEXT")
    private String documentUrl;

    @Column(name = "file_hash_sha256") private String fileHashSha256;
    @Column(name = "file_size_bytes")  private Long fileSizeBytes;
    @Column(name = "mime_type")        private String mimeType;

    @Builder.Default @Column(name = "is_required") private Boolean isRequired = true;

    @Column(name = "lc_advocate_stamp_present")      private Boolean lcAdvocateStampPresent;
    @Column(name = "lc_advocate_signature_present")  private Boolean lcAdvocateSignaturePresent;
    @Column(name = "lc_commissioner_oaths_present")  private Boolean lcCommissionerOathsPresent;
    @Column(name = "lc_official_seal_present")       private Boolean lcOfficialSealPresent;
    @Column(name = "lc_owner_signature_present")     private Boolean lcOwnerSignaturePresent;
    @Column(name = "lc_witness_signatures_present")  private Boolean lcWitnessSignaturesPresent;
    @Column(name = "lc_date_present")                private Boolean lcDatePresent;
    @Column(name = "lc_parcel_number_matches")       private Boolean lcParcelNumberMatches;
    @Column(name = "lc_original_document_confirmed") private Boolean lcOriginalDocumentConfirmed;

    @Column(name = "ai_authenticity_score")  private Integer aiAuthenticityScore;
    @Builder.Default @Column(name = "ai_tamper_detected")    private Boolean aiTamperDetected = false;
    @Builder.Default @Column(name = "ai_alteration_detected") private Boolean aiAlterationDetected = false;
    @Column(name = "ai_font_consistency")    private Boolean aiFontConsistency;
    @Column(name = "ai_date_sequence_valid") private Boolean aiDateSequenceValid;
    @Column(name = "ai_metadata_clean")      private Boolean aiMetadataClean;
    @Column(name = "ai_screening_notes", columnDefinition = "TEXT") private String aiScreeningNotes;
    @Column(name = "ai_screened_at")         private LocalDateTime aiScreenedAt;

    @Column(name = "human_legal_approved")   private Boolean humanLegalApproved;
    @Column(name = "human_reviewer_id")      private UUID humanReviewerId;
    @Column(name = "human_review_notes", columnDefinition = "TEXT") private String humanReviewNotes;
    @Column(name = "human_reviewed_at")      private LocalDateTime humanReviewedAt;

    @CreationTimestamp private LocalDateTime uploadedAt;
}
