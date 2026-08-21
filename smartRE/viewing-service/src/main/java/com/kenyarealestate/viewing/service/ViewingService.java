package com.kenyarealestate.viewing.service;

import com.kenyarealestate.viewing.client.PaymentClient;
import com.kenyarealestate.viewing.client.PropertyClient;
import com.kenyarealestate.viewing.dto.ScheduleViewingRequest;
import com.kenyarealestate.viewing.dto.ViewingResponse;
import com.kenyarealestate.viewing.entity.Viewing;
import com.kenyarealestate.viewing.entity.ViewingAuditLog;
import com.kenyarealestate.viewing.entity.ViewingStatus;
import com.kenyarealestate.viewing.exception.ConflictException;
import com.kenyarealestate.viewing.exception.ForbiddenException;
import com.kenyarealestate.viewing.exception.NotFoundException;
import com.kenyarealestate.viewing.kafka.ViewingEventPublisher;
import com.kenyarealestate.viewing.repository.ViewingAuditLogRepository;
import com.kenyarealestate.viewing.repository.ViewingRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
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
    private final PropertyClient propertyClient;
    private final ViewingAuditLogRepository auditRepo;

    public ViewingService(ViewingRepository repo,
                          ViewingEventPublisher eventPublisher,
                          PaymentClient paymentClient,
                          PropertyClient propertyClient,
                          ViewingAuditLogRepository auditRepo) {
        this.repo = repo;
        this.eventPublisher = eventPublisher;
        this.paymentClient = paymentClient;
        this.propertyClient = propertyClient;
        this.auditRepo = auditRepo;
    }

    private static final java.util.List<ViewingStatus> ACTIVE_STATUSES =
            java.util.List.of(ViewingStatus.PENDING_FEE, ViewingStatus.REQUESTED, ViewingStatus.CONFIRMED);

    public ViewingResponse schedule(UUID buyerId, ScheduleViewingRequest req, String clientIp) {
        if (!propertyClient.isSellerIdentityVerified(req.getPropertyId())) {
            throw new RuntimeException(
                    "This seller has not completed identity verification. Viewings cannot be scheduled until verification is approved.");
        }
        UUID actualSellerId = propertyClient.getSellerId(req.getPropertyId());
        if (actualSellerId != null && !actualSellerId.equals(req.getSellerId())) {
            throw new ForbiddenException("Seller does not match the property's registered owner");
        }
        // Fast-fail pre-check for a friendly error message. This is inherently a check-then-act
        // race under concurrent requests — the real guard against double-booking is the partial
        // unique index added in V5__prevent_double_booking.sql, enforced below at insert time.
        repo.findFirstByPropertyIdAndBuyerIdAndStatusInOrderByCreatedAtDesc(
                req.getPropertyId(), buyerId, ACTIVE_STATUSES).ifPresent(existing -> {
            throw new ConflictException("You already have a viewing scheduled for this property (status: "
                    + existing.getStatus() + "). Cancel it before scheduling another.");
        });
        Viewing v = Viewing.builder()
                .propertyId(req.getPropertyId())
                .buyerId(buyerId)
                .sellerId(req.getSellerId())
                .scheduledAt(req.getScheduledAt())
                .notes(req.getNotes())
                .buyerPhone(req.getBuyerPhone())
                .status(ViewingStatus.PENDING_FEE)
                .createdAt(java.time.LocalDateTime.now())
                .build();
        try {
            v = repo.save(v);
            repo.flush();
        } catch (DataIntegrityViolationException e) {
            log.warn("Double-booking prevented at the database level for propertyId={} buyerId={} scheduledAt={}: {}",
                    req.getPropertyId(), buyerId, req.getScheduledAt(), e.getMessage());
            throw new ConflictException(
                    "This property already has an active viewing booked for you, or that time slot is already taken. "
                    + "Please choose a different time or check your existing bookings.");
        }

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

    public ViewingResponse markFeeCompleted(UUID paymentId) {
        Viewing v = repo.findByViewingFeePaymentId(paymentId).orElseThrow(() -> new NotFoundException(
                "No viewing found yet for paymentId=" + paymentId + " - retrying"));
        String prevStatus = v.getStatus().name();
        v.setViewingFeeStatus("COMPLETED");
        v.setViewingFeePaymentId(paymentId);

        if (v.getStatus() == ViewingStatus.PENDING_FEE) {
            v.setStatus(ViewingStatus.REQUESTED);
        }
        v = repo.save(v);
        audit(v.getId(), "VIEWING_FEE_COMPLETED", prevStatus, v.getStatus().name(),
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
        if (!v.getSellerId().equals(sellerId)) throw new ForbiddenException("Access denied");
        if (v.getStatus() == ViewingStatus.PENDING_FEE)
            throw new RuntimeException("Viewing fee payment not yet completed");
        if (v.getStatus() == ViewingStatus.CANCELLED || v.getStatus() == ViewingStatus.COMPLETED)
            throw new RuntimeException("Cannot confirm a viewing with status: " + v.getStatus());
        String prevStatus = v.getStatus().name();
        v.setSellerConfirmed(true);
        if (v.isBuyerConfirmed()) {
            v.setStatus(ViewingStatus.CONFIRMED);
        }
        // save()/flush() through the entity (rather than a bulk @Modifying query) so @Version
        // is actually honored: a concurrent conflicting update raises
        // ObjectOptimisticLockingFailureException, mapped to 409 by GlobalExceptionHandler.
        Viewing saved = repo.saveAndFlush(v);
        audit(id, "SELLER_CONFIRMED", prevStatus, saved.getStatus().name(),
                sellerId, "SELLER", clientIp, "Seller confirmed viewing");
        return toResponse(saved);
    }

    public ViewingResponse confirmBuyer(UUID id, UUID buyerId, String clientIp) {
        Viewing v = find(id);
        if (!v.getBuyerId().equals(buyerId)) throw new ForbiddenException("Access denied");
        if (v.getStatus() == ViewingStatus.PENDING_FEE)
            throw new RuntimeException("Viewing fee payment not yet completed");
        if (v.getStatus() == ViewingStatus.CANCELLED || v.getStatus() == ViewingStatus.COMPLETED)
            throw new RuntimeException("Cannot confirm a viewing with status: " + v.getStatus());
        String prevStatus = v.getStatus().name();
        v.setBuyerConfirmed(true);
        if (v.isSellerConfirmed()) {
            v.setStatus(ViewingStatus.CONFIRMED);
        }
        Viewing saved = repo.saveAndFlush(v);
        audit(id, "BUYER_CONFIRMED", prevStatus, saved.getStatus().name(),
                buyerId, "BUYER", clientIp, "Buyer confirmed viewing");
        return toResponse(saved);
    }

    public ViewingResponse markCompleted(UUID id, UUID callerId, boolean isAdmin) {
        Viewing v = find(id);
        if (!isAdmin && !callerId.equals(v.getBuyerId()) && !callerId.equals(v.getSellerId())) {
            throw new ForbiddenException("Only the buyer, seller, or an admin can complete this viewing");
        }
        if (v.getStatus() == ViewingStatus.CANCELLED)
            throw new RuntimeException("Cannot complete a cancelled viewing");
        if (v.getStatus() == ViewingStatus.COMPLETED)
            throw new RuntimeException("Viewing already completed");
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

    private static final java.time.Duration BUYER_FREE_CANCEL_WINDOW = java.time.Duration.ofHours(2);

    public ViewingResponse cancel(UUID id, UUID userId, String reason, String clientIp) {
        Viewing v = find(id);
        if (!v.getBuyerId().equals(userId) && !v.getSellerId().equals(userId))
            throw new ForbiddenException("Access denied");
        if (v.getStatus() == ViewingStatus.COMPLETED)
            throw new RuntimeException("Cannot cancel completed viewing");
        if (v.getStatus() == ViewingStatus.CANCELLED || v.getStatus() == ViewingStatus.NO_SHOW)
            throw new RuntimeException("Viewing already " + v.getStatus());
        if (v.getStatus() == ViewingStatus.CONFIRMED && LocalDateTime.now().isAfter(v.getScheduledAt()))
            throw new RuntimeException("The scheduled viewing time has passed. Report a no-show instead of cancelling.");

        boolean feePaid = "COMPLETED".equals(v.getViewingFeeStatus());
        boolean sellerCancelling = v.getSellerId().equals(userId);
        boolean withinFreeCancelWindow = LocalDateTime.now().plus(BUYER_FREE_CANCEL_WINDOW).isBefore(v.getScheduledAt());
        boolean refundDue = feePaid && (sellerCancelling || withinFreeCancelWindow);

        String prevStatus = v.getStatus().name();
        v.setStatus(ViewingStatus.CANCELLED);
        v.setCancelledBy(userId);
        v.setCancellationReason(reason);

        if (refundDue) {
            boolean refunded = paymentClient.refundViewingFee(v.getViewingFeePaymentId(),
                    "Viewing cancelled by " + (sellerCancelling ? "seller" : "buyer") + ": " + reason);
            if (refunded) {
                v.setViewingFeeStatus("REFUNDED");
            } else {
                audit(id, "VIEWING_FEE_REFUND_FAILED", prevStatus, ViewingStatus.CANCELLED.name(),
                        userId, "USER", clientIp,
                        "ALERT: automatic refund failed for paymentId=" + v.getViewingFeePaymentId()
                                + ". Manual intervention required.");
            }
        }

        v = repo.save(v);
        audit(id, "VIEWING_CANCELLED", prevStatus, ViewingStatus.CANCELLED.name(),
                userId, "USER", clientIp,
                "Cancelled. reason=" + reason + " feeRefunded=" + refundDue);
        return toResponse(v);
    }

    public ViewingResponse markNoShow(UUID id, UUID callerId, boolean isAdmin, String reason, String clientIp) {
        Viewing v = find(id);
        if (!isAdmin && !callerId.equals(v.getBuyerId()) && !callerId.equals(v.getSellerId()))
            throw new ForbiddenException("Only the buyer, seller, or an admin can report a no-show");
        if (v.getStatus() != ViewingStatus.CONFIRMED)
            throw new RuntimeException("Can only report a no-show for a confirmed viewing. Current status: " + v.getStatus());
        if (LocalDateTime.now().isBefore(v.getScheduledAt()))
            throw new RuntimeException("Cannot report a no-show before the scheduled viewing time");

        String prevStatus = v.getStatus().name();
        v.setStatus(ViewingStatus.NO_SHOW);
        Viewing saved = repo.saveAndFlush(v);
        String actorRole = isAdmin ? "ADMIN" : callerId.equals(v.getBuyerId()) ? "BUYER" : "SELLER";
        audit(id, "VIEWING_NO_SHOW", prevStatus, ViewingStatus.NO_SHOW.name(),
                callerId, actorRole, clientIp, "No-show reported. Reason=" + reason);
        return toResponse(saved);
    }

    public boolean hasCompletedViewing(UUID propertyId, UUID buyerId) {
        return repo.existsByPropertyIdAndBuyerIdAndStatus(propertyId, buyerId, ViewingStatus.COMPLETED);
    }

    @Transactional(readOnly = true)
    public java.util.Optional<ViewingResponse> getActiveForBuyerAndProperty(UUID buyerId, UUID propertyId) {
        return repo.findFirstByPropertyIdAndBuyerIdAndStatusInOrderByCreatedAtDesc(
                propertyId, buyerId, ACTIVE_STATUSES).map(this::toResponse);
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
        return repo.findById(id).orElseThrow(() -> new NotFoundException("Viewing not found"));
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
                .cancelledBy(v.getCancelledBy())
                .cancellationReason(v.getCancellationReason())
                .createdAt(v.getCreatedAt()).updatedAt(v.getUpdatedAt())
                .build();
    }
}
