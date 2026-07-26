package com.kenyarealestate.verification.kafka;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.UUID;

@Slf4j
@Component
public class VerificationEventPublisher {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    @Value("${kafka.topics.verification-events:verification-events}")
    private String topic;

    public VerificationEventPublisher(KafkaTemplate<String, Object> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    public void publishIdentityApproved(UUID sellerId, UUID verificationId,
                                         LocalDateTime approvedAt, LocalDateTime expiresAt) {
        var event = Events.VerificationApprovedEvent.builder()
                .eventType("IDENTITY_APPROVED")
                .sellerId(sellerId)
                .verificationId(verificationId)
                .verificationType("IDENTITY")
                .approvedAt(approvedAt)
                .expiresAt(expiresAt)
                .build();
        kafkaTemplate.send(topic, sellerId.toString(), event)
                .whenComplete((result, ex) -> {
                    if (ex != null) log.error("Failed to publish IDENTITY_APPROVED event: {}", ex.getMessage());
                    else log.info("Published IDENTITY_APPROVED for seller {}", sellerId);
                });
    }

    public void publishOwnershipApproved(UUID sellerId, UUID verificationId,
                                          UUID propertyId, LocalDateTime approvedAt,
                                          String parcelNumber, String titleDeedNumber) {
        var event = Events.VerificationApprovedEvent.builder()
                .eventType("OWNERSHIP_APPROVED")
                .sellerId(sellerId)
                .verificationId(verificationId)
                .verificationType("OWNERSHIP")
                .propertyId(propertyId)
                .approvedAt(approvedAt)
                .parcelNumber(parcelNumber)
                .titleDeedNumber(titleDeedNumber)
                .build();
        kafkaTemplate.send(topic, propertyId.toString(), event)
                .whenComplete((result, ex) -> {
                    if (ex != null) log.error("Failed to publish OWNERSHIP_APPROVED event: {}", ex.getMessage());
                    else log.info("Published OWNERSHIP_APPROVED for property {}", propertyId);
                });
    }
}
