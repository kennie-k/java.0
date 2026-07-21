package com.kenyarealestate.payment.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "company_revenue")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CompanyRevenue {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "payment_id", nullable = false, unique = true)
    private UUID paymentId;

    @Column(name = "buyer_id", nullable = false)
    private UUID buyerId;

    @Column(name = "seller_id", nullable = false)
    private UUID sellerId;

    @Column(name = "property_id", nullable = false)
    private UUID propertyId;

    @Enumerated(EnumType.STRING)
    @Column(name = "revenue_type", nullable = false)
    private RevenueType revenueType;

    @Column(name = "gross_amount", nullable = false, precision = 15, scale = 2)
    private BigDecimal grossAmount;

    @Column(name = "platform_fee", nullable = false, precision = 15, scale = 2)
    private BigDecimal platformFee;

    @Column(name = "seller_payout", nullable = false, precision = 15, scale = 2)
    private BigDecimal sellerPayout;

    @Column(name = "fee_percentage", nullable = false, precision = 5, scale = 2)
    private BigDecimal feePercentage;

    @Builder.Default
    private String currency = "KES";

    @Enumerated(EnumType.STRING)
    @Builder.Default
    private RevenueStatus status = RevenueStatus.PENDING;

    @Column(name = "seller_payout_receipt")
    private String sellerPayoutReceipt;

    @Column(name = "seller_payout_at")
    private LocalDateTime sellerPayoutAt;

    @Column(name = "b2c_conversation_id")
    private String b2cConversationId;

    @Column(name = "b2c_originator_conversation_id")
    private String b2cOriginatorConversationId;

    @Column(name = "payout_failure_reason", columnDefinition = "TEXT")
    private String payoutFailureReason;

    @Column(name = "released_by_admin_id")
    private UUID releasedByAdminId;

    @Column(name = "release_notes", columnDefinition = "TEXT")
    private String releaseNotes;

    @Column(name = "payout_method", length = 20)
    @Builder.Default
    private String payoutMethod = "MPESA_B2C";

    @Column(name = "payout_identifier", length = 100)
    private String payoutIdentifier;

    @Column(name = "payout_account_name", length = 255)
    private String payoutAccountName;

    @CreationTimestamp
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;
}
