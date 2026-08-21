package com.kenyarealestate.payment.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Transactional outbox row for a Kafka event derived from a payment state change.
 * Written in the SAME database transaction as the payment status update, so a Kafka
 * broker outage or transient publish failure can never silently lose the event —
 * {@code PaymentOutboxSweeper} retries anything still unpublished.
 */
@Entity
@Table(name = "payment_outbox_events")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentOutboxEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "payment_id", nullable = false)
    private UUID paymentId;

    @Column(name = "event_type", nullable = false)
    private String eventType;

    @Column(name = "topic", nullable = false)
    private String topic;

    @Column(name = "payload", nullable = false, columnDefinition = "TEXT")
    private String payload;

    @Builder.Default
    @Column(name = "published", nullable = false)
    private boolean published = false;

    @Builder.Default
    @Column(name = "attempts", nullable = false)
    private int attempts = 0;

    @Column(name = "last_error", columnDefinition = "TEXT")
    private String lastError;

    @CreationTimestamp
    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "published_at")
    private LocalDateTime publishedAt;
}
