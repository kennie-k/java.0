package com.kenyarealestate.verification.service;

import com.kenyarealestate.verification.entity.PropertyOwnershipDocument;
import com.kenyarealestate.verification.entity.PropertyOwnershipVerification;
import com.kenyarealestate.verification.entity.SellerIdentityDocument;
import com.kenyarealestate.verification.entity.SellerIdentityVerification;
import com.kenyarealestate.verification.enums.BadgeLevel;
import com.kenyarealestate.verification.enums.IdentityDocumentCategory;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class ScoringEngine {

    public int computeIdentityScore(SellerIdentityVerification verif) {
        List<SellerIdentityDocument> docs = verif.getDocuments();
        if (docs == null || docs.isEmpty()) return 0;

        int score = 0;

        boolean hasIdFront = hasCategory(docs, "NATIONAL_ID_FRONT");
        boolean hasIdBack  = hasCategory(docs, "NATIONAL_ID_BACK");
        boolean hasKra     = hasCategory(docs, "KRA_PIN_CERTIFICATE");
        boolean hasSelfie  = hasCategory(docs, "SELFIE_WITH_ID");
        boolean hasBizReg  = hasCategory(docs, "BUSINESS_REGISTRATION_CERTIFICATE");

        if (hasIdFront && hasIdBack) score += 25;
        else if (hasIdFront || hasIdBack) score += 10;
        if (hasKra)   score += 15;
        if (hasSelfie) score += 10;
        if (hasBizReg) score += 5;

        double avgAi = docs.stream()
                .filter(d -> d.getAiAuthenticityScore() != null)
                .mapToInt(SellerIdentityDocument::getAiAuthenticityScore)
                .average().orElse(0.0);
        score += (int) (avgAi * 0.15);

        boolean anyTamper = docs.stream().anyMatch(d -> Boolean.TRUE.equals(d.getAiTamperDetected()));
        if (!anyTamper) score += 10;

        boolean sigsOk = docs.stream().anyMatch(d -> Boolean.TRUE.equals(d.getAiSignatureDetected()));
        if (sigsOk) score += 5;

        boolean sealOk = docs.stream().anyMatch(d -> Boolean.TRUE.equals(d.getAiSealDetected()));
        if (sealOk) score += 5;

        boolean metaClean = docs.stream()
                .filter(d -> d.getAiMetadataClean() != null)
                .allMatch(d -> Boolean.TRUE.equals(d.getAiMetadataClean()));
        if (metaClean && docs.stream().anyMatch(d -> d.getAiMetadataClean() != null)) score += 5;

        score += computeBiometricBonus(verif);

        return Math.min(score, 100);
    }

    private int computeBiometricBonus(SellerIdentityVerification verif) {
        if (verif.getFraudStrikeCount() != null && verif.getFraudStrikeCount() > 0) return -10;

        boolean selfieHighQuality = verif.getDocuments().stream()
                .anyMatch(d -> d.getDocumentCategory() == IdentityDocumentCategory.SELFIE_WITH_ID
                        && d.getAiAuthenticityScore() != null
                        && d.getAiAuthenticityScore() >= 80
                        && !Boolean.TRUE.equals(d.getAiTamperDetected()));

        boolean idFrontAuthentic = verif.getDocuments().stream()
                .anyMatch(d -> d.getDocumentCategory() == IdentityDocumentCategory.NATIONAL_ID_FRONT
                        && d.getAiAuthenticityScore() != null
                        && d.getAiAuthenticityScore() >= 80
                        && Boolean.TRUE.equals(d.getAiSignatureDetected())
                        && !Boolean.TRUE.equals(d.getAiTamperDetected()));

        int bonus = 0;
        if (selfieHighQuality) bonus += 5;
        if (idFrontAuthentic)  bonus += 5;
        return bonus;
    }

    public BadgeLevel computeBadgeLevel(int score) {
        if (score >= 90) return BadgeLevel.GOLD;
        if (score >= 75) return BadgeLevel.VERIFIED;
        if (score >= 50) return BadgeLevel.BASIC;
        return BadgeLevel.NONE;
    }

    public int computeOwnershipScore(PropertyOwnershipVerification verif) {
        List<PropertyOwnershipDocument> docs = verif.getDocuments();
        if (docs == null || docs.isEmpty()) return 0;

        int score = 0;

        PropertyOwnershipDocument titleDeed = docs.stream()
                .filter(d -> d.getDocumentCategory().name().equals("TITLE_DEED"))
                .findFirst().orElse(null);
        if (titleDeed != null) {
            int ai = titleDeed.getAiAuthenticityScore() != null ? titleDeed.getAiAuthenticityScore() : 0;
            if (ai >= 80) score += 25;
            else if (ai >= 60) score += 12;
        }

        if (hasOwnershipCategory(docs, "LAND_SEARCH_CERTIFICATE"))   score += 15;
        if (hasOwnershipCategory(docs, "LAND_RENT_CLEARANCE"))        score += 5;
        if (hasOwnershipCategory(docs, "RATES_CLEARANCE_CERTIFICATE")) score += 5;

        boolean advocateOk = docs.stream().anyMatch(d ->
                Boolean.TRUE.equals(d.getLcAdvocateStampPresent())
                && Boolean.TRUE.equals(d.getLcAdvocateSignaturePresent()));
        if (advocateOk) score += 10;

        boolean ownerWitness = docs.stream().anyMatch(d ->
                Boolean.TRUE.equals(d.getLcOwnerSignaturePresent())
                && Boolean.TRUE.equals(d.getLcWitnessSignaturesPresent()));
        if (ownerWitness) score += 10;

        boolean sealOk = docs.stream().anyMatch(d -> Boolean.TRUE.equals(d.getLcOfficialSealPresent()));
        if (sealOk) score += 5;

        boolean parcelMatch = docs.stream().anyMatch(d -> Boolean.TRUE.equals(d.getLcParcelNumberMatches()));
        if (parcelMatch) score += 5;

        boolean originalConfirmed = docs.stream()
                .anyMatch(d -> Boolean.TRUE.equals(d.getLcOriginalDocumentConfirmed()));
        if (originalConfirmed) score += 5;

        boolean clean = docs.stream().noneMatch(d ->
                Boolean.TRUE.equals(d.getAiTamperDetected())
                || Boolean.TRUE.equals(d.getAiAlterationDetected()));
        if (clean) score += 10;

        if (Boolean.TRUE.equals(verif.getMinistryLandsConfirmed())) score += 5;

        return Math.min(score, 100);
    }

    private boolean hasCategory(List<SellerIdentityDocument> docs, String category) {
        return docs.stream().anyMatch(d -> d.getDocumentCategory().name().equals(category));
    }

    private boolean hasOwnershipCategory(List<PropertyOwnershipDocument> docs, String category) {
        return docs.stream().anyMatch(d -> d.getDocumentCategory().name().equals(category));
    }
}
