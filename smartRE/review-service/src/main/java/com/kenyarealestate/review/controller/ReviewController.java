package com.kenyarealestate.review.controller;

import com.kenyarealestate.review.dto.*;
import com.kenyarealestate.review.security.JwtUtil;
import com.kenyarealestate.review.service.ReviewService;
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
@RequestMapping("/api/reviews")
public class ReviewController {

    private final ReviewService svc;
    private final JwtUtil jwtUtil;

    public ReviewController(ReviewService svc, JwtUtil jwtUtil) {
        this.svc = svc;
        this.jwtUtil = jwtUtil;
    }

    @PostMapping
    public ResponseEntity<ReviewResponse> create(
            @Valid @RequestBody CreateReviewRequest req, HttpServletRequest r) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(svc.create(resolveUserId(r), req, getClientIp(r)));
    }

    @GetMapping("/property/{propertyId}")
    public ResponseEntity<Page<ReviewResponse>> byProperty(
            @PathVariable UUID propertyId,
            @RequestParam(defaultValue = "0")  @Min(0)       int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "DESC") String direction) {
        Sort.Direction dir = "ASC".equalsIgnoreCase(direction) ? Sort.Direction.ASC : Sort.Direction.DESC;
        return ResponseEntity.ok(svc.getByProperty(propertyId, PageRequest.of(page, size, Sort.by(dir, sortBy))));
    }

    @GetMapping("/seller/{sellerId}")
    public ResponseEntity<Page<ReviewResponse>> bySeller(
            @PathVariable UUID sellerId,
            @RequestParam(defaultValue = "0")  @Min(0)       int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "DESC") String direction) {
        Sort.Direction dir = "ASC".equalsIgnoreCase(direction) ? Sort.Direction.ASC : Sort.Direction.DESC;
        return ResponseEntity.ok(svc.getBySeller(sellerId, PageRequest.of(page, size, Sort.by(dir, sortBy))));
    }

    @GetMapping("/seller/{sellerId}/rating")
    public ResponseEntity<SellerRatingResponse> sellerRating(@PathVariable UUID sellerId) {
        return ResponseEntity.ok(svc.getSellerRating(sellerId));
    }

    @GetMapping("/my")
    public ResponseEntity<Page<ReviewResponse>> myReviews(
            HttpServletRequest r,
            @RequestParam(defaultValue = "0")  @Min(0)       int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "DESC") String direction) {
        Sort.Direction dir = "ASC".equalsIgnoreCase(direction) ? Sort.Direction.ASC : Sort.Direction.DESC;
        return ResponseEntity.ok(svc.getByReviewer(resolveUserId(r), PageRequest.of(page, size, Sort.by(dir, sortBy))));
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
