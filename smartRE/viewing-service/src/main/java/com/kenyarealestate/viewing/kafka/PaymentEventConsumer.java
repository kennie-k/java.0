package com.kenyarealestate.viewing.kafka;

import com.kenyarealestate.viewing.service.ViewingService;
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
public class PaymentEventConsumer {

    private final ViewingService viewingService;

    public PaymentEventConsumer(ViewingService viewingService) {
        this.viewingService = viewingService;
    }

    @RetryableTopic(
            attempts = "3",
            backoff = @Backoff(delay = 1000, multiplier = 2.0),
            topicSuffixingStrategy = TopicSuffixingStrategy.SUFFIX_WITH_INDEX_VALUE
    )
    @KafkaListener(topics = "payment-events", groupId = "viewing-service-fee-tracker")
    public void onPaymentCompleted(
            @Payload Events.PaymentCompletedEvent event,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(name = "X-Correlation-Id", required = false) byte[] correlationIdBytes) {

        String traceId = correlationIdBytes != null ? new String(correlationIdBytes, java.nio.charset.StandardCharsets.UTF_8)
                : java.util.UUID.randomUUID().toString();
        org.slf4j.MDC.put("traceId", traceId);

        try {
            if (!"PAYMENT_COMPLETED".equals(event.getEventType())) return;
            if (!"VIEWING_FEE".equals(event.getPaymentType())) return;

            viewingService.markFeeCompleted(event.getPaymentId());
        } finally {
            org.slf4j.MDC.remove("traceId");
        }
    }

    @DltHandler
    public void handleDlt(
            @Payload Events.PaymentCompletedEvent event,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic) {
        log.error("ALERT: Viewing fee payment event for paymentId={} reached DLT on topic {}. Manual intervention required.",
                event.getPaymentId(), topic);
    }
}
