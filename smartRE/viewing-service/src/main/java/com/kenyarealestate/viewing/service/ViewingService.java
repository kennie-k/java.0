package com.kenyarealestate.viewing.service;

import com.kenyarealestate.viewing.client.PaymentClient;
import com.kenyarealestate.viewing.dto.ScheduleViewingRequest;
import com.kenyarealestate.viewing.dto.ViewingResponse;
import com.kenyarealestate.viewing.entity.Viewing;
import com.kenyarealestate.viewing.entity.ViewingAuditLog;
import com.kenyarealestate.viewing.entity.ViewingStatus;
import com.kenyarealestate.viewing.kafka.ViewingEventPublisher;
import com.kenyarealestate.viewing.repository.ViewingAuditLogRepository;
import com.kenyarealestate.viewing.repository.ViewingRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

@Slf4j
@Service
@Transactional
public class ViewingService {

    private final ViewingRepository repo;
    private final ViewingEventPublisher eventPublisher;
    private final PaymentClient paymentClient;
    private final ViewingAuditLogRepository auditRepo;

    public ViewingService(ViewingRepository repo,
                          ViewingEventPublisher eventPublisher,
                          PaymentClient paymentClient,
                          ViewingAuditLogRepository auditRepo) {
        this.repo = repo;
        this.eventPublisher = eventPublisher;
        this.paymentClient = paymentClient;
        this.auditRepo = auditRepo;
    }

    public ViewingResponse schedule(UUID buyerId, ScheduleViewingRequest req, String clientIp) {
        Viewing v = Viewing.builder()
                .propertyId(req.getPropertyId())
                .buyerId(buyerId)
                .sellerId(req.getSellerId())
                .scheduledAt(req.getScheduledAt())
                .notes(req.getNotes())
                .buyerPhone(req.getBuyerPhone())
                .status(ViewingStatus.PENDING_FEE)
                .build();
        v = repo.save(v);

        audit(v.getId(), "VIEWING_CREATED", null, ViewingStatus.PENDING_FEE.name(),
                buyerId, "BUYER", clientIp,
                "Viewing scheduled. propertyId=" + req.getPropertyId()
                + " sellerId=" + req.getSellerId()
                + " scheduledAt=" + req.getScheduledAt());

        PaymentClient.PaymentInitResult feeResult = paymentClient.initiateViewingFee(
                buyerId, req.getSellerId(), req.getPropertyId(), v.getId(), req.getBuyerPhone());

        if (feeResult != null) {
            v.setViewingFeePaymentId(feeResult.getId());
            v.setViewingFeeStatus(feeResult.getStatus());
            v = repo.save(v);
            audit(v.getId(), "VIEWING_FEE_INITIATED", null, ViewingStatus.PENDING_FEE.name(),
                    null, "SYSTEM", null,
                    "STK push sent for viewing fee. paymentId=" + feeResult.getId()
                    + " status=" + feeResult.getStatus());
        } else {
            audit(v.getId(), "VIEWING_FEE_INITIATION_FAILED", null, ViewingStatus.PENDING_FEE.name(),
                    buyerId, "BUYER", clientIp,
                    "Payment service call failed for viewing fee");
        }
        return toResponse(v);
    }

    public ViewingResponse markFeeCompleted(UUID viewingId, UUID paymentId) {
        Viewing v = repo.findByViewingFeePaymentId(paymentId).orElseGet(() -> find(viewingId));
        String prevStatus = v.getStatus().name();
        v.setViewingFeeStatus("COMPLETED");
        v.setViewingFeePaymentId(paymentId);
        v.setStatus(ViewingStatus.REQUESTED);
        v = repo.save(v);
        audit(v.getId(), "VIEWING_FEE_COMPLETED", prevStatus, ViewingStatus.REQUESTED.name(),
                null, "KAFKA_CONSUMER", null,
                "Viewing fee payment confirmed. paymentId=" + paymentId);
        return toResponse(v);
    }

