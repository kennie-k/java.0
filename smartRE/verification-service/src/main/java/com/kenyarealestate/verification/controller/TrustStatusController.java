package com.kenyarealestate.verification.controller;

import com.kenyarealestate.verification.dto.TrustStatusResponse;
import com.kenyarealestate.verification.service.TrustStatusService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/verification/trust-status")
public class TrustStatusController {

    private final TrustStatusService service;

    public TrustStatusController(TrustStatusService service) {
        this.service = service;
    }

    @GetMapping("/{userId}")
    public ResponseEntity<TrustStatusResponse> getIdentityStatus(@PathVariable UUID userId) {
        return ResponseEntity.ok(service.getIdentityOnlyStatus(userId));
    }

    @GetMapping("/{userId}/property/{propertyId}")
    public ResponseEntity<TrustStatusResponse> getFullTrustStatus(
            @PathVariable UUID userId, @PathVariable UUID propertyId) {
        return ResponseEntity.ok(service.getTrustStatus(userId, propertyId));
    }
}
