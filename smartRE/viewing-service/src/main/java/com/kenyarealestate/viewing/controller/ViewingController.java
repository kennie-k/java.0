package com.kenyarealestate.viewing.controller;

import com.kenyarealestate.viewing.dto.*;
import com.kenyarealestate.viewing.exception.UnauthorizedException;
import com.kenyarealestate.viewing.security.JwtUtil;
import com.kenyarealestate.viewing.service.ViewingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.data.domain.*;
import org.springframework.http.*;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@Validated
@RestController
@RequestMapping("/api/viewings")
@Tag(name = "Viewings", description = "Scheduling property viewings and the buyer/seller confirmation, cancellation, and no-show flow")
public class ViewingController {

    private final ViewingService svc;
    private final JwtUtil jwtUtil;

    public ViewingController(ViewingService svc, JwtUtil jwtUtil) {
        this.svc = svc;
        this.jwtUtil = jwtUtil;
    }

    @Operation(summary = "Schedule a viewing", description = "Buyer only. Verifies the seller has completed identity verification and that the given sellerId matches the property's registered owner, then triggers an M-Pesa viewing-fee STK push. Rejects (409) if the buyer already has an active viewing for this property, or if the requested time slot is already booked for this property.")
    @PostMapping
    public ResponseEntity<ViewingResponse> schedule(
            @Valid @RequestBody ScheduleViewingRequest req, HttpServletRequest r) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(svc.schedule(resolveUserId(r), req, getClientIp(r)));
    }

    @Operation(summary = "List the current user's viewings as a buyer")
    @GetMapping("/my/buyer")
    public ResponseEntity<Page<ViewingResponse>> myBuyer(
            HttpServletRequest r,
            @RequestParam(defaultValue = "0")  @Min(0)       int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
            @RequestParam(defaultValue = "scheduledAt") String sortBy,
            @RequestParam(defaultValue = "DESC") String direction) {
        Sort.Direction dir = "ASC".equalsIgnoreCase(direction) ? Sort.Direction.ASC : Sort.Direction.DESC;
        return ResponseEntity.ok(svc.myBuyer(resolveUserId(r), PageRequest.of(page, size, Sort.by(dir, sortBy))));
    }

    @Operation(summary = "Get the current buyer's active viewing for a property, if any")
    @GetMapping("/my/buyer/property/{propertyId}")
    public ResponseEntity<ViewingResponse> myActiveForProperty(
            @PathVariable UUID propertyId, HttpServletRequest r) {
        return svc.getActiveForBuyerAndProperty(resolveUserId(r), propertyId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @Operation(summary = "List the current user's viewings as a seller")
    @GetMapping("/my/seller")
    public ResponseEntity<Page<ViewingResponse>> mySeller(
            HttpServletRequest r,
            @RequestParam(defaultValue = "0")  @Min(0)       int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
            @RequestParam(defaultValue = "scheduledAt") String sortBy,
            @RequestParam(defaultValue = "DESC") String direction) {
        Sort.Direction dir = "ASC".equalsIgnoreCase(direction) ? Sort.Direction.ASC : Sort.Direction.DESC;
        return ResponseEntity.ok(svc.mySeller(resolveUserId(r), PageRequest.of(page, size, Sort.by(dir, sortBy))));
    }

    @Operation(summary = "Seller confirms a viewing")
    @PutMapping("/{id}/confirm-seller")
    public ResponseEntity<ViewingResponse> confirmSeller(@PathVariable UUID id, HttpServletRequest r) {
        return ResponseEntity.ok(svc.confirmSeller(id, resolveUserId(r), getClientIp(r)));
    }

    @Operation(summary = "Buyer confirms a viewing")
    @PutMapping("/{id}/confirm-buyer")
    public ResponseEntity<ViewingResponse> confirmBuyer(@PathVariable UUID id, HttpServletRequest r) {
        return ResponseEntity.ok(svc.confirmBuyer(id, resolveUserId(r), getClientIp(r)));
    }

    @Operation(summary = "Mark a viewing completed", description = "Buyer, seller, or admin. Requires both parties to have already confirmed.")
    @PutMapping("/{id}/complete")
    public ResponseEntity<ViewingResponse> complete(@PathVariable UUID id, HttpServletRequest r) {
        return ResponseEntity.ok(svc.markCompleted(id, resolveUserId(r), r.isUserInRole("ADMIN")));
    }

    @Operation(summary = "Cancel a viewing", description = "Buyer or seller. May trigger an automatic viewing-fee refund depending on who is cancelling and how far ahead of the scheduled time.")
    @PutMapping("/{id}/cancel")
    public ResponseEntity<ViewingResponse> cancel(
            @PathVariable UUID id, HttpServletRequest r,
            @RequestParam(required = false) String reason) {
        return ResponseEntity.ok(svc.cancel(id, resolveUserId(r), reason, getClientIp(r)));
    }

    @Operation(summary = "Report a no-show", description = "Buyer, seller, or admin. Only valid for a CONFIRMED viewing after its scheduled time has passed.")
    @PutMapping("/{id}/no-show")
    public ResponseEntity<ViewingResponse> noShow(
            @PathVariable UUID id, HttpServletRequest r,
            @RequestParam(required = false) String reason) {
        return ResponseEntity.ok(svc.markNoShow(id, resolveUserId(r), r.isUserInRole("ADMIN"), reason, getClientIp(r)));
    }

    @Operation(summary = "Internal: check whether a buyer has a completed viewing for a property", description = "Called by review-service to gate whether a review can be posted. No user auth — internal service-to-service call.")
    @GetMapping("/internal/check-completed")
    public ResponseEntity<Boolean> checkCompleted(
            @RequestParam UUID propertyId, @RequestParam UUID buyerId) {
        return ResponseEntity.ok(svc.hasCompletedViewing(propertyId, buyerId));
    }

    private UUID resolveUserId(HttpServletRequest req) {
        UUID u = (UUID) req.getAttribute("authenticatedUserId");
        if (u != null) return u;
        String h = req.getHeader("Authorization");
        if (h != null && h.startsWith("Bearer ")) return jwtUtil.extractUserId(h.substring(7));
        throw new UnauthorizedException("Cannot resolve user identity");
    }

    private String getClientIp(HttpServletRequest req) {
        String xff = req.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) return xff.split(",")[0].trim();
        String realIp = req.getHeader("X-Real-IP");
        if (realIp != null && !realIp.isBlank()) return realIp;
        return req.getRemoteAddr();
    }
}
