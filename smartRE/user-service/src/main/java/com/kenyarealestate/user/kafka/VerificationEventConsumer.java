package com.kenyarealestate.user.kafka;

import com.kenyarealestate.user.repository.UserRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Component
public class VerificationEventConsumer {

    private final UserRepository userRepository;

    public VerificationEventConsumer(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @KafkaListener(topics = "verification-events", groupId = "user-service")
    @Transactional
    public void handleVerificationEvent(
            @Payload Events.VerificationApprovedEvent event,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition) {

        if (!"IDENTITY_APPROVED".equals(event.getEventType()) || event.getSellerId() == null) return;

        try {
            userRepository.findById(event.getSellerId()).ifPresentOrElse(user -> {
                user.setVerified(true);
                userRepository.save(user);
                log.info("Marked user {} as verified via Kafka IDENTITY_APPROVED event", event.getSellerId());
            }, () -> log.warn("IDENTITY_APPROVED event received for unknown user {}", event.getSellerId()));
        } catch (Exception e) {
            log.error("Error handling IDENTITY_APPROVED event for user {}: {}", event.getSellerId(), e.getMessage(), e);
        }
    }
}
