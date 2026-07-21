package com.kenyarealestate.payment.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "mpesa_raw_callbacks")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MpesaRawCallback {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "callback_type", nullable = false, length = 20)
    private String callbackType;

    @Column(name = "checkout_request_id", length = 100)
    private String checkoutRequestId;

    @Column(name = "originator_conversation_id", length = 100)
    private String originatorConversationId;

    @Column(name = "result_code")
    private Integer resultCode;

    @Column(name = "result_desc", length = 255)
    private String resultDesc;

    @Column(name = "raw_payload", nullable = false, columnDefinition = "TEXT")
    private String rawPayload;

    @Column(name = "payment_id")
    private UUID paymentId;

    @Column(name = "revenue_id")
    private UUID revenueId;

    @CreationTimestamp
    @Column(name = "received_at", nullable = false, updatable = false)
    private LocalDateTime receivedAt;
}
