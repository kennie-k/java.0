package com.kenyarealestate.property.service;

import com.kenyarealestate.property.client.VerificationClient;
import com.kenyarealestate.property.dto.*;
import com.kenyarealestate.property.entity.*;
import com.kenyarealestate.property.exception.ConflictException;
import com.kenyarealestate.property.exception.ForbiddenException;
import com.kenyarealestate.property.exception.NotFoundException;
import com.kenyarealestate.property.repository.PropertyImageHashRepository;
import com.kenyarealestate.property.repository.PropertyRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.*;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.*;

@Slf4j
@Service
@Transactional
public class PropertyService {

    private final PropertyRepository repo;
    private final VerificationClient verifClient;
    private final RedisTemplate<String, Object> redis;
    private final PropertyAuditService auditService;
    private final PropertyImageHashRepository imageHashRepo;
    private final ImageHashService imageHashService;

    private static final String CACHE_DETAIL_PREFIX = "property:detail:";
    private static final String CACHE_SEARCH_PREFIX = "property:search:";
    private static final String VIEW_DEBOUNCE_PREFIX = "property:viewed:";

    @Value("${redis.property-detail-ttl-seconds:300}")
    private long detailTtl;

    @Value("${redis.property-search-ttl-seconds:120}")
    private long searchTtl;

    @Value("${redis.view-debounce-window-minutes:30}")
    private long viewDebounceWindowMinutes;

    public PropertyService(PropertyRepository repo,
                           VerificationClient verifClient,
                           RedisTemplate<String, Object> redis,
                           PropertyAuditService auditService,
                           PropertyImageHashRepository imageHashRepo,
                           ImageHashService imageHashService) {
        this.repo = repo;
        this.verifClient = verifClient;
        this.redis = redis;
        this.auditService = auditService;
        this.imageHashRepo = imageHashRepo;
        this.imageHashService = imageHashService;
    }

    public PropertyResponse create(UUID sellerId, CreatePropertyRequest req) {
        boolean identityVerified = verifClient.isIdentityVerified(sellerId);

        Property p = repo.save(Property.builder()
                .sellerId(sellerId)
                .title(req.getTitle())
                .description(req.getDescription())
                .propertyType(PropertyType.valueOf(req.getPropertyType().toUpperCase()))
                .listingType(ListingType.valueOf(req.getListingType().toUpperCase()))
                .county(req.getCounty())
                .subCounty(req.getSubCounty())
                .city(req.getCity())
                .locationDescription(req.getLocationDescription())
                .latitude(req.getLatitude())
                .longitude(req.getLongitude())
                .price(req.getPrice())
                .bedrooms(req.getBedrooms())
                .bathrooms(req.getBathrooms())
                .areaSqm(req.getAreaSqm())
                .yearBuilt(req.getYearBuilt())
                .imageUrls(req.getImageUrls() != null ? req.getImageUrls() : new ArrayList<>())
                .sellerIdentityVerified(identityVerified)
                .status(identityVerified ? ListingStatus.PENDING_VERIFICATION : ListingStatus.DRAFT)
                .build());

        syncImageHashes(sellerId, p.getId(), p.getImageUrls());

        auditService.log(p.getId(), "PROPERTY_CREATED", null, p.getStatus().name(),
                sellerId, "SELLER", null,
                "identityVerified=" + identityVerified + " type=" + req.getPropertyType());

        evictSearchCache();
        return toResponse(p);
    }

    public PropertyResponse update(UUID id, UUID sellerId, UpdatePropertyRequest req) {
        Property p = findByIdAndSeller(id, sellerId);
        String prevStatus = p.getStatus().name();

        if (req.getTitle() != null) p.setTitle(req.getTitle());
        if (req.getDescription() != null) p.setDescription(req.getDescription());
        if (req.getCounty() != null) p.setCounty(req.getCounty());
        if (req.getSubCounty() != null) p.setSubCounty(req.getSubCounty());
        if (req.getCity() != null) p.setCity(req.getCity());
        if (req.getLocationDescription() != null) p.setLocationDescription(req.getLocationDescription());
        if (req.getLatitude() != null) p.setLatitude(req.getLatitude());
        if (req.getLongitude() != null) p.setLongitude(req.getLongitude());
        if (req.getPrice() != null) p.setPrice(req.getPrice());
        if (req.getBedrooms() != null) p.setBedrooms(req.getBedrooms());
        if (req.getBathrooms() != null) p.setBathrooms(req.getBathrooms());
        if (req.getAreaSqm() != null) p.setAreaSqm(req.getAreaSqm());
        if (req.getYearBuilt() != null) p.setYearBuilt(req.getYearBuilt());
        if (req.getImageUrls() != null) {
            p.setImageUrls(req.getImageUrls());
            syncImageHashes(sellerId, id, req.getImageUrls());
        }

        Property saved = repo.save(p);
        auditService.log(id, "PROPERTY_UPDATED", prevStatus, saved.getStatus().name(),
                sellerId, "SELLER", null, "Seller updated property fields");

        evictDetailCache(id);
        evictSearchCache();
        return toResponse(saved);
    }

