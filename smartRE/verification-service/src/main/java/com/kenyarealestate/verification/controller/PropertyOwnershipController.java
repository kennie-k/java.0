package com.kenyarealestate.verification.controller;

import com.kenyarealestate.verification.dto.ownership.*;
import com.kenyarealestate.verification.enums.OwnershipVerificationStatus;
import com.kenyarealestate.verification.security.JwtUtil;
import com.kenyarealestate.verification.service.PropertyOwnershipVerificationService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.data.domain.*;
import org.springframework.http.*;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api/verification/ownership")
public class PropertyOwnershipController {

    private final PropertyOwnershipVerificationService service;
    private final JwtUtil jwtUtil;

    public PropertyOwnershipController(PropertyOwnershipVerificationService service, JwtUtil jwtUtil) {
        this.service = service;
        this.jwtUtil = jwtUtil;
    }

    @PostMapping("/start")
    public ResponseEntity<OwnershipVerificationResponse> start(
            HttpServletRequest httpReq, @Valid @RequestBody StartOwnershipVerificationRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(service.startOwnershipVerification(resolveUserId(httpReq), req));
    }

    @PostMapping("/{verificationId}/documents")
    public ResponseEntity<OwnershipVerificationResponse> uploadDocument(
            HttpServletRequest httpReq,
            @PathVariable UUID verificationId,
            @Valid @RequestBody UploadOwnershipDocumentRequest req) {
        return ResponseEntity.ok(service.uploadDocument(resolveUserId(httpReq), verificationId, req));
    }

    @PostMapping("/{verificationId}/submit")
    public ResponseEntity<OwnershipVerificationResponse> submit(
            HttpServletRequest httpReq, @PathVariable UUID verificationId) {
        return ResponseEntity.ok(service.submitForReview(resolveUserId(httpReq), verificationId));
    }

    @GetMapping("/property/{propertyId}")
    public ResponseEntity<OwnershipVerificationResponse> getByProperty(@PathVariable UUID propertyId) {
        return ResponseEntity.ok(service.getByPropertyId(propertyId));
    }

    @GetMapping("/me")
    public ResponseEntity<List<OwnershipVerificationResponse>> getMyVerifications(HttpServletRequest httpReq) {
        return ResponseEntity.ok(service.getByUserId(resolveUserId(httpReq)));
    }

    @PostMapping("/internal/{verificationId}/ai-screening")
    public ResponseEntity<OwnershipVerificationResponse> aiScreening(
            @PathVariable UUID verificationId, @RequestBody OwnershipDocumentLegalCheckRequest req) {
        return ResponseEntity.ok(service.processAiScreening(verificationId, req));
    }

    @GetMapping("/admin/queue")
    public ResponseEntity<Page<OwnershipVerificationResponse>> adminQueue(
            @RequestParam(defaultValue = "HUMAN_REVIEW") OwnershipVerificationStatus status,
            @RequestParam(defaultValue = "0")  int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "ASC") String direction) {
        Sort.Direction dir = "DESC".equalsIgnoreCase(direction)
                ? Sort.Direction.DESC : Sort.Direction.ASC;
        return ResponseEntity.ok(service.getQueue(status,
                PageRequest.of(page, size, Sort.by(dir, sortBy))));
    }

    @PutMapping("/admin/{verificationId}/ministry-check")
    public ResponseEntity<OwnershipVerificationResponse> ministryCheck(
            @PathVariable UUID verificationId,
            @RequestParam boolean ministryConfirmed,
            @RequestParam(required = false) String notes,
            HttpServletRequest httpReq) {
        return ResponseEntity.ok(service.recordMinistryCheck(
                verificationId, ministryConfirmed, resolveUserId(httpReq), notes));
    }

    @PutMapping("/admin/{verificationId}/encumbrance-check")
    public ResponseEntity<OwnershipVerificationResponse> encumbranceCheck(
            @PathVariable UUID verificationId,
            @RequestParam boolean encumbranceClear,
            @RequestParam(required = false) String notes,
            HttpServletRequest httpReq) {
        return ResponseEntity.ok(service.recordEncumbranceCheck(
                verificationId, encumbranceClear, resolveUserId(httpReq), notes));
    }

    @PutMapping("/admin/{verificationId}/legal-check")
    public ResponseEntity<OwnershipVerificationResponse> legalCheck(
            @PathVariable UUID verificationId,
            @RequestBody OwnershipDocumentLegalCheckRequest req,
            HttpServletRequest httpReq) {
        return ResponseEntity.ok(service.submitLegalCheckResults(
                verificationId, req, resolveUserId(httpReq)));
    }

    @PutMapping("/admin/{verificationId}/final-decision")
    public ResponseEntity<OwnershipVerificationResponse> finalDecision(
            @PathVariable UUID verificationId,
            @Valid @RequestBody AdminOwnershipReviewRequest req,
            HttpServletRequest httpReq) {
        return ResponseEntity.ok(service.adminFinalDecision(
                verificationId, req, resolveUserId(httpReq), extractToken(httpReq)));
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
