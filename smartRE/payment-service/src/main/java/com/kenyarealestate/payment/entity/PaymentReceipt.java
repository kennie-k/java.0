package com.kenyarealestate.payment.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "payment_receipts")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentReceipt {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "receipt_number", nullable = false, unique = true, length = 50)
    private String receiptNumber;

    @Column(name = "payment_id", nullable = false, unique = true)
    private UUID paymentId;

    @Column(name = "buyer_id", nullable = false)
    private UUID buyerId;

    @Column(name = "seller_id", nullable = false)
    private UUID sellerId;

    @Column(name = "property_id", nullable = false)
    private UUID propertyId;

    @Column(name = "payment_type", nullable = false, length = 30)
    private String paymentType;

    @Column(name = "gross_amount", nullable = false, precision = 15, scale = 2)
    private BigDecimal grossAmount;

    @Column(name = "platform_fee", nullable = false, precision = 15, scale = 2)
    private BigDecimal platformFee;

    @Column(name = "seller_payout", nullable = false, precision = 15, scale = 2)
    private BigDecimal sellerPayout;

    @Builder.Default
    private String currency = "KES";

    @Column(name = "mpesa_receipt", length = 50)
    private String mpesaReceipt;

    @Column(name = "payer_phone", length = 20)
    private String payerPhone;

    @Column(name = "payer_name", length = 255)
    private String payerName;

    @Column(name = "payee_identifier", length = 100)
    private String payeeIdentifier;

    @Column(name = "payee_type", length = 20)
    @Builder.Default
    private String payeeType = "MPESA";

    @Builder.Default
    private String status = "ISSUED";

    @Column(name = "void_reason")
    private String voidReason;

    @Column(name = "voided_at")
    private LocalDateTime voidedAt;

    @CreationTimestamp
    @Column(name = "issued_at", nullable = false, updatable = false)
    private LocalDateTime issuedAt;
}
