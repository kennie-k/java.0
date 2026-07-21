package com.kenyarealestate.review.repository;

import com.kenyarealestate.review.entity.ReviewAuditLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface ReviewAuditLogRepository extends JpaRepository<ReviewAuditLog, UUID> {
    List<ReviewAuditLog> findByReviewIdOrderByCreatedAtAsc(UUID reviewId);
}
