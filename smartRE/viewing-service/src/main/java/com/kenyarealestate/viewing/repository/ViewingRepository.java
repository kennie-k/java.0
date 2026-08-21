package com.kenyarealestate.viewing.repository;

import com.kenyarealestate.viewing.entity.Viewing;
import com.kenyarealestate.viewing.entity.ViewingStatus;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ViewingRepository extends JpaRepository<Viewing, UUID> {
    Page<Viewing> findByBuyerId(UUID buyerId, Pageable p);
    Page<Viewing> findBySellerId(UUID sellerId, Pageable p);
    boolean existsByPropertyIdAndBuyerIdAndStatus(UUID propertyId, UUID buyerId, ViewingStatus status);
    Optional<Viewing> findFirstByPropertyIdAndBuyerIdAndStatusInOrderByCreatedAtDesc(
            UUID propertyId, UUID buyerId, List<ViewingStatus> statuses);
    Page<Viewing> findByPropertyIdAndStatus(UUID propertyId, ViewingStatus status, Pageable p);
    Optional<Viewing> findByViewingFeePaymentId(UUID paymentId);

    // NOTE: confirmSeller/confirmBuyer/markNoShow used to go through bulk @Modifying JPQL
    // UPDATE statements here (markSellerConfirmed/markBuyerConfirmed/updateStatusIfDifferent).
    // Bulk JPQL updates bypass JPA's @Version optimistic-locking check entirely, so the
    // `version` column added in V4 was not actually protecting these flows against lost
    // updates from concurrent requests. ViewingService now loads the entity and calls
    // save()/flush() instead, so @Version is honored (a concurrent conflicting update raises
    // ObjectOptimisticLockingFailureException, mapped to 409 by GlobalExceptionHandler).
}
