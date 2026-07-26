package com.kenyarealestate.property.controller;

import com.kenyarealestate.property.dto.*;
import com.kenyarealestate.property.security.JwtUtil;
import com.kenyarealestate.property.service.PropertyService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.data.domain.*;
import org.springframework.http.*;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Validated
@RestController
@RequestMapping("/api/properties")
public class PropertyController {

    private final PropertyService svc;
    private final JwtUtil jwtUtil;

    public PropertyController(PropertyService svc, JwtUtil jwtUtil) {
        this.svc = svc;
        this.jwtUtil = jwtUtil;
    }

    @PostMapping
    public ResponseEntity<PropertyResponse> create(
            @Valid @RequestBody CreatePropertyRequest req, HttpServletRequest httpReq) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(svc.create(resolveUserId(httpReq), req));
    }

    @PutMapping("/{id}")
    @org.springframework.security.access.prepost.PreAuthorize("hasAnyRole('SELLER','AGENT')")
    public ResponseEntity<PropertyResponse> update(
            @PathVariable UUID id,
            @Valid @RequestBody UpdatePropertyRequest req,
            HttpServletRequest httpReq) {
        return ResponseEntity.ok(svc.update(id, resolveUserId(httpReq), req));
    }

    @DeleteMapping("/{id}")
    @org.springframework.security.access.prepost.PreAuthorize("hasAnyRole('SELLER','AGENT')")
    public ResponseEntity<Void> delete(@PathVariable UUID id, HttpServletRequest httpReq) {
        svc.delete(id, resolveUserId(httpReq));
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{id}")
    public ResponseEntity<PropertyResponse> getById(@PathVariable UUID id, HttpServletRequest httpReq) {
        return ResponseEntity.ok(svc.getById(id, resolveUserIdOrNull(httpReq), httpReq.isUserInRole("ADMIN")));
    }

    @GetMapping("/seller/{sellerId}")
    public ResponseEntity<List<PropertyResponse>> bySeller(@PathVariable UUID sellerId) {
        return ResponseEntity.ok(svc.getActiveBySeller(sellerId));
    }

    @GetMapping("/search")
    public ResponseEntity<Page<PropertyResponse>> search(
            @RequestParam(required = false) String county,
            @RequestParam(required = false) String city,
            @RequestParam(required = false) String propertyType,
            @RequestParam(required = false) String listingType,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) BigDecimal minPrice,
            @RequestParam(required = false) BigDecimal maxPrice,
            @RequestParam(required = false) Integer minBedrooms,
            @RequestParam(defaultValue = "false") boolean verifiedOnly,
            @RequestParam(defaultValue = "0")  @Min(0)       int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(1000) int size,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "DESC") String direction) {

        PropertySearchRequest req = new PropertySearchRequest();
        req.setCounty(county); req.setCity(city);
        req.setPropertyType(propertyType); req.setListingType(listingType);
        req.setKeyword(keyword); req.setMinPrice(minPrice); req.setMaxPrice(maxPrice);
        req.setMinBedrooms(minBedrooms); req.setVerifiedOnly(verifiedOnly);
        req.setPage(page); req.setSize(size);
        req.setSortBy(sortBy); req.setDirection(direction);
        return ResponseEntity.ok(svc.search(req));
    }

    @GetMapping("/my")
    public ResponseEntity<Page<PropertyResponse>> myListings(
            HttpServletRequest httpReq,
            @RequestParam(defaultValue = "0")  @Min(0)       int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "DESC") String direction) {
        Sort.Direction dir = direction.equalsIgnoreCase("ASC") ? Sort.Direction.ASC : Sort.Direction.DESC;
        return ResponseEntity.ok(svc.getMyListings(resolveUserId(httpReq),
                PageRequest.of(page, size, Sort.by(dir, sortBy))));
    }

    @GetMapping("/admin/all")
    public ResponseEntity<Page<PropertyResponse>> adminAll(
            @RequestParam(required = false) com.kenyarealestate.property.entity.ListingStatus status,
            @RequestParam(defaultValue = "0")  @Min(0)       int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(1000) int size,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "DESC") String direction) {
        Sort.Direction dir = direction.equalsIgnoreCase("ASC") ? Sort.Direction.ASC : Sort.Direction.DESC;
        return ResponseEntity.ok(svc.adminGetAll(status, PageRequest.of(page, size, Sort.by(dir, sortBy))));
    }

    @PutMapping("/admin/{id}/suspend")
    public ResponseEntity<PropertyResponse> adminSuspend(@PathVariable UUID id, HttpServletRequest httpReq) {
        return ResponseEntity.ok(svc.adminSuspend(id, resolveUserId(httpReq)));
    }

    @PutMapping("/admin/{id}/reactivate")
    public ResponseEntity<PropertyResponse> adminReactivate(@PathVariable UUID id, HttpServletRequest httpReq) {
        return ResponseEntity.ok(svc.adminReactivate(id, resolveUserId(httpReq)));
    }

    @PutMapping("/internal/seller/{sellerId}/activate-all")
    public ResponseEntity<Void> activateAll(@PathVariable UUID sellerId) {
        svc.activateAllForSeller(sellerId);
        return ResponseEntity.ok().build();
    }

    @PutMapping("/internal/{propertyId}/mark-ownership-verified")
    public ResponseEntity<Void> markOwnership(
            @PathVariable UUID propertyId,
            @RequestParam(required = false) String parcelNumber,
            @RequestParam(required = false) String titleDeedNumber) {
        svc.markOwnershipVerified(propertyId, parcelNumber, titleDeedNumber);
        return ResponseEntity.ok().build();
    }

    private UUID resolveUserId(HttpServletRequest req) {
        UUID fromAttr = (UUID) req.getAttribute("authenticatedUserId");
        if (fromAttr != null) return fromAttr;
        String h = req.getHeader("Authorization");
        if (h != null && h.startsWith("Bearer ")) return jwtUtil.extractUserId(h.substring(7));
        throw new RuntimeException("Cannot resolve user identity");
    }

    private UUID resolveUserIdOrNull(HttpServletRequest req) {
        try {
            return resolveUserId(req);
        } catch (RuntimeException e) {
            return null;
        }
    }
}
