package com.kenyarealestate.verification.controller;

import com.kenyarealestate.verification.dto.identity.*;
import com.kenyarealestate.verification.enums.IdentityVerificationStatus;
import com.kenyarealestate.verification.security.JwtUtil;
import com.kenyarealestate.verification.service.SellerIdentityVerificationService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.data.domain.*;
import org.springframework.http.*;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/verification/identity")
public class SellerIdentityController {

    private final SellerIdentityVerificationService service;
    private final JwtUtil jwtUtil;

    public SellerIdentityController(SellerIdentityVerificationService service, JwtUtil jwtUtil) {
        this.service = service;
        this.jwtUtil = jwtUtil;
    }

    @PostMapping("/start")
    public ResponseEntity<IdentityVerificationResponse> start(HttpServletRequest httpReq) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(service.startVerification(resolveUserId(httpReq)));
    }

    @PostMapping("/documents")
    public ResponseEntity<IdentityVerificationResponse> uploadDocument(
            HttpServletRequest httpReq, @Valid @RequestBody UploadIdentityDocumentRequest req) {
        return ResponseEntity.ok(service.uploadDocument(resolveUserId(httpReq), req));
    }

    @PostMapping("/submit")
    public ResponseEntity<IdentityVerificationResponse> submit(HttpServletRequest httpReq) {
        return ResponseEntity.ok(service.submitForReview(resolveUserId(httpReq)));
    }

    @GetMapping("/me")
    public ResponseEntity<IdentityVerificationResponse> getMyVerification(HttpServletRequest httpReq) {
        return ResponseEntity.ok(service.getMyVerification(resolveUserId(httpReq)));
    }

    @PostMapping("/internal/{verificationId}/ai-screening")
    public ResponseEntity<IdentityVerificationResponse> aiScreening(
            @PathVariable UUID verificationId, @RequestBody AiScreeningRequest req) {
        return ResponseEntity.ok(service.processAiScreening(verificationId, req));
    }

    @GetMapping("/admin/queue")
    public ResponseEntity<Page<IdentityVerificationResponse>> adminQueue(
            @RequestParam(defaultValue = "HUMAN_REVIEW") IdentityVerificationStatus status,
            @RequestParam(defaultValue = "0")  int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "ASC") String direction) {
        org.springframework.data.domain.Sort.Direction dir =
                "DESC".equalsIgnoreCase(direction)
                ? org.springframework.data.domain.Sort.Direction.DESC
                : org.springframework.data.domain.Sort.Direction.ASC;
        return ResponseEntity.ok(service.getQueue(status,
                PageRequest.of(page, size, org.springframework.data.domain.Sort.by(dir, sortBy))));
    }

    @PutMapping("/admin/{verificationId}/review")
    public ResponseEntity<IdentityVerificationResponse> adminReview(
            @PathVariable UUID verificationId,
            @Valid @RequestBody AdminIdentityReviewRequest req,
            HttpServletRequest httpReq) {
        UUID adminId = resolveUserId(httpReq);
        String jwt = extractToken(httpReq);
        return ResponseEntity.ok(service.adminReview(verificationId, req, adminId, jwt));
    }

    private UUID resolveUserId(HttpServletRequest request) {
        UUID fromAttr = (UUID) request.getAttribute("authenticatedUserId");
        if (fromAttr != null) return fromAttr;
        String token = extractToken(request);
        if (token != null) return jwtUtil.extractUserId(token);
        throw new com.kenyarealestate.verification.exception.VerificationException(
                "Could not resolve authenticated user identity");
    }

    private String extractToken(HttpServletRequest r) {
        String h = r.getHeader("Authorization");
        return (StringUtils.hasText(h) && h.startsWith("Bearer ")) ? h.substring(7) : null;
    }
}
