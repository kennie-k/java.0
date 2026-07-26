package com.kenyarealestate.payment.repository;
import com.kenyarealestate.payment.entity.*; import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.*; import org.springframework.stereotype.Repository;
import java.util.*; import java.util.UUID;
@Repository
public interface PaymentRepository extends JpaRepository<Payment,UUID> {
    Optional<Payment> findByMpesaCheckoutRequestId(String id);
    Optional<Payment> findByIdempotencyKey(String key);
    Page<Payment> findByBuyerId(UUID buyerId, Pageable p);
    Page<Payment> findBySellerId(UUID sellerId, Pageable p);
    Page<Payment> findByStatusAndEscrowReleasedFalseAndPaymentTypeIn(
            com.kenyarealestate.payment.entity.PaymentStatus status,
            java.util.List<com.kenyarealestate.payment.entity.PaymentType> types,
            Pageable p);
    boolean existsByIdempotencyKey(String key);
}
