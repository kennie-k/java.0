package com.kenyarealestate.user.kafka;

import com.kenyarealestate.user.repository.UserRepository;
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
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Component
public class VerificationEventConsumer {

    private final UserRepository userRepository;

    public VerificationEventConsumer(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @RetryableTopic(
            attempts = "3",
            backoff = @Backoff(delay = 1000, multiplier = 2.0),
            topicSuffixingStrategy = TopicSuffixingStrategy.SUFFIX_WITH_INDEX_VALUE
    )
    @KafkaListener(topics = "verification-events", groupId = "user-service")
    @Transactional
    public void handleVerificationEvent(
            @Payload Events.VerificationApprovedEvent event,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(name = "X-Correlation-Id", required = false) byte[] correlationIdBytes) {

        String traceId = correlationIdBytes != null ? new String(correlationIdBytes, java.nio.charset.StandardCharsets.UTF_8)
                : java.util.UUID.randomUUID().toString();
        org.slf4j.MDC.put("traceId", traceId);

        try {
            if (!"IDENTITY_APPROVED".equals(event.getEventType()) || event.getSellerId() == null) return;

            userRepository.findById(event.getSellerId()).ifPresentOrElse(user -> {
                user.setVerified(true);
                userRepository.save(user);
                log.info("Marked user {} as verified via Kafka IDENTITY_APPROVED event", event.getSellerId());
            }, () -> log.warn("IDENTITY_APPROVED event received for unknown user {}", event.getSellerId()));
        } finally {
            org.slf4j.MDC.remove("traceId");
        }
    }

    @DltHandler
    public void handleDlt(
            @Payload Events.VerificationApprovedEvent event,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic) {
        log.error("ALERT: Verification event for user {} reached DLT on topic {}. Manual intervention required.",
                event.getSellerId(), topic);
    }
}
