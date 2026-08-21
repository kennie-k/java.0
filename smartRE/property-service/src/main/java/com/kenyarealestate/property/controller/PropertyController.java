package com.kenyarealestate.property.controller;

import com.kenyarealestate.property.dto.*;
import com.kenyarealestate.property.exception.UnauthorizedException;
import com.kenyarealestate.property.security.JwtUtil;
import com.kenyarealestate.property.service.PropertyService;
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

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Validated
@RestController
@RequestMapping("/api/properties")
@Tag(name = "Properties", description = "Property listing CRUD, public search, and admin moderation")
public class PropertyController {

    private final PropertyService svc;
    private final JwtUtil jwtUtil;

    public PropertyController(PropertyService svc, JwtUtil jwtUtil) {
        this.svc = svc;
        this.jwtUtil = jwtUtil;
    }

    @Operation(summary = "Create a property listing", description = "Seller/agent only. New listings start in DRAFT or PENDING_VERIFICATION depending on the seller's identity-verification status.")
    @PostMapping
    public ResponseEntity<PropertyResponse> create(
            @Valid @RequestBody CreatePropertyRequest req, HttpServletRequest httpReq) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(svc.create(resolveUserId(httpReq), req));
    }

    @Operation(summary = "Update a property listing", description = "Only the owning seller/agent may update their own listing.")
    @PutMapping("/{id}")
    @org.springframework.security.access.prepost.PreAuthorize("hasAnyRole('SELLER','AGENT')")
    public ResponseEntity<PropertyResponse> update(
            @PathVariable UUID id,
            @Valid @RequestBody UpdatePropertyRequest req,
            HttpServletRequest httpReq) {
        return ResponseEntity.ok(svc.update(id, resolveUserId(httpReq), req));
    }

    @Operation(summary = "Delete a property listing", description = "Only the owning seller/agent may delete their own listing.")
    @DeleteMapping("/{id}")
    @org.springframework.security.access.prepost.PreAuthorize("hasAnyRole('SELLER','AGENT')")
    public ResponseEntity<Void> delete(@PathVariable UUID id, HttpServletRequest httpReq) {
        svc.delete(id, resolveUserId(httpReq));
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Get a property by id", description = "Publicly readable when ACTIVE; non-active listings are only visible to their owner or an admin.")
    @GetMapping("/{id}")
    public ResponseEntity<PropertyResponse> getById(@PathVariable UUID id, HttpServletRequest httpReq) {
        return ResponseEntity.ok(svc.getById(id, resolveUserIdOrNull(httpReq),
                httpReq.isUserInRole("ADMIN"), getClientIp(httpReq)));
    }

    @Operation(summary = "List a seller's active listings")
    @GetMapping("/seller/{sellerId}")
    public ResponseEntity<List<PropertyResponse>> bySeller(@PathVariable UUID sellerId) {
        return ResponseEntity.ok(svc.getActiveBySeller(sellerId));
    }

    @Operation(summary = "Public property search", description = "Filters by county/city/type/keyword/price/bedrooms/verification status, with pagination and sorting.")
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

    @Operation(summary = "List the current user's own listings", description = "NOTE: geo-radius search (using the existing latitude/longitude columns) is a known feature gap, intentionally left out of this fix pass — it needs its own DTO/controller/index design rather than a quick patch here.")
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

    @Operation(summary = "Admin: list all listings", description = "Admin only. Optionally filtered by status.")
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

    @Operation(summary = "Admin: aggregate listing stats", description = "Counts by status/type/county, average active price, total views.")
    @GetMapping("/admin/stats")
    public ResponseEntity<com.kenyarealestate.property.dto.PropertyAdminStatsResponse> adminStats() {
        return ResponseEntity.ok(svc.getAdminStats());
    }

    @Operation(summary = "Admin: suspend a listing")
    @PutMapping("/admin/{id}/suspend")
    public ResponseEntity<PropertyResponse> adminSuspend(@PathVariable UUID id, HttpServletRequest httpReq) {
        return ResponseEntity.ok(svc.adminSuspend(id, resolveUserId(httpReq)));
    }

    @Operation(summary = "Admin: reactivate a suspended listing")
    @PutMapping("/admin/{id}/reactivate")
    public ResponseEntity<PropertyResponse> adminReactivate(@PathVariable UUID id, HttpServletRequest httpReq) {
        return ResponseEntity.ok(svc.adminReactivate(id, resolveUserId(httpReq)));
    }

    @Operation(summary = "Internal: activate all of a seller's eligible listings", description = "Called by verification-service (via Kafka consumer, or directly) when a seller's identity is approved. Requires the internal-secret header; not for external clients.")
    @PutMapping("/internal/seller/{sellerId}/activate-all")
    public ResponseEntity<Void> activateAll(@PathVariable UUID sellerId) {
        svc.activateAllForSeller(sellerId);
        return ResponseEntity.ok().build();
    }

    @Operation(summary = "Internal: suspend all of a seller's listings", description = "Called when a seller account is banned. Requires the internal-secret header; not for external clients.")
    @PutMapping("/internal/seller/{sellerId}/suspend-all")
    public ResponseEntity<Void> suspendAll(@PathVariable UUID sellerId,
                                            @RequestParam(required = false, defaultValue = "Seller account banned") String reason) {
        svc.suspendAllForSeller(sellerId, reason);
        return ResponseEntity.ok().build();
    }

    @Operation(summary = "Internal: mark a property's sale/rental transaction complete", description = "Delists the property (SOLD/RENTED) to prevent double-selling. Requires the internal-secret header.")
    @PutMapping("/internal/{propertyId}/mark-transaction-complete")
    public ResponseEntity<Void> markTransactionComplete(@PathVariable UUID propertyId) {
        svc.markTransactionComplete(propertyId);
        return ResponseEntity.ok().build();
    }

    @Operation(summary = "Internal: mark a property's ownership as verified", description = "Called when verification-service approves ownership. Requires the internal-secret header.")
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
        throw new UnauthorizedException("Cannot resolve user identity");
    }

    private UUID resolveUserIdOrNull(HttpServletRequest req) {
        try {
            return resolveUserId(req);
        } catch (RuntimeException e) {
            return null;
        }
    }

    private String getClientIp(HttpServletRequest req) {
        String xff = req.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) return xff.split(",")[0].trim();
        String realIp = req.getHeader("X-Real-IP");
        if (realIp != null && !realIp.isBlank()) return realIp;
        return req.getRemoteAddr();
    }
}