    @Transactional(readOnly = true)
    public Page<ViewingResponse> myBuyer(UUID buyerId, Pageable p) {
        return repo.findByBuyerId(buyerId, p).map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public Page<ViewingResponse> mySeller(UUID sellerId, Pageable p) {
        return repo.findBySellerId(sellerId, p).map(this::toResponse);
    }

    public ViewingResponse confirmSeller(UUID id, UUID sellerId, String clientIp) {
        Viewing v = find(id);
        if (!v.getSellerId().equals(sellerId)) throw new RuntimeException("Access denied");
        if (v.getStatus() == ViewingStatus.PENDING_FEE)
            throw new RuntimeException("Viewing fee payment not yet completed");
        String prevStatus = v.getStatus().name();
        v.setSellerConfirmed(true);
        if (v.isBuyerConfirmed()) v.setStatus(ViewingStatus.CONFIRMED);
        v = repo.save(v);
        audit(id, "SELLER_CONFIRMED", prevStatus, v.getStatus().name(),
                sellerId, "SELLER", clientIp, "Seller confirmed viewing");
        return toResponse(v);
    }

    public ViewingResponse confirmBuyer(UUID id, UUID buyerId, String clientIp) {
        Viewing v = find(id);
        if (!v.getBuyerId().equals(buyerId)) throw new RuntimeException("Access denied");
        if (v.getStatus() == ViewingStatus.PENDING_FEE)
            throw new RuntimeException("Viewing fee payment not yet completed");
        String prevStatus = v.getStatus().name();
        v.setBuyerConfirmed(true);
        if (v.isSellerConfirmed()) v.setStatus(ViewingStatus.CONFIRMED);
        v = repo.save(v);
        audit(id, "BUYER_CONFIRMED", prevStatus, v.getStatus().name(),
                buyerId, "BUYER", clientIp, "Buyer confirmed viewing");
        return toResponse(v);
    }

    public ViewingResponse markCompleted(UUID id, UUID callerId, boolean isAdmin) {
        Viewing v = find(id);
        if (!isAdmin && !callerId.equals(v.getBuyerId()) && !callerId.equals(v.getSellerId())) {
            throw new RuntimeException("Only the buyer, seller, or an admin can complete this viewing");
        }
        if (!v.isBuyerConfirmed() || !v.isSellerConfirmed())
            throw new RuntimeException("Both parties must confirm before completing");
        String prevStatus = v.getStatus().name();
        v.setStatus(ViewingStatus.COMPLETED);
        v.setCompletedAt(LocalDateTime.now());
        Viewing saved = repo.save(v);
        audit(id, "VIEWING_COMPLETED", prevStatus, ViewingStatus.COMPLETED.name(),
                null, "SYSTEM", null, "Viewing marked completed");
        eventPublisher.publishViewingCompleted(saved);
        return toResponse(saved);
    }

    public ViewingResponse cancel(UUID id, UUID userId, String reason, String clientIp) {
        Viewing v = find(id);
        if (v.getStatus() == ViewingStatus.COMPLETED)
            throw new RuntimeException("Cannot cancel completed viewing");
        String prevStatus = v.getStatus().name();
        v.setStatus(ViewingStatus.CANCELLED);
        v.setCancelledBy(userId);
        v.setCancellationReason(reason);
        v = repo.save(v);
        audit(id, "VIEWING_CANCELLED", prevStatus, ViewingStatus.CANCELLED.name(),
                userId, "USER", clientIp, "Cancelled. reason=" + reason);
        return toResponse(v);
    }

    public boolean hasCompletedViewing(UUID propertyId, UUID buyerId) {
        return repo.existsByPropertyIdAndBuyerIdAndStatus(propertyId, buyerId, ViewingStatus.COMPLETED);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void audit(UUID viewingId, String eventType, String prevStatus, String newStatus,
                      UUID actorId, String actorRole, String actorIp, String detail) {
        try {
            auditRepo.save(ViewingAuditLog.builder()
                    .viewingId(viewingId)
                    .eventType(eventType)
                    .previousStatus(prevStatus)
                    .newStatus(newStatus)
                    .actorId(actorId)
                    .actorRole(actorRole)
                    .actorIp(actorIp)
                    .detail(detail)
                    .build());
        } catch (Exception e) {
            log.error("VIEWING AUDIT FAILURE viewingId={} event={}: {}", viewingId, eventType, e.getMessage());
        }
    }

    private Viewing find(UUID id) {
        return repo.findById(id).orElseThrow(() -> new RuntimeException("Viewing not found"));
    }

    private ViewingResponse toResponse(Viewing v) {
        return ViewingResponse.builder()
                .id(v.getId()).propertyId(v.getPropertyId())
                .buyerId(v.getBuyerId()).sellerId(v.getSellerId())
                .scheduledAt(v.getScheduledAt()).completedAt(v.getCompletedAt())
                .status(v.getStatus().name()).notes(v.getNotes())
                .buyerConfirmed(v.isBuyerConfirmed()).sellerConfirmed(v.isSellerConfirmed())
                .viewingFeePaymentId(v.getViewingFeePaymentId())
                .viewingFeeStatus(v.getViewingFeeStatus())
                .createdAt(v.getCreatedAt()).updatedAt(v.getUpdatedAt())
                .build();
    }
}
