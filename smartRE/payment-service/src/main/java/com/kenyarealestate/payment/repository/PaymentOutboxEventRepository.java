package com.kenyarealestate.payment.repository;

import com.kenyarealestate.payment.entity.PaymentOutboxEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Repository
public interface PaymentOutboxEventRepository extends JpaRepository<PaymentOutboxEvent, UUID> {

    List<PaymentOutboxEvent> findByPublishedFalseAndCreatedAtBefore(LocalDateTime cutoff);
}
