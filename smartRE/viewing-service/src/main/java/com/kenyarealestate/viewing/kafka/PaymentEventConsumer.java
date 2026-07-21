package com.kenyarealestate.viewing.kafka;

import com.kenyarealestate.viewing.service.ViewingService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class PaymentEventConsumer {

    private final ViewingService viewingService;

    public PaymentEventConsumer(ViewingService viewingService) {
        this.viewingService = viewingService;
    }

    @KafkaListener(topics = "payment-events", groupId = "viewing-service-fee-tracker")
    public void onPaymentCompleted(
            @Payload Events.PaymentCompletedEvent event,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic) {
        if (!"PAYMENT_COMPLETED".equals(event.getEventType())) return;
        try {
            viewingService.markFeeCompleted(null, event.getPaymentId());
        } catch (Exception e) {
            log.warn("Could not mark viewing fee completed for paymentId={}: {}",
                    event.getPaymentId(), e.getMessage());
        }
    }
}