    public void delete(UUID id, UUID sellerId) {
        Property p = findByIdAndSeller(id, sellerId);
        String prevStatus = p.getStatus().name();
        repo.delete(p);
        imageHashRepo.deleteByPropertyId(id);
        auditService.log(id, "PROPERTY_DELETED", prevStatus, "DELETED",
                sellerId, "SELLER", null, "Property deleted by seller");
        evictDetailCache(id);
        evictSearchCache();
    }

    // Cross-seller image reuse is a strong fraud signal (stolen photos, or the same bad actor
    // running multiple fake listings) — the same seller reusing their own photos across a
    // re-listing is legitimate and explicitly excluded. Re-syncs the full hash set on every
    // create/update so it never drifts from the property's current imageUrls.
    private void syncImageHashes(UUID sellerId, UUID propertyId, List<String> imageUrls) {
        if (imageUrls == null || imageUrls.isEmpty()) return;
        List<PropertyImageHash> toSave = new ArrayList<>();
        for (String url : imageUrls) {
            Optional<String> hashOpt = imageHashService.computeSha256(url);
            if (hashOpt.isEmpty()) continue;
            String hash = hashOpt.get();
            imageHashRepo.findFirstByImageHashAndSellerIdNot(hash, sellerId).ifPresent(existing -> {
                log.warn("ALERT: sellerId={} attempted to use a photo already used by sellerId={} on propertyId={}",
                        sellerId, existing.getSellerId(), existing.getPropertyId());
                // Runs in its own REQUIRES_NEW transaction (see PropertyAuditService), so the
                // fraud attempt is recorded permanently even though we're about to roll back
                // this create/update by throwing.
                auditService.log(propertyId, "DUPLICATE_PHOTO_FRAUD_DETECTED", null, null,
                        sellerId, "SELLER", null,
                        "Attempted to reuse a photo already used by sellerId=" + existing.getSellerId()
                                + " on propertyId=" + existing.getPropertyId());
                throw new ConflictException(
                        "One of these photos has already been used on another seller's listing. Please upload your own original photos.");
            });
            toSave.add(PropertyImageHash.builder()
                    .propertyId(propertyId).sellerId(sellerId).imageUrl(url).imageHash(hash).build());
        }
        imageHashRepo.deleteByPropertyId(propertyId);
        imageHashRepo.saveAll(toSave);
    }

    @Transactional(readOnly = true)
    public PropertyResponse getById(UUID id, UUID callerId, boolean isAdmin, String viewerIp) {
        String key = CACHE_DETAIL_PREFIX + id;
        try {
            Object cached = redis.opsForValue().get(key);
            if (cached instanceof PropertyResponse r) {
                if (!"ACTIVE".equals(r.getStatus()) && !isAdmin
                        && (callerId == null || !callerId.equals(r.getSellerId()))) {
                    throw new NotFoundException("Property not found");
                }
                return r;
            }
        } catch (NotFoundException e) {
            throw e;
        } catch (Exception e) {
            log.warn("Detail cache read error: {}", e.getMessage());
        }
        Property p = repo.findById(id).orElseThrow(() -> new NotFoundException("Property not found"));
        if (p.getStatus() != ListingStatus.ACTIVE && !isAdmin
                && (callerId == null || !callerId.equals(p.getSellerId()))) {
            throw new NotFoundException("Property not found");
        }
        // Debounce: only count one view per viewer (authenticated user, or IP if anonymous) per
        // property within the configured window, so the endpoint can't be trivially hammered
        // (by an attacker, or by the property's own seller) to inflate its view count.
        String viewerKey = callerId != null ? "u:" + callerId : "ip:" + (viewerIp != null ? viewerIp : "unknown");
        if (tryClaimView(id, viewerKey)) {
            p.setViewCount(p.getViewCount() + 1);
            repo.save(p);
        }
        PropertyResponse resp = toResponse(p);
        try {
            redis.opsForValue().set(key, resp, Duration.ofSeconds(detailTtl));
        } catch (Exception e) {
            log.warn("Detail cache write error: {}", e.getMessage());
        }
        return resp;
    }

