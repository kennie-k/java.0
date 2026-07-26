package com.kenyarealestate.review.kafka;

import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Slf4j
@Component
public class PaymentEventConsumer {

    private final RedisTemplate<String, Object> redis;
    private static final String PREFIX = "payment:eligible:";
    private static final Duration TTL = Duration.ofDays(365);

    public PaymentEventConsumer(RedisTemplate<String, Object> redis) {
        this.redis = redis;
    }

    @KafkaListener(topics = "payment-events", groupId = "review-service")
    public void handlePaymentCompleted(
            @Payload Events.PaymentCompletedEvent event,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic) {

        if (!"PAYMENT_COMPLETED".equals(event.getEventType())) return;
        if (!"FULL_PAYMENT".equals(event.getPaymentType()) && !"DEPOSIT".equals(event.getPaymentType())) return;

        log.info("Payment completed event received: paymentId={} buyerId={}",
                event.getPaymentId(), event.getBuyerId());

        try {
            redis.opsForValue().set(
                    PREFIX + event.getPaymentId(),
                    event.getBuyerId().toString(),
                    TTL);
            log.info("Review eligibility stored for paymentId={}", event.getPaymentId());
        } catch (Exception e) {
            log.error("Failed to store review eligibility: {}", e.getMessage());
        }
    }
}
