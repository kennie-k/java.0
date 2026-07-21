package com.kenyarealestate.viewing.repository;

import com.kenyarealestate.viewing.entity.Viewing;
import com.kenyarealestate.viewing.entity.ViewingStatus;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface ViewingRepository extends JpaRepository<Viewing, UUID> {
    Page<Viewing> findByBuyerId(UUID buyerId, Pageable p);
    Page<Viewing> findBySellerId(UUID sellerId, Pageable p);
    boolean existsByPropertyIdAndBuyerIdAndStatus(UUID propertyId, UUID buyerId, ViewingStatus status);
    Page<Viewing> findByPropertyIdAndStatus(UUID propertyId, ViewingStatus status, Pageable p);
    Optional<Viewing> findByViewingFeePaymentId(UUID paymentId);
}
