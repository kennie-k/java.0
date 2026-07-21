package com.kenyarealestate.review.repository;

import com.kenyarealestate.review.entity.Review;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.*;

@Repository
public interface ReviewRepository extends JpaRepository<Review, UUID> {
    Page<Review> findBySellerId(UUID sellerId, Pageable p);
    Page<Review> findByPropertyId(UUID propertyId, Pageable p);
    Page<Review> findByReviewerId(UUID reviewerId, Pageable p);
    boolean existsByPaymentId(UUID paymentId);
    boolean existsByReviewerIdAndPropertyId(UUID reviewerId, UUID propertyId);

    @Query("SELECT AVG(r.rating) FROM Review r WHERE r.sellerId = :id AND r.isVerified = true")
    Double avgRating(@Param("id") UUID id);

    @Query("SELECT COUNT(r) FROM Review r WHERE r.sellerId = :id AND r.isVerified = true")
    long countBySeller(@Param("id") UUID id);
}
