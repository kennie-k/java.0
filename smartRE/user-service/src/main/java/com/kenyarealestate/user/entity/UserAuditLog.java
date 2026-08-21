package com.kenyarealestate.user.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

/** Audit trail for sensitive admin actions on user accounts (role promotion, ban, unban). */
@Entity
@Table(name = "user_audit_log")
@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class UserAuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "target_user_id", nullable = false)
    private UUID targetUserId;

    @Column(nullable = false, length = 60)
    private String action;

    @Column(name = "performed_by")
    private UUID performedBy;

    @Column(name = "performed_by_role", length = 30)
    private String performedByRole;

    @Column(name = "from_value", length = 60)
    private String fromValue;

    @Column(name = "to_value", length = 60)
    private String toValue;

    @Column(columnDefinition = "TEXT")
    private String notes;

    @CreationTimestamp
    @Column(name = "created_at")
    private LocalDateTime createdAt;
}
