package com.kenyarealestate.verification.kafka;

import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
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

        ProducerRecord<String, Object> record = new ProducerRecord<>(topic, sellerId.toString(), event);
        attachTraceHeader(record);

        kafkaTemplate.send(record)
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

        ProducerRecord<String, Object> record = new ProducerRecord<>(topic, propertyId.toString(), event);
        attachTraceHeader(record);

        kafkaTemplate.send(record)
                .whenComplete((result, ex) -> {
                    if (ex != null) log.error("Failed to publish OWNERSHIP_APPROVED event: {}", ex.getMessage());
                    else log.info("Published OWNERSHIP_APPROVED for property {}", propertyId);
                });
    }

    private void attachTraceHeader(ProducerRecord<String, Object> record) {
        String correlationId = MDC.get("traceId");
        if (!StringUtils.hasText(correlationId)) correlationId = UUID.randomUUID().toString();
        record.headers().add("X-Correlation-Id", correlationId.getBytes(StandardCharsets.UTF_8));
    }
}
