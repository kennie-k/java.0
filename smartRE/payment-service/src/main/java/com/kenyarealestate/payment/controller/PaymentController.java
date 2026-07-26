package com.kenyarealestate.payment.controller;

import com.kenyarealestate.payment.dto.*;
import com.kenyarealestate.payment.security.CallbackSecurity;
import com.kenyarealestate.payment.security.JwtUtil;
import com.kenyarealestate.payment.service.PaymentService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.*;
import org.springframework.http.*;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@Slf4j
@Validated
@RestController
@RequestMapping("/api/payments")
public class PaymentController {

    private final PaymentService svc;
    private final JwtUtil jwtUtil;

    @org.springframework.beans.factory.annotation.Value("${mpesa.callback-secret}")
    private String callbackSecret;

    @org.springframework.beans.factory.annotation.Value("${mpesa.callback-allowed-ips:}")
    private String callbackAllowedIps;

    public PaymentController(PaymentService svc, JwtUtil jwtUtil) {
        this.svc = svc;
        this.jwtUtil = jwtUtil;
    }

    @PostMapping("/initiate")
    public ResponseEntity<PaymentResponse> initiate(
            @Valid @RequestBody InitiatePaymentRequest req, HttpServletRequest r) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(svc.initiate(resolveUserId(r), req, getClientIp(r)));
    }

    @PostMapping("/mpesa/callback/{secret}")
    public ResponseEntity<Void> mpesaCallback(
            @PathVariable String secret, @RequestBody String rawBody, HttpServletRequest r) {
        String callerIp = getClientIp(r);
        if (!CallbackSecurity.secretMatches(secret, callbackSecret)) {
            log.warn("M-Pesa callback received with invalid secret from IP {}", callerIp);
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        if (!CallbackSecurity.ipAllowed(callerIp, callbackAllowedIps)) {
            log.warn("M-Pesa callback received from disallowed IP {}", callerIp);
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        try {
            com.fasterxml.jackson.databind.ObjectMapper om = new com.fasterxml.jackson.databind.ObjectMapper();
            MpesaCallbackRequest req = om.readValue(rawBody, MpesaCallbackRequest.class);
            svc.handleCallback(req, rawBody);
        } catch (Exception e) {
            log.error("M-Pesa callback error: {}", e.getMessage(), e);
        }
        return ResponseEntity.ok().build();
    }

    @GetMapping("/config")
    public ResponseEntity<java.util.Map<String, Object>> config() {
        return ResponseEntity.ok(java.util.Map.of(
                "viewingFeeKes", svc.getViewingFeeKes(),
                "profileAccessFeeKes", svc.getProfileAccessFeeKes(),
                "transactionCommissionPct", svc.getCommissionPct()
        ));
    }

    @GetMapping("/{id}")
    public ResponseEntity<PaymentResponse> getById(
            @PathVariable UUID id, HttpServletRequest r) {
        return ResponseEntity.ok(svc.getByIdAndBuyer(id, resolveUserId(r)));
    }

    @GetMapping("/my")
    public ResponseEntity<Page<PaymentResponse>> myPayments(
            HttpServletRequest r,
            @RequestParam(defaultValue = "0")  @Min(0)       int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "DESC") String direction) {
        Sort.Direction dir = "ASC".equalsIgnoreCase(direction) ? Sort.Direction.ASC : Sort.Direction.DESC;
        return ResponseEntity.ok(svc.getByBuyer(resolveUserId(r),
                PageRequest.of(page, size, Sort.by(dir, sortBy))));
    }

    @GetMapping("/admin/pending-release")
    public ResponseEntity<Page<PaymentResponse>> pendingRelease(
            @RequestParam(defaultValue = "0")  @Min(0)       int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return ResponseEntity.ok(svc.getPendingEscrowReview(page, size));
    }

    @GetMapping("/{id}/audit")
    public ResponseEntity<List<PaymentAuditResponse>> auditTrail(@PathVariable UUID id, HttpServletRequest r) {
        return ResponseEntity.ok(svc.getAuditTrail(id, resolveUserId(r)));
    }

    @GetMapping("/{id}/receipt")
    public ResponseEntity<PaymentReceiptResponse> receipt(
            @PathVariable UUID id, HttpServletRequest r) {
        return ResponseEntity.ok(svc.getReceipt(id, resolveUserId(r)));
    }

    @GetMapping("/profile-access/{sellerId}")
    public ResponseEntity<java.util.Map<String, Boolean>> checkProfileAccess(
            @PathVariable UUID sellerId, HttpServletRequest r) {
        boolean hasAccess = svc.hasProfileAccess(resolveUserId(r), sellerId);
        return ResponseEntity.ok(java.util.Map.of("hasAccess", hasAccess));
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
