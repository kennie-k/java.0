package com.kenyarealestate.property.kafka;

import com.kenyarealestate.property.service.PropertyService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Slf4j
@Component
public class VerificationEventConsumer {

    private final PropertyService propertyService;

    public VerificationEventConsumer(PropertyService propertyService) {
        this.propertyService = propertyService;
    }

    @KafkaListener(topics = "verification-events", groupId = "property-service")
    public void handleVerificationEvent(
            @Payload Events.VerificationApprovedEvent event,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition) {

        log.info("Received verification event: type={} sellerId={} propertyId={}",
                event.getEventType(), event.getSellerId(), event.getPropertyId());

        try {
            if ("IDENTITY_APPROVED".equals(event.getEventType())) {
                propertyService.activateAllForSeller(event.getSellerId());
                log.info("Activated all listings for seller {} via Kafka", event.getSellerId());
            } else if ("OWNERSHIP_APPROVED".equals(event.getEventType())
                    && event.getPropertyId() != null) {
                propertyService.markOwnershipVerified(event.getPropertyId(), null, null);
                log.info("Marked ownership verified for property {} via Kafka", event.getPropertyId());
            }
        } catch (Exception e) {
            log.error("Error handling verification event for seller {}: {}",
                    event.getSellerId(), e.getMessage(), e);

        }
    }
}