package com.kenyarealestate.payment.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kenyarealestate.payment.entity.PaymentOutboxEvent;
import com.kenyarealestate.payment.repository.PaymentOutboxEventRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Backing store for the payment-events transactional outbox. {@link #recordPending}
 * deliberately does NOT use REQUIRES_NEW: it must participate in (and commit atomically
 * with) the caller's existing payment-status-update transaction, so the event record can
 * never exist without the state change it describes, or vice versa. The mark* methods run
 * in their own transaction since they are invoked from async Kafka callback threads /
 * the scheduled sweeper, after the original transaction has already committed.
 */
@Slf4j
@Service
public class PaymentOutboxService {

    private final PaymentOutboxEventRepository repo;
    private final ObjectMapper objectMapper;

    public PaymentOutboxService(PaymentOutboxEventRepository repo, ObjectMapper objectMapper) {
        this.repo = repo;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public PaymentOutboxEvent recordPending(UUID paymentId, String eventType, String topic, Object event) {
        String payload;
        try {
            payload = objectMapper.writeValueAsString(event);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize outbox event for paymentId=" + paymentId, e);
        }
        return repo.save(PaymentOutboxEvent.builder()
                .paymentId(paymentId)
                .eventType(eventType)
                .topic(topic)
                .payload(payload)
                .build());
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markPublished(UUID outboxId) {
        repo.findById(outboxId).ifPresent(e -> {
            e.setPublished(true);
            e.setPublishedAt(LocalDateTime.now());
            repo.save(e);
        });
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markAttemptFailed(UUID outboxId, String error) {
        repo.findById(outboxId).ifPresent(e -> {
            e.setAttempts(e.getAttempts() + 1);
            e.setLastError(error);
            repo.save(e);
        });
    }

    @Transactional(readOnly = true)
    public List<PaymentOutboxEvent> findUnpublishedOlderThan(Duration grace) {
        return repo.findByPublishedFalseAndCreatedAtBefore(LocalDateTime.now().minus(grace));
    }
}
