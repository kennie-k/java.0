package com.kenyarealestate.viewing.controller;

import com.kenyarealestate.viewing.dto.*;
import com.kenyarealestate.viewing.security.JwtUtil;
import com.kenyarealestate.viewing.service.ViewingService;
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
public class ViewingController {

    private final ViewingService svc;
    private final JwtUtil jwtUtil;

    public ViewingController(ViewingService svc, JwtUtil jwtUtil) {
        this.svc = svc;
        this.jwtUtil = jwtUtil;
    }

    @PostMapping
    public ResponseEntity<ViewingResponse> schedule(
            @Valid @RequestBody ScheduleViewingRequest req, HttpServletRequest r) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(svc.schedule(resolveUserId(r), req, getClientIp(r)));
    }

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

    @PutMapping("/{id}/confirm-seller")
    public ResponseEntity<ViewingResponse> confirmSeller(@PathVariable UUID id, HttpServletRequest r) {
        return ResponseEntity.ok(svc.confirmSeller(id, resolveUserId(r), getClientIp(r)));
    }

    @PutMapping("/{id}/confirm-buyer")
    public ResponseEntity<ViewingResponse> confirmBuyer(@PathVariable UUID id, HttpServletRequest r) {
        return ResponseEntity.ok(svc.confirmBuyer(id, resolveUserId(r), getClientIp(r)));
    }

    @PutMapping("/{id}/complete")
    public ResponseEntity<ViewingResponse> complete(@PathVariable UUID id, HttpServletRequest r) {
        return ResponseEntity.ok(svc.markCompleted(id, resolveUserId(r), r.isUserInRole("ADMIN")));
    }

    @PutMapping("/{id}/cancel")
    public ResponseEntity<ViewingResponse> cancel(
            @PathVariable UUID id, HttpServletRequest r,
            @RequestParam(required = false) String reason) {
        return ResponseEntity.ok(svc.cancel(id, resolveUserId(r), reason, getClientIp(r)));
    }

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
        throw new RuntimeException("Cannot resolve user identity");
    }

    private String getClientIp(HttpServletRequest req) {
        String xff = req.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) return xff.split(",")[0].trim();
        String realIp = req.getHeader("X-Real-IP");
        if (realIp != null && !realIp.isBlank()) return realIp;
        return req.getRemoteAddr();
    }
}
