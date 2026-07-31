package com.kenyarealestate.review.kafka;

import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.kafka.annotation.DltHandler;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.annotation.RetryableTopic;
import org.springframework.kafka.retrytopic.TopicSuffixingStrategy;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.retry.annotation.Backoff;
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

    @RetryableTopic(
            attempts = "3",
            backoff = @Backoff(delay = 1000, multiplier = 2.0),
            topicSuffixingStrategy = TopicSuffixingStrategy.SUFFIX_WITH_INDEX_VALUE
    )
    @KafkaListener(topics = "payment-events", groupId = "review-service")
    public void handlePaymentCompleted(
            @Payload Events.PaymentCompletedEvent event,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(name = "X-Correlation-Id", required = false) byte[] correlationIdBytes) {

        String traceId = correlationIdBytes != null ? new String(correlationIdBytes, java.nio.charset.StandardCharsets.UTF_8)
                : java.util.UUID.randomUUID().toString();
        org.slf4j.MDC.put("traceId", traceId);

        try {
            if (!"PAYMENT_COMPLETED".equals(event.getEventType())) return;
            if (!"FULL_PAYMENT".equals(event.getPaymentType()) && !"DEPOSIT".equals(event.getPaymentType())) return;

            log.info("Payment completed event received: paymentId={} buyerId={}",
                    event.getPaymentId(), event.getBuyerId());

            String eligibility = event.getBuyerId() + "|" + event.getSellerId() + "|" + event.getPropertyId();
            redis.opsForValue().set(PREFIX + event.getPaymentId(), eligibility, TTL);
            log.info("Review eligibility stored for paymentId={}", event.getPaymentId());
        } finally {
            org.slf4j.MDC.remove("traceId");
        }
    }

    @DltHandler
    public void handleDlt(
            @Payload Events.PaymentCompletedEvent event,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic) {
        log.error("ALERT: Payment completed event for paymentId={} reached DLT on topic {}. Manual intervention required.",
                event.getPaymentId(), topic);
    }
}
