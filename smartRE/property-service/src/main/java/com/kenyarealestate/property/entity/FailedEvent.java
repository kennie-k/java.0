package com.kenyarealestate.property.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * A Kafka event that exhausted all retries and landed in the dead-letter topic. Previously these
 * were only ever logged (log.error), so a poison verification event could silently stall a
 * seller's listing activation forever with no queryable record. Persisting them here lets an
 * operator (or a future admin endpoint) find and manually replay/resolve them.
 */
@Entity
@Table(name = "failed_events")
@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class FailedEvent {

    @Id @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "source_topic", nullable = false)
    private String sourceTopic;

    @Column(name = "event_type")
    private String eventType;

    @Column(columnDefinition = "TEXT")
    private String payload;

    @Column(name = "failure_reason", columnDefinition = "TEXT")
    private String failureReason;

    @Builder.Default
    @Column(nullable = false)
    private boolean resolved = false;

    @CreationTimestamp
    @Column(name = "received_at")
    private LocalDateTime receivedAt;
}
