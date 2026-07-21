package com.kenyarealestate.payment.controller;

import com.kenyarealestate.payment.dto.*;
import com.kenyarealestate.payment.entity.MpesaRawCallback;
import com.kenyarealestate.payment.repository.MpesaRawCallbackRepository;
import com.kenyarealestate.payment.security.JwtUtil;
import com.kenyarealestate.payment.service.PaymentAuditService;
import com.kenyarealestate.payment.service.ReceiptService;
import com.kenyarealestate.payment.service.RevenueService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@Slf4j
@Validated
@RestController
@RequestMapping("/api/revenue")
public class RevenueController {

    private final RevenueService svc;
    private final JwtUtil jwtUtil;
    private final PaymentAuditService auditService;
    private final ReceiptService receiptService;
    private final MpesaRawCallbackRepository rawCallbackRepo;

    @org.springframework.beans.factory.annotation.Value("${mpesa.callback-secret}")
    private String callbackSecret;

    public RevenueController(RevenueService svc, JwtUtil jwtUtil,
                              PaymentAuditService auditService,
                              ReceiptService receiptService,
                              MpesaRawCallbackRepository rawCallbackRepo) {
        this.svc = svc;
        this.jwtUtil = jwtUtil;
        this.auditService = auditService;
        this.receiptService = receiptService;
        this.rawCallbackRepo = rawCallbackRepo;
    }

    @GetMapping("/summary")
    public ResponseEntity<RevenueSummaryResponse> summary() {
        return ResponseEntity.ok(svc.getSummary());
    }

    @GetMapping
    public ResponseEntity<Page<RevenueResponse>> all(
            @RequestParam(defaultValue = "0")  @Min(0)       int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return ResponseEntity.ok(svc.getAll(PageRequest.of(page, size)));
    }

    @PutMapping("/payments/{id}/release-escrow")
    public ResponseEntity<RevenueResponse> releaseEscrow(
            @PathVariable UUID id,
            @Valid @RequestBody ReleaseEscrowRequest req,
            HttpServletRequest r) {
        return ResponseEntity.ok(
                svc.releaseEscrowWithPayout(id, resolveUserId(r), getClientIp(r), req));
    }

    @PutMapping("/payments/{id}/refund")
    public ResponseEntity<Void> refund(
            @PathVariable UUID id,
            @Valid @RequestBody RefundRequest req,
            HttpServletRequest r) {
        svc.rejectAndRefund(id, resolveUserId(r), getClientIp(r), req.getReason());
        return ResponseEntity.ok().build();
    }

    @GetMapping("/payments/{id}/audit")
    public ResponseEntity<List<PaymentAuditResponse>> revenueAudit(@PathVariable UUID id) {
        return ResponseEntity.ok(auditService.getRevenueAuditTrail(id));
    }

    @GetMapping("/receipts/{receiptNumber}")
    public ResponseEntity<PaymentReceiptResponse> receiptByNumber(@PathVariable String receiptNumber) {
        return ResponseEntity.ok(receiptService.getByReceiptNumber(receiptNumber));
    }

    @GetMapping("/payments/{id}/receipt")
    public ResponseEntity<PaymentReceiptResponse> receiptByPayment(@PathVariable UUID id) {
        return ResponseEntity.ok(receiptService.getByPaymentId(id));
    }

    @GetMapping("/callbacks/raw")
    public ResponseEntity<List<MpesaRawCallback>> rawCallbacks(
            @RequestParam(required = false) UUID paymentId) {
        if (paymentId != null) {
            return ResponseEntity.ok(rawCallbackRepo.findByPaymentIdOrderByReceivedAtAsc(paymentId));
        }
        return ResponseEntity.ok(rawCallbackRepo.findAll());
    }

    @PostMapping("/mpesa/b2c/callback/{secret}")
    public ResponseEntity<Void> b2cCallback(@PathVariable String secret, @RequestBody String rawBody) {
        if (!callbackSecret.equals(secret)) {
            log.warn("B2C callback received with invalid secret");
            return ResponseEntity.status(org.springframework.http.HttpStatus.FORBIDDEN).build();
        }
        try {
            com.fasterxml.jackson.databind.ObjectMapper om = new com.fasterxml.jackson.databind.ObjectMapper();
            B2cCallbackRequest req = om.readValue(rawBody, B2cCallbackRequest.class);
            var result = req.getResult();
            boolean success = result.getResultCode() != null && result.getResultCode() == 0;
            String receipt = null;
            if (success && result.getResultParameters() != null) {
                receipt = result.getResultParameters().getResultParameter().stream()
                        .filter(p -> "TransactionReceipt".equals(p.getKey()))
                        .map(p -> String.valueOf(p.getValue()))
                        .findFirst().orElse(null);
            }

            rawCallbackRepo.save(MpesaRawCallback.builder()
                    .callbackType("B2C")
                    .originatorConversationId(result.getOriginatorConversationId())
                    .resultCode(result.getResultCode())
                    .resultDesc(result.getResultDesc())
                    .rawPayload(rawBody)
                    .build());

            svc.handleB2cCallback(
                    result.getOriginatorConversationId(),
                    rawBody,
                    success,
                    receipt,
                    success ? null : result.getResultDesc(),
                    null);
        } catch (Exception e) {
            log.error("B2C callback error: {}", e.getMessage(), e);
        }
        return ResponseEntity.ok().build();
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