    private boolean tryClaimView(UUID propertyId, String viewerKey) {
        try {
            String key = VIEW_DEBOUNCE_PREFIX + propertyId + ":" + viewerKey;
            Boolean firstViewInWindow = redis.opsForValue()
                    .setIfAbsent(key, "1", Duration.ofMinutes(viewDebounceWindowMinutes));
            return Boolean.TRUE.equals(firstViewInWindow);
        } catch (Exception e) {
            // Fail closed: if Redis is unavailable we'd rather under-count views than let the
            // debounce be silently bypassed.
            log.warn("View debounce check failed, not counting this view: {}", e.getMessage());
            return false;
        }
    }

    @Transactional(readOnly = true)
    public List<PropertyResponse> getActiveBySeller(UUID sellerId) {
        return repo.findBySellerIdAndStatus(sellerId, ListingStatus.ACTIVE).stream()
                .map(this::toResponse).collect(java.util.stream.Collectors.toList());
    }

    @Transactional(readOnly = true)
    @SuppressWarnings("unchecked")
    public Page<PropertyResponse> search(PropertySearchRequest req) {
        String cacheKey = buildSearchKey(req);
        try {
            Object cached = redis.opsForValue().get(cacheKey);
            if (cached instanceof Page<?> p) {
                return (Page<PropertyResponse>) p;
            }
        } catch (Exception e) {
            log.warn("Search cache read error: {}", e.getMessage());
        }

        String propertyType = (req.getPropertyType() != null && !req.getPropertyType().isBlank())
                ? req.getPropertyType().toUpperCase() : null;
        String listingType = (req.getListingType() != null && !req.getListingType().isBlank())
                ? req.getListingType().toUpperCase() : null;

        Pageable pageable = PageRequest.of(req.getPage(), req.getSize());

        Page<Property> page = repo.search(
                req.getCounty(), req.getCity(),
                propertyType, listingType,
                req.getMinPrice(), req.getMaxPrice(),
                req.getMinBedrooms(), req.getKeyword(),
                req.isVerifiedOnly(),
                pageable);

        Page<PropertyResponse> result = page.map(this::toResponse);
        try {
            redis.opsForValue().set(cacheKey, result, Duration.ofSeconds(searchTtl));
        } catch (Exception e) {
            log.warn("Search cache write error: {}", e.getMessage());
        }
        return result;
    }

    @Transactional(readOnly = true)
    public Page<PropertyResponse> getMyListings(UUID sellerId, Pageable p) {
        return repo.findBySellerId(sellerId, p).map(this::toResponse);
    }

    public void activateAllForSeller(UUID sellerId) {
        List<Property> props = new ArrayList<>(repo.findBySellerIdAndStatus(sellerId, ListingStatus.DRAFT));
        props.addAll(repo.findBySellerIdAndStatus(sellerId, ListingStatus.PENDING_VERIFICATION));
        props.forEach(p -> {
            String prevStatus = p.getStatus().name();
            p.setSellerIdentityVerified(true);
            p.setStatus(p.isPropertyOwnershipVerified() ? ListingStatus.ACTIVE : ListingStatus.PENDING_VERIFICATION);
            repo.save(p);
            auditService.log(p.getId(), "IDENTITY_VERIFIED_ACTIVATION",
                    prevStatus, p.getStatus().name(),
                    sellerId, "SYSTEM", null,
                    "Activated via Kafka IDENTITY_APPROVED event for sellerId=" + sellerId);
        });
        evictSearchCache();
        log.info("Activated {} properties for sellerId={}", props.size(), sellerId);
    }

    public void suspendAllForSeller(UUID sellerId, String reason) {
        List<Property> props = new ArrayList<>(repo.findBySellerIdAndStatus(sellerId, ListingStatus.ACTIVE));
        props.addAll(repo.findBySellerIdAndStatus(sellerId, ListingStatus.PENDING_VERIFICATION));
        props.addAll(repo.findBySellerIdAndStatus(sellerId, ListingStatus.DRAFT));
        props.forEach(p -> {
            String prevStatus = p.getStatus().name();
            p.setStatus(ListingStatus.SUSPENDED);
            repo.save(p);
            auditService.log(p.getId(), "SELLER_BANNED_AUTO_SUSPENDED",
                    prevStatus, ListingStatus.SUSPENDED.name(),
                    null, "SYSTEM", null, reason);
            evictDetailCache(p.getId());
        });
        evictSearchCache();
        log.info("Suspended {} properties for banned sellerId={}", props.size(), sellerId);
    }

