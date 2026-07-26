package com.kenyarealestate.payment.kafka;

import com.kenyarealestate.payment.entity.Payment;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Slf4j
@Component
public class PaymentEventPublisher {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    @Value("${kafka.topics.payment-events:payment-events}")
    private String topic;

    public PaymentEventPublisher(KafkaTemplate<String, Object> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    public void publishPaymentCompleted(Payment payment) {
        var event = Events.PaymentCompletedEvent.builder()
                .eventType("PAYMENT_COMPLETED")
                .paymentId(payment.getId())
                .buyerId(payment.getBuyerId())
                .sellerId(payment.getSellerId())
                .propertyId(payment.getPropertyId())
                .amount(payment.getAmount())
                .currency(payment.getCurrency())
                .mpesaReceiptNumber(payment.getMpesaReceiptNumber())
                .paymentType(payment.getPaymentType().name())
                .completedAt(LocalDateTime.now())
                .build();
        kafkaTemplate.send(topic, payment.getId().toString(), event)
                .whenComplete((result, ex) -> {
                    if (ex != null)
                        log.error("Failed to publish PAYMENT_COMPLETED: {}", ex.getMessage());
                    else
                        log.info("Published PAYMENT_COMPLETED paymentId={} receipt={}",
                                payment.getId(), payment.getMpesaReceiptNumber());
                });
    }
}
