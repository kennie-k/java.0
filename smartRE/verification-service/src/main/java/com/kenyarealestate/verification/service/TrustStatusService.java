package com.kenyarealestate.verification.service;

import com.kenyarealestate.verification.dto.TrustStatusResponse;
import com.kenyarealestate.verification.entity.PropertyOwnershipVerification;
import com.kenyarealestate.verification.entity.SellerIdentityVerification;
import com.kenyarealestate.verification.enums.BadgeLevel;
import com.kenyarealestate.verification.enums.IdentityVerificationStatus;
import com.kenyarealestate.verification.enums.OwnershipVerificationStatus;
import com.kenyarealestate.verification.repository.PropertyOwnershipVerificationRepository;
import com.kenyarealestate.verification.repository.SellerIdentityVerificationRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@Transactional(readOnly = true)
public class TrustStatusService {

    private final SellerIdentityVerificationRepository identityRepo;
    private final PropertyOwnershipVerificationRepository ownershipRepo;
    private final TrustStatusCacheService cache;

    public TrustStatusService(SellerIdentityVerificationRepository identityRepo,
                               PropertyOwnershipVerificationRepository ownershipRepo,
                               TrustStatusCacheService cache) {
        this.identityRepo = identityRepo;
        this.ownershipRepo = ownershipRepo;
        this.cache = cache;
    }

    public TrustStatusResponse getTrustStatus(UUID userId, UUID propertyId) {

        TrustStatusResponse cached = propertyId != null
                ? cache.getForProperty(userId, propertyId)
                : cache.get(userId);
        if (cached != null) {
            log.debug("Trust status cache HIT for userId={}", userId);
            return cached;
        }

        log.debug("Trust status cache MISS for userId={} — querying DB", userId);
        TrustStatusResponse response = buildFromDb(userId, propertyId);

        if (propertyId != null) {
            cache.putForProperty(userId, propertyId, response);
        } else {
            cache.put(userId, response);
        }

        return response;
    }

    public TrustStatusResponse getIdentityOnlyStatus(UUID userId) {
        return getTrustStatus(userId, null);
    }

    public void evictCache(UUID userId) {
        cache.evict(userId);
    }

    private TrustStatusResponse buildFromDb(UUID userId, UUID propertyId) {
        Optional<SellerIdentityVerification> identityOpt = identityRepo.findByUserId(userId);
        Optional<PropertyOwnershipVerification> ownershipOpt = propertyId != null
                ? ownershipRepo.findByPropertyId(propertyId)
                : Optional.empty();

        boolean identityVerified = identityOpt.map(this::isApprovedAndNotExpired).orElse(false);
        boolean ownershipVerified = ownershipOpt
                .map(v -> v.getStatus() == OwnershipVerificationStatus.APPROVED).orElse(false);
        boolean identityExpired = identityOpt.map(this::isExpired).orElse(false);

        return TrustStatusResponse.builder()
                .userId(userId)
                .identityVerified(identityVerified)
                .identityStatus(identityOpt.map(SellerIdentityVerification::getStatus).orElse(null))
                .badgeLevel(identityOpt.map(SellerIdentityVerification::getBadgeLevel).orElse(BadgeLevel.NONE))
                .identityScore(identityOpt.map(SellerIdentityVerification::getIdentityScore).orElse(0))
                .identityExpired(identityExpired)
                .identityExpiresAt(identityOpt.map(SellerIdentityVerification::getExpiresAt).orElse(null))
                .propertyId(propertyId)
                .ownershipVerified(ownershipVerified)
                .ownershipStatus(ownershipOpt.map(PropertyOwnershipVerification::getStatus).orElse(null))
                .ownershipScore(ownershipOpt.map(PropertyOwnershipVerification::getOwnershipScore).orElse(0))
                .ministryLandsConfirmed(ownershipOpt.map(PropertyOwnershipVerification::getMinistryLandsConfirmed).orElse(false))
                .encumbranceClear(ownershipOpt.map(PropertyOwnershipVerification::getEncumbranceClear).orElse(false))
                .fullyTrusted(identityVerified && ownershipVerified && !identityExpired)
                .message(buildMessage(identityVerified, identityExpired, ownershipVerified,
                        identityOpt.orElse(null), ownershipOpt.orElse(null)))
                .build();
    }

    private boolean isApprovedAndNotExpired(SellerIdentityVerification v) {
        return v.getStatus() == IdentityVerificationStatus.APPROVED && !isExpired(v) && !v.isPermanentlyBanned();
    }

    private boolean isExpired(SellerIdentityVerification v) {
        return v.getExpiresAt() != null && v.getExpiresAt().isBefore(LocalDateTime.now());
    }

    private String buildMessage(boolean idVerified, boolean idExpired, boolean ownVerified,
                                 SellerIdentityVerification iv, PropertyOwnershipVerification ov) {
        if (idExpired) return "Seller identity verification has EXPIRED. Please renew.";
        if (iv != null && iv.isPermanentlyBanned()) return "This account has been permanently banned.";
        if (idVerified && ownVerified)
            return "Seller identity verified. Property ownership verified. Fully trusted.";
        if (!idVerified && iv == null) return "Seller has not started identity verification.";
        if (!idVerified) return "Seller identity is " + iv.getStatus() + ".";
        if (!ownVerified && ov == null)
            return "Identity verified. Property ownership verification not started.";
        return "Identity verified. Property ownership is " + ov.getStatus() + ".";
    }
}
