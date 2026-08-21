package com.kenyarealestate.verification.repository;

import com.kenyarealestate.verification.entity.SellerIdentityVerification;
import com.kenyarealestate.verification.enums.IdentityVerificationStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface SellerIdentityVerificationRepository extends JpaRepository<SellerIdentityVerification, UUID> {
    Optional<SellerIdentityVerification> findByUserId(UUID userId);
    boolean existsByUserId(UUID userId);
    boolean existsByNationalIdNumberAndUserIdNot(String nationalIdNumber, UUID userId);
    Page<SellerIdentityVerification> findByStatus(IdentityVerificationStatus status, Pageable pageable);
    List<SellerIdentityVerification> findByStatusAndExpiresAtBefore(
            IdentityVerificationStatus status, LocalDateTime cutoff);
    long countByPermanentlyBannedTrue();
    @Query("SELECT COALESCE(SUM(v.fraudStrikeCount), 0) FROM SellerIdentityVerification v")
    long sumFraudStrikeCount();
}
