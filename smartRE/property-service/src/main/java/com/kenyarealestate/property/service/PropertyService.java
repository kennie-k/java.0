package com.kenyarealestate.property.service;

import com.kenyarealestate.property.client.VerificationClient;
import com.kenyarealestate.property.dto.*;
import com.kenyarealestate.property.entity.*;
import com.kenyarealestate.property.repository.PropertyRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.*;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.*;

@Slf4j
@Service
@Transactional
public class PropertyService {

    private final PropertyRepository repo;
    private final VerificationClient verifClient;
    private final RedisTemplate<String, Object> redis;
    private final PropertyAuditService auditService;

    private static final String CACHE_DETAIL_PREFIX = "property:detail:";
    private static final String CACHE_SEARCH_PREFIX = "property:search:";

    @Value("${redis.property-detail-ttl-seconds:300}")
    private long detailTtl;

    @Value("${redis.property-search-ttl-seconds:120}")
    private long searchTtl;

    public PropertyService(PropertyRepository repo,
                           VerificationClient verifClient,
                           RedisTemplate<String, Object> redis,
                           PropertyAuditService auditService) {
        this.repo = repo;
        this.verifClient = verifClient;
        this.redis = redis;
        this.auditService = auditService;
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
        if (req.getImageUrls() != null) p.setImageUrls(req.getImageUrls());

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
        auditService.log(id, "PROPERTY_DELETED", prevStatus, "DELETED",
                sellerId, "SELLER", null, "Property deleted by seller");
        evictDetailCache(id);
        evictSearchCache();
    }

    @Transactional(readOnly = true)
    public PropertyResponse getById(UUID id, UUID callerId, boolean isAdmin) {
        String key = CACHE_DETAIL_PREFIX + id;
        try {
            Object cached = redis.opsForValue().get(key);
            if (cached instanceof PropertyResponse r) {
                if (!"ACTIVE".equals(r.getStatus()) && !isAdmin
                        && (callerId == null || !callerId.equals(r.getSellerId()))) {
                    throw new RuntimeException("Property not found");
                }
                return r;
            }
        } catch (Exception e) {
            log.warn("Detail cache read error: {}", e.getMessage());
        }
        Property p = repo.findById(id).orElseThrow(() -> new RuntimeException("Property not found"));
        if (p.getStatus() != ListingStatus.ACTIVE && !isAdmin
                && (callerId == null || !callerId.equals(p.getSellerId()))) {
            throw new RuntimeException("Property not found");
        }
        p.setViewCount(p.getViewCount() + 1);
        repo.save(p);
        PropertyResponse resp = toResponse(p);
        try {
            redis.opsForValue().set(key, resp, java.time.Duration.ofSeconds(detailTtl));
        } catch (Exception e) {
            log.warn("Detail cache write error: {}", e.getMessage());
        }
        return resp;
    }

    @Transactional(readOnly = true)
    public List<PropertyResponse> getActiveBySeller(UUID sellerId) {
        return repo.findBySellerIdAndStatus(sellerId, ListingStatus.ACTIVE).stream()
                .map(this::toResponse).collect(java.util.stream.Collectors.toList());
    }

    @Transactional(readOnly = true)
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
                pageable);

        Page<PropertyResponse> result = page.map(this::toResponse);
        try {
            redis.opsForValue().set(cacheKey, result, java.time.Duration.ofSeconds(searchTtl));
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
        List<Property> props = repo.findBySellerIdAndStatus(sellerId, ListingStatus.DRAFT);
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

    public void markOwnershipVerified(UUID propertyId, String parcelNumber, String titleDeedNumber) {
        Property p = repo.findById(propertyId)
                .orElseThrow(() -> new RuntimeException("Property not found: " + propertyId));
        String prevStatus = p.getStatus().name();
        p.setPropertyOwnershipVerified(true);
        if (parcelNumber != null && !parcelNumber.isBlank()) {
            List<Property> duplicates = repo.findByParcelNumberAndIdNot(parcelNumber, propertyId);
            if (!duplicates.isEmpty()) {
                log.warn("ALERT: parcel number {} is already used by {} other listing(s): {}",
                        parcelNumber, duplicates.size(),
                        duplicates.stream().map(d -> d.getId().toString()).collect(java.util.stream.Collectors.joining(",")));
                auditService.log(propertyId, "DUPLICATE_PARCEL_DETECTED",
                        prevStatus, prevStatus,
                        null, "SYSTEM", null,
                        "Parcel number " + parcelNumber + " already used by " + duplicates.size() + " other listing(s)");
            }
            p.setParcelNumber(parcelNumber);
        }
        if (titleDeedNumber != null && !titleDeedNumber.isBlank()) p.setTitleDeedNumber(titleDeedNumber);
        if (p.isSellerIdentityVerified()) p.setStatus(ListingStatus.ACTIVE);
        repo.save(p);
        auditService.log(propertyId, "OWNERSHIP_VERIFIED_ACTIVATION",
                prevStatus, p.getStatus().name(),
                null, "SYSTEM", null,
                "Activated via Kafka OWNERSHIP_APPROVED event");
        evictDetailCache(propertyId);
        evictSearchCache();
    }

    private Property findByIdAndSeller(UUID id, UUID sellerId) {
        Property p = repo.findById(id).orElseThrow(() -> new RuntimeException("Property not found"));
        if (!p.getSellerId().equals(sellerId)) throw new RuntimeException("Access denied");
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
                .imageUrls(p.getImageUrls())
                .sellerIdentityVerified(p.isSellerIdentityVerified())
                .propertyOwnershipVerified(p.isPropertyOwnershipVerified())
                .fullyTrusted(p.isSellerIdentityVerified() && p.isPropertyOwnershipVerified())
                .parcelNumber(p.getParcelNumber()).titleDeedNumber(p.getTitleDeedNumber())
                .viewCount(p.getViewCount())
                .createdAt(p.getCreatedAt()).updatedAt(p.getUpdatedAt())
                .build();
    }
}