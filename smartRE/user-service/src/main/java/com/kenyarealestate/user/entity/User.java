package com.kenyarealestate.user.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "users")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(unique = true, nullable = false)
    private String email;

    @Column(nullable = false)
    private String password;

    @Column(name = "full_name", nullable = false)
    private String fullName;

    @Column(unique = true)
    private String phone;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Role role;

    @Builder.Default
    @Column(name = "account_type", nullable = false, length = 20)
    private String accountType = "INDIVIDUAL";

    @Column(name = "company_name")
    private String companyName;

    @Column(name = "company_reg_number")
    private String companyRegNumber;

    @Column(name = "kra_pin")
    private String kraPin;

    @Column(name = "paybill_number", length = 20)
    private String paybillNumber;

    @Column(name = "till_number", length = 20)
    private String tillNumber;

    @Column(name = "bank_account_name")
    private String bankAccountName;

    @Column(name = "bank_account_number")
    private String bankAccountNumber;

    @Column(name = "bank_name")
    private String bankName;

    @Column(name = "bank_branch")
    private String bankBranch;

    @Column(name = "bank_swift_code")
    private String bankSwiftCode;

    @Builder.Default
    @Column(name = "preferred_payout_method", nullable = false, length = 20)
    private String preferredPayoutMethod = "MPESA";

    @Column(name = "payout_phone")
    private String payoutPhone;

    @Builder.Default
    @Column(name = "is_verified")
    private boolean verified = false;

    @Builder.Default
    @Column(name = "is_active")
    private boolean active = true;

    @Column(name = "profile_image")
    private String profileImage;

    @Column(name = "last_login_at")
    private LocalDateTime lastLoginAt;

    @Column(name = "last_login_ip")
    private String lastLoginIp;

    @CreationTimestamp
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;
}
