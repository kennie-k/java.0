package com.kenyarealestate.payment.repository;

import com.kenyarealestate.payment.entity.PaymentReceipt;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface PaymentReceiptRepository extends JpaRepository<PaymentReceipt, UUID> {

    Optional<PaymentReceipt> findByPaymentId(UUID paymentId);

    Optional<PaymentReceipt> findByReceiptNumber(String receiptNumber);

    boolean existsByPaymentId(UUID paymentId);
}
