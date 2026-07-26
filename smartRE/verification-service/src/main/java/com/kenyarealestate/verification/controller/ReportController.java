package com.kenyarealestate.verification.controller;

import com.kenyarealestate.verification.dto.report.*;
import com.kenyarealestate.verification.enums.ReportStatus;
import com.kenyarealestate.verification.security.JwtUtil;
import com.kenyarealestate.verification.service.ReportService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/verification/reports")
public class ReportController {

    private final ReportService service;
    private final JwtUtil jwtUtil;

    public ReportController(ReportService service, JwtUtil jwtUtil) {
        this.service = service;
        this.jwtUtil = jwtUtil;
    }

    @PostMapping
    public ResponseEntity<ReportResponse> create(
            HttpServletRequest httpReq, @Valid @RequestBody CreateReportRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(service.create(resolveUserId(httpReq), req));
    }

    @GetMapping("/mine")
    public ResponseEntity<Page<ReportResponse>> mine(
            HttpServletRequest httpReq,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(service.getMine(resolveUserId(httpReq),
                PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"))));
    }

    @GetMapping("/admin/queue")
    public ResponseEntity<Page<ReportResponse>> adminQueue(
            @RequestParam(defaultValue = "OPEN") ReportStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "ASC") String direction) {
        Sort.Direction dir = "DESC".equalsIgnoreCase(direction) ? Sort.Direction.DESC : Sort.Direction.ASC;
        return ResponseEntity.ok(service.getQueue(status, PageRequest.of(page, size, Sort.by(dir, sortBy))));
    }

    @PutMapping("/admin/{reportId}/resolve")
    public ResponseEntity<ReportResponse> resolve(
            @PathVariable UUID reportId,
            @Valid @RequestBody AdminReportResolveRequest req,
            HttpServletRequest httpReq) {
        return ResponseEntity.ok(service.resolve(reportId, req, resolveUserId(httpReq)));
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