    public void markOwnershipVerified(UUID propertyId, String parcelNumber, String titleDeedNumber) {
        Property p = repo.findById(propertyId)
                .orElseThrow(() -> new NotFoundException("Property not found: " + propertyId));
        String prevStatus = p.getStatus().name();
        p.setPropertyOwnershipVerified(true);
        boolean duplicateDetected = false;
        if (parcelNumber != null && !parcelNumber.isBlank()) {
            List<Property> duplicates = repo.findByParcelNumberAndIdNot(parcelNumber, propertyId);
            if (!duplicates.isEmpty()) {
                duplicateDetected = true;
                p.setDuplicateParcelFlag(true);
                log.warn("ALERT: parcel number {} is already used by {} other listing(s): {}",
                        parcelNumber, duplicates.size(),
                        duplicates.stream().map(d -> d.getId().toString()).collect(java.util.stream.Collectors.joining(",")));
                auditService.log(propertyId, "DUPLICATE_PARCEL_DETECTED",
                        prevStatus, prevStatus,
                        null, "SYSTEM", null,
                        "Parcel number " + parcelNumber + " already used by " + duplicates.size()
                                + " other listing(s) - activation blocked pending admin review");
            }
            p.setParcelNumber(parcelNumber);
        }
        if (titleDeedNumber != null && !titleDeedNumber.isBlank()) p.setTitleDeedNumber(titleDeedNumber);

        boolean eligibleForActivation = p.getStatus() == ListingStatus.DRAFT
                || p.getStatus() == ListingStatus.PENDING_VERIFICATION;
        if (p.isSellerIdentityVerified() && !duplicateDetected && eligibleForActivation) {
            p.setStatus(ListingStatus.ACTIVE);
        }
        repo.save(p);
        auditService.log(propertyId, "OWNERSHIP_VERIFIED_ACTIVATION",
                prevStatus, p.getStatus().name(),
                null, "SYSTEM", null,
                duplicateDetected
                        ? "Ownership verified but held pending admin review due to duplicate parcel number"
                        : "Activated via Kafka OWNERSHIP_APPROVED event");
        evictDetailCache(propertyId);
        evictSearchCache();
    }

    public void markTransactionComplete(UUID propertyId) {
        Property p = repo.findById(propertyId)
                .orElseThrow(() -> new NotFoundException("Property not found: " + propertyId));
        if (p.getStatus() != ListingStatus.ACTIVE) return;
        String prevStatus = p.getStatus().name();
        ListingStatus newStatus = p.getListingType() == ListingType.RENT
                ? ListingStatus.RENTED : ListingStatus.SOLD;
        p.setStatus(newStatus);
        repo.save(p);
        auditService.log(propertyId, "TRANSACTION_COMPLETED_DELISTED",
                prevStatus, newStatus.name(),
                null, "SYSTEM", null,
                "Escrow released for a full payment on this property - delisted to prevent double-selling");
        evictDetailCache(propertyId);
        evictSearchCache();
    }

    @Transactional(readOnly = true)
    public PropertyAdminStatsResponse getAdminStats() {
        List<PropertyAdminStatsResponse.TypeCount> byType = repo.countActiveByType().stream()
                .map(row -> PropertyAdminStatsResponse.TypeCount.builder()
                        .name(String.valueOf(row[0])).value((Long) row[1]).build())
                .collect(java.util.stream.Collectors.toList());

        List<PropertyAdminStatsResponse.CountyCount> topCounties = repo.countActiveByCounty(PageRequest.of(0, 6)).stream()
                .map(row -> PropertyAdminStatsResponse.CountyCount.builder()
                        .name(String.valueOf(row[0])).value((Long) row[1]).build())
                .collect(java.util.stream.Collectors.toList());

        return PropertyAdminStatsResponse.builder()
                .active(repo.countByStatus(ListingStatus.ACTIVE))
                .draft(repo.countByStatus(ListingStatus.DRAFT))
                .pendingVerification(repo.countByStatus(ListingStatus.PENDING_VERIFICATION))
                .sold(repo.countByStatus(ListingStatus.SOLD))
                .rented(repo.countByStatus(ListingStatus.RENTED))
                .suspended(repo.countByStatus(ListingStatus.SUSPENDED))
                .withdrawn(repo.countByStatus(ListingStatus.WITHDRAWN))
                .avgActivePrice(repo.averageActivePrice())
                .totalViews(repo.sumViewCount())
                .byType(byType)
                .topCounties(topCounties)
                .build();
    }

