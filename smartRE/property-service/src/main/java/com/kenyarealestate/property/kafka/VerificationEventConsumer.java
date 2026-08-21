package com.kenyarealestate.property.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kenyarealestate.property.entity.FailedEvent;
import com.kenyarealestate.property.repository.FailedEventRepository;
import com.kenyarealestate.property.service.PropertyService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.DltHandler;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.annotation.RetryableTopic;
import org.springframework.kafka.retrytopic.TopicSuffixingStrategy;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.retry.annotation.Backoff;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class VerificationEventConsumer {

    private final PropertyService propertyService;
    private final FailedEventRepository failedEventRepository;
    private final ObjectMapper objectMapper;

    public VerificationEventConsumer(PropertyService propertyService,
                                      FailedEventRepository failedEventRepository,
                                      ObjectMapper objectMapper) {
        this.propertyService = propertyService;
        this.failedEventRepository = failedEventRepository;
        this.objectMapper = objectMapper;
    }

    @RetryableTopic(
            attempts = "3",
            backoff = @Backoff(delay = 1000, multiplier = 2.0),
            topicSuffixingStrategy = TopicSuffixingStrategy.SUFFIX_WITH_INDEX_VALUE
    )
    @KafkaListener(topics = "verification-events", groupId = "property-service")
    public void handleVerificationEvent(
            @Payload Events.VerificationApprovedEvent event,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(name = "X-Correlation-Id", required = false) byte[] correlationIdBytes) {

        String traceId = correlationIdBytes != null ? new String(correlationIdBytes, java.nio.charset.StandardCharsets.UTF_8)
                : java.util.UUID.randomUUID().toString();
        org.slf4j.MDC.put("traceId", traceId);

        try {
            log.info("Received verification event: type={} sellerId={} propertyId={}",
                    event.getEventType(), event.getSellerId(), event.getPropertyId());

            if ("IDENTITY_APPROVED".equals(event.getEventType())) {
                propertyService.activateAllForSeller(event.getSellerId());
                log.info("Activated all listings for seller {} via Kafka", event.getSellerId());
            } else if ("OWNERSHIP_APPROVED".equals(event.getEventType())
                    && event.getPropertyId() != null) {
                propertyService.markOwnershipVerified(event.getPropertyId(),
                        event.getParcelNumber(), event.getTitleDeedNumber());
                log.info("Marked ownership verified for property {} via Kafka", event.getPropertyId());
            }
        } finally {
            org.slf4j.MDC.remove("traceId");
        }
    }

    @DltHandler
    public void handleDlt(
            @Payload Events.VerificationApprovedEvent event,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(name = KafkaHeaders.EXCEPTION_MESSAGE, required = false) String exceptionMessage) {
        log.error("ALERT: Verification event reached DLT on topic {}. Manual intervention required. event={}",
                topic, event);
        try {
            failedEventRepository.save(FailedEvent.builder()
                    .sourceTopic(topic)
                    .eventType(event != null ? event.getEventType() : null)
                    .payload(objectMapper.writeValueAsString(event))
                    .failureReason(exceptionMessage != null ? exceptionMessage : "Unknown - retries exhausted")
                    .build());
        } catch (Exception e) {
            log.error("Failed to persist DLT event for manual review (topic={}): {}", topic, e.getMessage());
        }
    }
}
