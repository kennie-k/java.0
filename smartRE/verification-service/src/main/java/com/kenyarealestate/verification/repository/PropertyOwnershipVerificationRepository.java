package com.kenyarealestate.verification.repository;

import com.kenyarealestate.verification.entity.PropertyOwnershipVerification;
import com.kenyarealestate.verification.enums.OwnershipVerificationStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PropertyOwnershipVerificationRepository extends JpaRepository<PropertyOwnershipVerification, UUID> {
    Optional<PropertyOwnershipVerification> findByPropertyId(UUID propertyId);
    boolean existsByPropertyIdAndStatus(UUID propertyId, OwnershipVerificationStatus status);
    boolean existsByParcelNumberAndPropertyIdNotAndStatusNot(
            String parcelNumber, UUID propertyId, OwnershipVerificationStatus excludedStatus);
    boolean existsByTitleDeedNumberAndPropertyIdNotAndStatusNot(
            String titleDeedNumber, UUID propertyId, OwnershipVerificationStatus excludedStatus);
    boolean existsByLrNumberAndPropertyIdNotAndStatusNot(
            String lrNumber, UUID propertyId, OwnershipVerificationStatus excludedStatus);
    List<PropertyOwnershipVerification> findBySellerIdentityVerificationUserId(UUID userId);
    Page<PropertyOwnershipVerification> findByStatus(OwnershipVerificationStatus status, Pageable pageable);
}