    public PropertyResponse adminSuspend(UUID propertyId, UUID adminId) {
        Property p = repo.findById(propertyId)
                .orElseThrow(() -> new NotFoundException("Property not found: " + propertyId));
        String prevStatus = p.getStatus().name();
        p.setStatus(ListingStatus.SUSPENDED);
        Property saved = repo.save(p);
        auditService.log(propertyId, "ADMIN_SUSPENDED", prevStatus, saved.getStatus().name(),
                adminId, "ADMIN", null, "Listing suspended by admin");
        evictDetailCache(propertyId);
        evictSearchCache();
        return toResponse(saved);
    }

    public PropertyResponse adminReactivate(UUID propertyId, UUID adminId) {
        Property p = repo.findById(propertyId)
                .orElseThrow(() -> new NotFoundException("Property not found: " + propertyId));
        String prevStatus = p.getStatus().name();
        boolean fullyVerified = p.isSellerIdentityVerified() && p.isPropertyOwnershipVerified() && !p.isDuplicateParcelFlag();
        p.setStatus(fullyVerified ? ListingStatus.ACTIVE : ListingStatus.PENDING_VERIFICATION);
        if (fullyVerified) p.setDuplicateParcelFlag(false);
        Property saved = repo.save(p);
        auditService.log(propertyId, "ADMIN_REACTIVATED", prevStatus, saved.getStatus().name(),
                adminId, "ADMIN", null, fullyVerified
                        ? "Listing reactivated by admin"
                        : "Listing unsuspended by admin but held at PENDING_VERIFICATION - identity and/or ownership verification not yet complete");
        evictDetailCache(propertyId);
        evictSearchCache();
        return toResponse(saved);
    }

    @Transactional(readOnly = true)
    public Page<PropertyResponse> adminGetAll(ListingStatus status, Pageable pageable) {
        Page<Property> page = status != null ? repo.findByStatus(status, pageable) : repo.findAll(pageable);
        return page.map(this::toResponse);
    }

    private Property findByIdAndSeller(UUID id, UUID sellerId) {
        Property p = repo.findById(id).orElseThrow(() -> new NotFoundException("Property not found"));
        if (!p.getSellerId().equals(sellerId)) throw new ForbiddenException("Access denied");
        return p;
    }

    private void evictDetailCache(UUID id) {
        try { redis.delete(CACHE_DETAIL_PREFIX + id); } catch (Exception ignored) {}
    }

    private void evictSearchCache() {
        try {
            Set<String> keys = redis.keys(CACHE_SEARCH_PREFIX + "*");
            if (keys != null && !keys.isEmpty()) redis.delete(keys);
        } catch (Exception ignored) {}
    }

    private String buildSearchKey(PropertySearchRequest r) {
        return CACHE_SEARCH_PREFIX + r.getCounty() + ":" + r.getCity() + ":" +
                r.getPropertyType() + ":" + r.getListingType() + ":" + r.getKeyword() + ":" +
                r.getMinPrice() + ":" + r.getMaxPrice() + ":" + r.getMinBedrooms() + ":" +
                r.isVerifiedOnly() + ":" +
                r.getPage() + ":" + r.getSize() + ":" + r.getSortBy() + ":" + r.getDirection();
    }

    private PropertyResponse toResponse(Property p) {
        return PropertyResponse.builder()
                .id(p.getId()).sellerId(p.getSellerId())
                .title(p.getTitle()).description(p.getDescription())
                .propertyType(p.getPropertyType().name())
                .listingType(p.getListingType().name())
                .status(p.getStatus().name())
                .county(p.getCounty()).subCounty(p.getSubCounty()).city(p.getCity())
                .locationDescription(p.getLocationDescription())
                .latitude(p.getLatitude()).longitude(p.getLongitude())
                .price(p.getPrice()).bedrooms(p.getBedrooms()).bathrooms(p.getBathrooms())
                .yearBuilt(p.getYearBuilt()).areaSqm(p.getAreaSqm())
                .imageUrls(new java.util.ArrayList<>(p.getImageUrls()))
                .sellerIdentityVerified(p.isSellerIdentityVerified())
                .propertyOwnershipVerified(p.isPropertyOwnershipVerified())
                .fullyTrusted(p.isSellerIdentityVerified() && p.isPropertyOwnershipVerified())
                .duplicateParcelFlag(p.isDuplicateParcelFlag())
                .parcelNumber(p.getParcelNumber()).titleDeedNumber(p.getTitleDeedNumber())
                .viewCount(p.getViewCount())
                .createdAt(p.getCreatedAt()).updatedAt(p.getUpdatedAt())
                .build();
    }
}
