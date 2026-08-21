package com.kenyarealestate.verification.service;

import com.kenyarealestate.verification.entity.PropertyOwnershipDocument;
import com.kenyarealestate.verification.entity.PropertyOwnershipVerification;
import com.kenyarealestate.verification.entity.SellerIdentityDocument;
import com.kenyarealestate.verification.entity.SellerIdentityVerification;
import com.kenyarealestate.verification.enums.BadgeLevel;
import com.kenyarealestate.verification.enums.IdentityDocumentCategory;
import com.kenyarealestate.verification.enums.OwnershipDocumentCategory;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class ScoringEngineTest {

    private final ScoringEngine engine = new ScoringEngine();

    private SellerIdentityDocument identityDoc(IdentityDocumentCategory category, Integer aiScore, boolean tamper) {
        return SellerIdentityDocument.builder()
                .id(UUID.randomUUID())
                .documentCategory(category)
                .aiAuthenticityScore(aiScore)
                .aiTamperDetected(tamper)
                .build();
    }

    @Test
    void computeIdentityScore_returnsZero_whenNoDocuments() {
        SellerIdentityVerification verif = SellerIdentityVerification.builder()
                .documents(List.of()).fraudStrikeCount(0).build();

        assertEquals(0, engine.computeIdentityScore(verif));
    }

    @Test
    void computeIdentityScore_rewardsCompleteCleanSubmission() {
        SellerIdentityVerification verif = SellerIdentityVerification.builder()
                .fraudStrikeCount(0)
                .documents(List.of(
                        identityDoc(IdentityDocumentCategory.NATIONAL_ID_FRONT, 90, false),
                        identityDoc(IdentityDocumentCategory.NATIONAL_ID_BACK, 90, false),
                        identityDoc(IdentityDocumentCategory.KRA_PIN_CERTIFICATE, 90, false),
                        identityDoc(IdentityDocumentCategory.SELFIE_WITH_ID, 90, false)
                ))
                .build();

        int score = engine.computeIdentityScore(verif);

        assertTrue(score > 50, "a complete, untampered submission should score well above the BASIC threshold");
    }

    @Test
    void computeIdentityScore_penalizesFraudStrikes() {
        SellerIdentityVerification clean = SellerIdentityVerification.builder()
                .fraudStrikeCount(0)
                .documents(List.of(identityDoc(IdentityDocumentCategory.NATIONAL_ID_FRONT, 90, false)))
                .build();
        SellerIdentityVerification flagged = SellerIdentityVerification.builder()
                .fraudStrikeCount(2)
                .documents(List.of(identityDoc(IdentityDocumentCategory.NATIONAL_ID_FRONT, 90, false)))
                .build();

        assertTrue(engine.computeIdentityScore(flagged) < engine.computeIdentityScore(clean));
    }

    @Test
    void computeIdentityScore_neverExceeds100() {
        SellerIdentityVerification verif = SellerIdentityVerification.builder()
                .fraudStrikeCount(0)
                .documents(List.of(
                        identityDoc(IdentityDocumentCategory.NATIONAL_ID_FRONT, 100, false),
                        identityDoc(IdentityDocumentCategory.NATIONAL_ID_BACK, 100, false),
                        identityDoc(IdentityDocumentCategory.KRA_PIN_CERTIFICATE, 100, false),
                        identityDoc(IdentityDocumentCategory.SELFIE_WITH_ID, 100, false),
                        identityDoc(IdentityDocumentCategory.BUSINESS_REGISTRATION_CERTIFICATE, 100, false)
                ))
                .build();

        assertTrue(engine.computeIdentityScore(verif) <= 100);
    }

    @Test
    void computeBadgeLevel_mapsScoreThresholdsCorrectly() {
        assertEquals(BadgeLevel.NONE, engine.computeBadgeLevel(0));
        assertEquals(BadgeLevel.NONE, engine.computeBadgeLevel(49));
        assertEquals(BadgeLevel.BASIC, engine.computeBadgeLevel(50));
        assertEquals(BadgeLevel.BASIC, engine.computeBadgeLevel(74));
        assertEquals(BadgeLevel.VERIFIED, engine.computeBadgeLevel(75));
        assertEquals(BadgeLevel.VERIFIED, engine.computeBadgeLevel(89));
        assertEquals(BadgeLevel.GOLD, engine.computeBadgeLevel(90));
        assertEquals(BadgeLevel.GOLD, engine.computeBadgeLevel(100));
    }

    @Test
    void computeOwnershipScore_returnsZero_whenNoDocuments() {
        PropertyOwnershipVerification verif = PropertyOwnershipVerification.builder()
                .documents(List.of()).build();

        assertEquals(0, engine.computeOwnershipScore(verif));
    }

    @Test
    void computeOwnershipScore_rewardsAuthenticTitleDeedAndClearMinistryCheck() {
        PropertyOwnershipDocument titleDeed = PropertyOwnershipDocument.builder()
                .id(UUID.randomUUID())
                .documentCategory(OwnershipDocumentCategory.TITLE_DEED)
                .aiAuthenticityScore(90)
                .aiTamperDetected(false)
                .aiAlterationDetected(false)
                .build();
        PropertyOwnershipVerification verif = PropertyOwnershipVerification.builder()
                .documents(List.of(titleDeed))
                .ministryLandsConfirmed(true)
                .build();

        int score = engine.computeOwnershipScore(verif);

        assertTrue(score >= 25 + 10 + 5, "title deed + clean-docs + ministry-confirmed bonuses should all apply");
    }

    @Test
    void computeOwnershipScore_withholdsTitleDeedPoints_whenAuthenticityLow() {
        PropertyOwnershipDocument weakTitleDeed = PropertyOwnershipDocument.builder()
                .id(UUID.randomUUID())
                .documentCategory(OwnershipDocumentCategory.TITLE_DEED)
                .aiAuthenticityScore(30)
                .build();
        PropertyOwnershipVerification verif = PropertyOwnershipVerification.builder()
                .documents(List.of(weakTitleDeed))
                .build();

        int score = engine.computeOwnershipScore(verif);

        assertTrue(score < 25, "a low-authenticity title deed should not earn the full 25 points");
    }

    @Test
    void computeOwnershipScore_neverExceeds100() {
        PropertyOwnershipDocument perfectDoc = PropertyOwnershipDocument.builder()
                .id(UUID.randomUUID())
                .documentCategory(OwnershipDocumentCategory.TITLE_DEED)
                .aiAuthenticityScore(100)
                .lcAdvocateStampPresent(true).lcAdvocateSignaturePresent(true)
                .lcOwnerSignaturePresent(true).lcWitnessSignaturesPresent(true)
                .lcOfficialSealPresent(true).lcParcelNumberMatches(true)
                .lcOriginalDocumentConfirmed(true)
                .build();
        PropertyOwnershipVerification verif = PropertyOwnershipVerification.builder()
                .documents(List.of(perfectDoc))
                .ministryLandsConfirmed(true)
                .build();

        assertTrue(engine.computeOwnershipScore(verif) <= 100);
    }
}
