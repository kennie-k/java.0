package com.kenyarealestate.viewing.kafka;

import com.kenyarealestate.viewing.entity.Viewing;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Slf4j
@Component
public class ViewingEventPublisher {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    @Value("${kafka.topics.viewing-events:viewing-events}")
    private String topic;

    public ViewingEventPublisher(KafkaTemplate<String, Object> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    public void publishViewingCompleted(Viewing viewing) {
        var event = Events.ViewingCompletedEvent.builder()
                .eventType("VIEWING_COMPLETED")
                .viewingId(viewing.getId())
                .propertyId(viewing.getPropertyId())
                .buyerId(viewing.getBuyerId())
                .sellerId(viewing.getSellerId())
                .completedAt(LocalDateTime.now())
                .build();

        kafkaTemplate.send(topic, viewing.getPropertyId().toString(), event)
                .whenComplete((result, ex) -> {
                    if (ex != null)
                        log.error("Failed to publish VIEWING_COMPLETED: {}", ex.getMessage());
                    else
                        log.info("Published VIEWING_COMPLETED viewingId={}", viewing.getId());
                });
    }
}
