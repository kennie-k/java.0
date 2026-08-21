package com.kenyarealestate.payment.repository;
import com.kenyarealestate.payment.entity.*; import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.*; import org.springframework.stereotype.Repository;
import jakarta.persistence.LockModeType;
import java.util.*; import java.util.UUID;
@Repository
public interface PaymentRepository extends JpaRepository<Payment,UUID> {

    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select p from Payment p where p.id = :id")
    Optional<Payment> findByIdForUpdate(java.util.UUID id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Payment p where p.mpesaCheckoutRequestId = :checkoutId")
    Optional<Payment> findByMpesaCheckoutRequestIdForUpdate(String checkoutId);
    Optional<Payment> findByIdempotencyKey(String key);
    Page<Payment> findByBuyerId(UUID buyerId, Pageable p);
    Page<Payment> findBySellerId(UUID sellerId, Pageable p);
    Page<Payment> findByStatusAndEscrowReleasedFalseAndPaymentTypeIn(
            com.kenyarealestate.payment.entity.PaymentStatus status,
            java.util.List<com.kenyarealestate.payment.entity.PaymentType> types,
            Pageable p);
    boolean existsByIdempotencyKey(String key);

    List<Payment> findByStatusAndCreatedAtBefore(PaymentStatus status, java.time.LocalDateTime before);

    long countByBuyerIdAndStatus(UUID buyerId, PaymentStatus status);
    long countByBuyerIdAndStatusIn(UUID buyerId, java.util.List<PaymentStatus> statuses);

    @Query("SELECT COALESCE(SUM(p.amount), 0) FROM Payment p WHERE p.buyerId = :buyerId AND p.status = :status")
    java.math.BigDecimal sumAmountByBuyerIdAndStatus(UUID buyerId, PaymentStatus status);
}
