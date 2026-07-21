package com.kenyarealestate.verification.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "verification_audit_log")
@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class VerificationAuditLog {

    @Id @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "verification_id",    nullable = false) private UUID verificationId;
    @Column(name = "verification_track", nullable = false) private String verificationTrack;
    @Column(nullable = false) private String action;
    @Column(name = "performed_by")      private UUID performedBy;
    @Column(name = "performed_by_role") private String performedByRole;
    @Column(name = "from_status")       private String fromStatus;
    @Column(name = "to_status")         private String toStatus;
    @Column(columnDefinition = "TEXT")  private String notes;

    @CreationTimestamp private LocalDateTime createdAt;
}
