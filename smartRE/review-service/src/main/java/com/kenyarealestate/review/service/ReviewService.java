package com.kenyarealestate.review.service;

import com.kenyarealestate.review.dto.*;
import com.kenyarealestate.review.entity.Review;
import com.kenyarealestate.review.entity.ReviewAuditLog;
import com.kenyarealestate.review.repository.ReviewAuditLogRepository;
import com.kenyarealestate.review.repository.ReviewRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.*;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.UUID;

@Slf4j
@Service
@Transactional
public class ReviewService {

    private final ReviewRepository repo;
    private final ReviewAuditLogRepository auditRepo;
    private final RedisTemplate<String, Object> redis;

    private static final String PAYMENT_ELIGIBLE_PREFIX = "payment:eligible:";
    private static final String RATING_PREFIX = "rating:";

    @Value("${redis.seller-rating-ttl-seconds:60}")
    private long ratingTtlSeconds;

    public ReviewService(ReviewRepository repo,
                         ReviewAuditLogRepository auditRepo,
                         RedisTemplate<String, Object> redis) {
        this.repo = repo;
        this.auditRepo = auditRepo;
        this.redis = redis;
    }

    public ReviewResponse create(UUID reviewerId, CreateReviewRequest req, String clientIp) {
        if (repo.existsByPaymentId(req.getPaymentId()))
            throw new RuntimeException("A review already exists for this payment.");

        if (repo.existsByReviewerIdAndPropertyId(reviewerId, req.getPropertyId()))
            throw new RuntimeException("You have already reviewed this property.");

        Object eligibleBuyer = redis.opsForValue().get(PAYMENT_ELIGIBLE_PREFIX + req.getPaymentId());
        if (eligibleBuyer == null || !eligibleBuyer.toString().equals(reviewerId.toString())) {
            audit(null, "REVIEW_REJECTED_PAYMENT_MISMATCH", reviewerId, clientIp,
                    "Reviewer " + reviewerId + " tried to review payment " + req.getPaymentId()
                            + " with no matching completed-payment eligibility record");
            throw new RuntimeException("Payment does not belong to this user.");
        }

        Review r = repo.save(Review.builder()
                .reviewerId(reviewerId)
                .sellerId(req.getSellerId())
                .propertyId(req.getPropertyId())
                .paymentId(req.getPaymentId())
                .rating(req.getRating())
                .comment(req.getComment())
                .build());

        audit(r.getId(), "REVIEW_CREATED", reviewerId, clientIp,
                "Review created. rating=" + req.getRating()
                        + " paymentId=" + req.getPaymentId()
                        + " propertyId=" + req.getPropertyId());

        evictRatingCache(req.getSellerId());
        return toResponse(r);
    }

    @Transactional(readOnly = true)
    public Page<ReviewResponse> getByProperty(UUID propertyId, Pageable p) {
        return repo.findByPropertyId(propertyId, p).map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public Page<ReviewResponse> getBySeller(UUID sellerId, Pageable p) {
        return repo.findBySellerId(sellerId, p).map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public Page<ReviewResponse> getByReviewer(UUID reviewerId, Pageable p) {
        return repo.findByReviewerId(reviewerId, p).map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public SellerRatingResponse getSellerRating(UUID sellerId) {
        String key = RATING_PREFIX + sellerId;
        try {
            Object cached = redis.opsForValue().get(key);
            if (cached instanceof SellerRatingResponse r) return r;
        } catch (Exception e) {
            log.warn("Rating cache read error: {}", e.getMessage());
        }
        Double avg = repo.avgRating(sellerId);
        long count = repo.countBySeller(sellerId);
        SellerRatingResponse resp = SellerRatingResponse.builder()
                .sellerId(sellerId)
                .averageRating(avg != null ? Math.round(avg * 10.0) / 10.0 : 0.0)
                .reviewCount(count)
                .build();
        try {
            redis.opsForValue().set(key, resp, Duration.ofSeconds(ratingTtlSeconds));
        } catch (Exception e) {
            log.warn("Rating cache write error: {}", e.getMessage());
        }
        return resp;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void audit(UUID reviewId, String eventType, UUID actorId, String actorIp, String detail) {
        try {
            auditRepo.save(ReviewAuditLog.builder()
                    .reviewId(reviewId)
                    .eventType(eventType)
                    .actorId(actorId)
                    .actorIp(actorIp)
                    .detail(detail)
                    .build());
        } catch (Exception e) {
            log.error("AUDIT LOG FAILURE reviewId={} event={}: {}", reviewId, eventType, e.getMessage());
        }
    }

    private void evictRatingCache(UUID sellerId) {
        try { redis.delete(RATING_PREFIX + sellerId); } catch (Exception ignored) {}
    }

    private ReviewResponse toResponse(Review r) {
        return ReviewResponse.builder()
                .id(r.getId()).reviewerId(r.getReviewerId())
                .sellerId(r.getSellerId()).propertyId(r.getPropertyId())
                .paymentId(r.getPaymentId()).rating(r.getRating())
                .comment(r.getComment()).isVerified(r.isVerified())
                .createdAt(r.getCreatedAt())
                .build();
    }
}