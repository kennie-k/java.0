package com.kenyarealestate.payment.repository;

import com.kenyarealestate.payment.entity.CompanyRevenue;
import com.kenyarealestate.payment.entity.RevenueStatus;
import com.kenyarealestate.payment.entity.RevenueType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface CompanyRevenueRepository extends JpaRepository<CompanyRevenue, UUID> {

    Optional<CompanyRevenue> findByPaymentId(UUID paymentId);

    boolean existsByPaymentId(UUID paymentId);

    Page<CompanyRevenue> findAllByOrderByCreatedAtDesc(Pageable pageable);

    Page<CompanyRevenue> findByRevenueTypeOrderByCreatedAtDesc(RevenueType type, Pageable pageable);

    Optional<CompanyRevenue> findByB2cOriginatorConversationId(String originatorConversationId);

    Optional<CompanyRevenue> findByStatusQueryConversationId(String statusQueryConversationId);

    List<CompanyRevenue> findByStatusAndCreatedAtBefore(RevenueStatus status, LocalDateTime cutoff);

    @Query("SELECT COALESCE(SUM(r.platformFee), 0) FROM CompanyRevenue r")
    BigDecimal sumTotalPlatformFees();

    @Query("SELECT COALESCE(SUM(r.platformFee), 0) FROM CompanyRevenue r WHERE r.revenueType = :type")
    BigDecimal sumPlatformFeesByType(RevenueType type);

    @Query("SELECT COALESCE(SUM(r.platformFee), 0) FROM CompanyRevenue r WHERE r.createdAt >= :from AND r.createdAt < :to")
    BigDecimal sumPlatformFeesBetween(LocalDateTime from, LocalDateTime to);

    @Query("SELECT COUNT(r) FROM CompanyRevenue r WHERE r.createdAt >= :from AND r.createdAt < :to")
    long countTransactionsBetween(LocalDateTime from, LocalDateTime to);
}
