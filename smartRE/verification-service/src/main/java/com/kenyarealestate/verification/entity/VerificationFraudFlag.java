package com.kenyarealestate.verification.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "verification_fraud_flags")
@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class VerificationFraudFlag {

    @Id @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "verification_id", nullable = false)
    private UUID verificationId;

    @Column(name = "verification_track", nullable = false)
    private String verificationTrack;

    @Column(name = "fraud_type", nullable = false)
    private String fraudType;

    @Column(name = "document_category")
    private String documentCategory;

    @Column(name = "document_hash")
    private String documentHash;

    @Column(columnDefinition = "TEXT")
    private String details;

    @CreationTimestamp
    private LocalDateTime flaggedAt;
}
