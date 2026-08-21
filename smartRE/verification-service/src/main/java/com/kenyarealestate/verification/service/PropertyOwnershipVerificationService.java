package com.kenyarealestate.verification.service;

import com.kenyarealestate.verification.client.ArdhisasaClient;
import com.kenyarealestate.verification.kafka.VerificationEventPublisher;
import com.kenyarealestate.verification.client.PropertyServiceClient;
import com.kenyarealestate.verification.dto.ownership.*;
import com.kenyarealestate.verification.entity.*;
import com.kenyarealestate.verification.enums.*;
import com.kenyarealestate.verification.exception.*;
import com.kenyarealestate.verification.repository.*;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@Transactional
public class PropertyOwnershipVerificationService {

    private final PropertyOwnershipVerificationRepository ownershipRepo;
    private final PropertyOwnershipDocumentRepository docRepo;
    private final SellerIdentityVerificationRepository identityRepo;
    private final OwnershipDocumentRequirementRepository requirementRepo;
    private final VerificationFraudFlagRepository fraudRepo;
    private final AuditService auditService;
    private final ScoringEngine scoringEngine;
    private final PropertyServiceClient propertyClient;
    private final DocumentAnalysisService documentAnalysisService;
    private final ArdhisasaClient ardhisasaClient;
    private final VerificationEventPublisher eventPublisher;
    private final TrustStatusService trustStatusService;
    private final FraudFlagService fraudFlagService;

    public PropertyOwnershipVerificationService(
            PropertyOwnershipVerificationRepository ownershipRepo,
            PropertyOwnershipDocumentRepository docRepo,
            SellerIdentityVerificationRepository identityRepo,
            OwnershipDocumentRequirementRepository requirementRepo,
            VerificationFraudFlagRepository fraudRepo,
            AuditService auditService,
            ScoringEngine scoringEngine,
            PropertyServiceClient propertyClient,
            DocumentAnalysisService documentAnalysisService,
            ArdhisasaClient ardhisasaClient,
            VerificationEventPublisher eventPublisher,
            TrustStatusService trustStatusService,
            FraudFlagService fraudFlagService) {
        this.ownershipRepo           = ownershipRepo;
        this.docRepo                 = docRepo;
        this.identityRepo            = identityRepo;
        this.requirementRepo         = requirementRepo;
        this.fraudRepo               = fraudRepo;
        this.auditService            = auditService;
        this.scoringEngine           = scoringEngine;
        this.propertyClient          = propertyClient;
        this.documentAnalysisService = documentAnalysisService;
        this.ardhisasaClient         = ardhisasaClient;
        this.eventPublisher          = eventPublisher;
        this.trustStatusService      = trustStatusService;
        this.fraudFlagService        = fraudFlagService;
    }

    public OwnershipVerificationResponse startOwnershipVerification(UUID userId,
                                                                    StartOwnershipVerificationRequest req) {
        SellerIdentityVerification identity = identityRepo.findByUserId(userId)
                .orElseThrow(() -> new VerificationException(
                        "Complete Seller Identity Verification first."));

        if (identity.getStatus() != IdentityVerificationStatus.APPROVED)
            throw new VerificationException(
                    "Your identity verification is " + identity.getStatus()
                            + ". It must be APPROVED before verifying property ownership.");

        if (identity.getExpiresAt() != null && identity.getExpiresAt().isBefore(LocalDateTime.now()))
            throw new VerificationException(
                    "Your identity verification has expired. Please renew your identity verification first.");

        if (ownershipRepo.existsByPropertyIdAndStatus(req.getPropertyId(), OwnershipVerificationStatus.APPROVED))
            throw new VerificationException("This property already has an approved ownership verification.");

        rejectIfLandIdentifierReused("parcel number", req.getParcelNumber(), req.getPropertyId(),
                ownershipRepo::existsByParcelNumberAndPropertyIdNotAndStatusNot);
        rejectIfLandIdentifierReused("title deed number", req.getTitleDeedNumber(), req.getPropertyId(),
                ownershipRepo::existsByTitleDeedNumberAndPropertyIdNotAndStatusNot);
        rejectIfLandIdentifierReused("LR number", req.getLrNumber(), req.getPropertyId(),
                ownershipRepo::existsByLrNumberAndPropertyIdNotAndStatusNot);

        PropertyOwnershipVerification verif;
        try {
            verif = ownershipRepo.save(
                    PropertyOwnershipVerification.builder()
                            .propertyId(req.getPropertyId())
                            .sellerIdentityVerification(identity)
                            .propertyType(req.getPropertyType())
                            .county(req.getCounty())
                            .parcelNumber(req.getParcelNumber())
                            .titleDeedNumber(req.getTitleDeedNumber())
                            .lrNumber(req.getLrNumber())
                            .build());
        } catch (DataIntegrityViolationException e) {
            String msg = e.getMostSpecificCause().getMessage();
            if (msg != null && (msg.contains("uk_pov_parcel_number") || msg.contains("uk_pov_title_deed_number")
                    || msg.contains("uk_pov_lr_number"))) {
                throw new DuplicateDocumentException(
                        "This parcel/title deed/LR number is already under verification for a different property.");
            }
            throw e;
        }

        auditService.log(verif.getId(), "OWNERSHIP", "STARTED", userId, "SELLER", null, "DRAFT",
                "Property: " + req.getPropertyId() + " Type: " + req.getPropertyType());
        return toResponse(verif);
    }

    public OwnershipVerificationResponse uploadDocument(UUID userId, UUID verificationId,
                                                        UploadOwnershipDocumentRequest req) {
        PropertyOwnershipVerification verif = getAndAuthorize(verificationId, userId);

        if (verif.getStatus() == OwnershipVerificationStatus.APPROVED)
            throw new VerificationException("Ownership already approved.");
        if (isUnderReview(verif.getStatus()))
            throw new VerificationException("Cannot upload while status is: " + verif.getStatus());

        String serverHash = documentAnalysisService.computeSha256FromUrl(req.getDocumentUrl());
        if (serverHash == null)
            throw new VerificationException(
                    "Could not retrieve document from URL. Ensure the URL is publicly accessible.");

        if (docRepo.existsByFileHashSha256(serverHash)) {
            flagOwnershipFraud(verif, userId, "DUPLICATE_DOCUMENT_HASH",
                    req.getDocumentCategory().name(), serverHash,
                    "Document hash already exists in the system");
            throw new DuplicateDocumentException(
                    "This document already exists in the system. This incident has been flagged.");
        }

        if (fraudRepo.existsByDocumentHash(serverHash)) {
            flagOwnershipFraud(verif, userId, "FRAUDULENT_DOCUMENT_REUSE",
                    req.getDocumentCategory().name(), serverHash,
                    "Document hash matches a previously flagged document");
            throw new DuplicateDocumentException(
                    "This document has been flagged in our fraud detection system.");
        }

        verif.getDocuments().removeIf(d -> d.getDocumentCategory() == req.getDocumentCategory());

        boolean mandatory = requirementRepo
                .findByPropertyTypeAndIsMandatoryTrue(verif.getPropertyType().name()).stream()
                .anyMatch(r -> r.getDocumentCategory().equals(req.getDocumentCategory().name()));

        PropertyOwnershipDocument doc = PropertyOwnershipDocument.builder()
                .propertyOwnershipVerification(verif)
                .documentCategory(req.getDocumentCategory())
                .documentUrl(req.getDocumentUrl())
                .fileHashSha256(serverHash)
                .fileSizeBytes(req.getFileSizeBytes())
                .mimeType(req.getMimeType())
                .isRequired(mandatory)
                .build();
        verif.getDocuments().add(doc);

        if (verif.getStatus() == OwnershipVerificationStatus.REQUIRES_RESUBMISSION)
            verif.setStatus(OwnershipVerificationStatus.DRAFT);

        try {
            verif = ownershipRepo.save(verif);

            ownershipRepo.flush();
        } catch (DataIntegrityViolationException e) {
            if (!isDuplicateHashViolation(e)) throw e;

            flagOwnershipFraud(verif, userId, "DUPLICATE_DOCUMENT_HASH",
                    req.getDocumentCategory().name(), serverHash,
                    "Concurrent duplicate upload detected at database level");
            throw new DuplicateDocumentException(
                    "This document already exists in the system. This incident has been flagged.");
        }

        auditService.log(verif.getId(), "OWNERSHIP", "DOCUMENT_UPLOADED", userId, "SELLER",
                null, null, "Category: " + req.getDocumentCategory()
                        + " Hash: " + serverHash.substring(0, 12) + "...");
        return toResponse(verif);
    }

    public OwnershipVerificationResponse deleteDocument(UUID userId, UUID verificationId, UUID documentId) {
        PropertyOwnershipVerification verif = getAndAuthorize(verificationId, userId);

        if (verif.getStatus() == OwnershipVerificationStatus.APPROVED)
            throw new VerificationException("Ownership already approved.");
        if (verif.getStatus() != OwnershipVerificationStatus.DRAFT
                && verif.getStatus() != OwnershipVerificationStatus.REJECTED)
            throw new VerificationException("Cannot remove documents while status is: " + verif.getStatus());

        boolean removed = verif.getDocuments().removeIf(d -> d.getId().equals(documentId));
        if (!removed) throw new NotFoundException("Document not found: " + documentId);

        verif = ownershipRepo.save(verif);
        auditService.log(verif.getId(), "OWNERSHIP", "DOCUMENT_REMOVED", userId, "SELLER",
                null, null, "Document: " + documentId);
        return toResponse(verif);
    }

    public OwnershipVerificationResponse submitForReview(UUID userId, UUID verificationId) {
        PropertyOwnershipVerification verif = getAndAuthorize(verificationId, userId);

        if (verif.getStatus() == OwnershipVerificationStatus.APPROVED)
            throw new VerificationException("Already approved.");
        if (isUnderReview(verif.getStatus()))
            throw new VerificationException("Already under review.");

        List<DocumentRequirementResponse> missing = getMissingMandatoryDocs(verif);
        if (!missing.isEmpty()) {
            String list = missing.stream()
                    .map(r -> r.getDocumentCategory() + " (" + r.getKenyaLawRef() + ")")
                    .collect(Collectors.joining("; "));
            throw new VerificationException("Missing mandatory documents under Kenyan law: " + list);
        }

        String prev = verif.getStatus().name();

        if (!documentAnalysisService.isEnabled()) {
            verif.setStatus(OwnershipVerificationStatus.MINISTRY_LANDS_CHECK);
            verif = ownershipRepo.save(verif);
            auditService.log(verif.getId(), "OWNERSHIP", "SUBMITTED_NO_AI_ANALYSIS", userId, "SELLER",
                    prev, "MINISTRY_LANDS_CHECK",
                    "Document analysis provider disabled - sent directly to manual ministry check, no automated screening ran");
            return toResponse(verif);
        }

        verif.setStatus(OwnershipVerificationStatus.AI_SCREENING);
        verif = ownershipRepo.save(verif);
        auditService.log(verif.getId(), "OWNERSHIP", "SUBMITTED_FOR_AI_ANALYSIS", userId, "SELLER",
                prev, "AI_SCREENING", null);

        // Run screening synchronously against every required document instead of
        // parking at SUBMITTED and waiting for an external caller to hit the
        // internal /ai-screening endpoint — nothing in this system ever did.
        for (PropertyOwnershipDocument doc : List.copyOf(verif.getDocuments())) {
            if (!Boolean.TRUE.equals(doc.getIsRequired())) continue;

            DocumentAnalysisService.DocumentAnalysisResult analysis =
                    documentAnalysisService.analyseDocument(doc.getDocumentUrl(), doc.getDocumentCategory().name());

            OwnershipDocumentLegalCheckRequest screeningReq = new OwnershipDocumentLegalCheckRequest();
            screeningReq.setDocumentId(doc.getId());
            screeningReq.setAiAuthenticityScore(analysis.authenticityScore());
            screeningReq.setAiTamperDetected(analysis.tamperDetected());
            screeningReq.setAiAlterationDetected(analysis.alterationDetected());
            screeningReq.setAiFontConsistency(analysis.fontConsistency());
            screeningReq.setAiDateSequenceValid(analysis.dateSequenceValid());
            screeningReq.setAiMetadataClean(analysis.metadataClean());
            screeningReq.setAiScreeningNotes(analysis.notes());

            applyAiScreeningResult(verif, doc, screeningReq);
            if (verif.getStatus() == OwnershipVerificationStatus.REJECTED) break;
        }

        return toResponse(verif);
    }

    public OwnershipVerificationResponse processAiScreening(UUID verificationId,
                                                            OwnershipDocumentLegalCheckRequest req) {
        PropertyOwnershipVerification verif = ownershipRepo.findById(verificationId)
                .orElseThrow(() -> new NotFoundException("Verification not found: " + verificationId));

        PropertyOwnershipDocument doc = verif.getDocuments().stream()
                .filter(d -> d.getId().equals(req.getDocumentId()))
                .findFirst()
                .orElseThrow(() -> new NotFoundException("Document not found: " + req.getDocumentId()));

        applyAiScreeningResult(verif, doc, req);
        return toResponse(verif);
    }

    private void applyAiScreeningResult(PropertyOwnershipVerification verif, PropertyOwnershipDocument doc,
                                        OwnershipDocumentLegalCheckRequest req) {
        doc.setAiAuthenticityScore(req.getAiAuthenticityScore());
        doc.setAiTamperDetected(req.getAiTamperDetected());
        doc.setAiAlterationDetected(req.getAiAlterationDetected());
        doc.setAiFontConsistency(req.getAiFontConsistency());
        doc.setAiDateSequenceValid(req.getAiDateSequenceValid());
        doc.setAiMetadataClean(req.getAiMetadataClean());
        doc.setAiScreeningNotes(req.getAiScreeningNotes());
        doc.setAiScreenedAt(LocalDateTime.now());

        if (Boolean.TRUE.equals(req.getAiTamperDetected()) || Boolean.TRUE.equals(req.getAiAlterationDetected())) {
            String fraudType = Boolean.TRUE.equals(req.getAiTamperDetected()) ? "TAMPERING" : "ALTERATION";
            UUID ownerId = verif.getSellerIdentityVerification().getUserId();
            flagOwnershipFraud(verif, ownerId, "DOCUMENT_" + fraudType,
                    doc.getDocumentCategory().name(), doc.getFileHashSha256(),
                    "AI screening detected " + fraudType);

            verif.setStatus(OwnershipVerificationStatus.REJECTED);
            verif.setRejectionReason(
                    "FRAUD DETECTED — Automated analysis identified " + fraudType
                            + " on: " + doc.getDocumentCategory().name()
                            + ". Submission permanently rejected. "
                            + "Falsifying property documents is a criminal offence under "
                            + "the Kenya Penal Code Cap 63 and the Land Registration Act 2012.");
            ownershipRepo.save(verif);
            auditService.log(verif.getId(), "OWNERSHIP", "AUTO_REJECTED_FRAUD_" + fraudType,
                    null, "SYSTEM", "AI_SCREENING", "REJECTED",
                    fraudType + " on: " + doc.getDocumentCategory().name());
            return;
        }

        verif.setOwnershipScore(scoringEngine.computeOwnershipScore(verif));

        boolean allDone = verif.getDocuments().stream()
                .filter(d -> Boolean.TRUE.equals(d.getIsRequired()))
                .allMatch(d -> d.getAiScreenedAt() != null);

        if (allDone) {
            runArdhisasaCheck(verif);
        } else {
            verif.setStatus(OwnershipVerificationStatus.AI_SCREENING);
            ownershipRepo.save(verif);
        }
    }

    private void runArdhisasaCheck(PropertyOwnershipVerification verif) {
        String parcel = verif.getParcelNumber();
        String titleDeed = verif.getTitleDeedNumber();

        ArdhisasaClient.LandSearchResult result = null;

        if (parcel != null && verif.getCounty() != null) {
            result = ardhisasaClient.searchParcel(parcel, verif.getCounty());
        } else if (titleDeed != null) {
            result = ardhisasaClient.searchByTitleDeed(titleDeed);
        }

        if (result != null && result.found()) {
            if (!result.titleValid()) {
                verif.setStatus(OwnershipVerificationStatus.REJECTED);
                verif.setRejectionReason(
                        "Ardhisasa verification failed: title deed is not valid in the Kenya land registry. "
                                + result.resultMessage());
                ownershipRepo.save(verif);
                auditService.log(verif.getId(), "OWNERSHIP", "ARDHISASA_TITLE_INVALID",
                        null, "SYSTEM", "AI_SCREENING", "REJECTED", result.resultMessage());
                return;
            }

            verif.setMinistryLandsConfirmed(true);

            if (!result.encumbranceClear()) {
                verif.setEncumbranceClear(false);
                verif.setStatus(OwnershipVerificationStatus.REJECTED);
                verif.setRejectionReason(
                        "Encumbrance check failed: property has active "
                                + (result.hasActiveCaveats() ? "caveats " : "")
                                + (result.hasActiveCharges() ? "charges " : "")
                                + (result.hasCourtOrders() ? "court orders " : "")
                                + "on the Kenya land registry.");
                ownershipRepo.save(verif);
                auditService.log(verif.getId(), "OWNERSHIP", "ENCUMBRANCE_CHECK_FAILED",
                        null, "SYSTEM", "AI_SCREENING", "REJECTED", "Encumbrances detected by Ardhisasa");
                return;
            }

            verif.setEncumbranceClear(true);
            verif.setOwnershipScore(scoringEngine.computeOwnershipScore(verif));
            verif.setStatus(OwnershipVerificationStatus.LEGAL_REVIEW);
            auditService.log(verif.getId(), "OWNERSHIP", "ARDHISASA_CONFIRMED_AUTO",
                    null, "SYSTEM", "AI_SCREENING", "LEGAL_REVIEW",
                    "Ardhisasa confirmed parcel. Registered owner: " + result.registeredOwner()
                            + ". Score: " + verif.getOwnershipScore());
        } else {
            verif.setStatus(OwnershipVerificationStatus.MINISTRY_LANDS_CHECK);
            auditService.log(verif.getId(), "OWNERSHIP", "AI_SCREENING_COMPLETE",
                    null, "SYSTEM", "AI_SCREENING", "MINISTRY_LANDS_CHECK",
                    "Ardhisasa API not available or parcel not found — manual ministry check required. "
                            + "Score: " + verif.getOwnershipScore());
        }

        ownershipRepo.save(verif);
    }

    public OwnershipVerificationResponse recordMinistryCheck(UUID verificationId,
                                                             boolean ministryConfirmed,
                                                             UUID adminId, String notes) {
        PropertyOwnershipVerification verif = ownershipRepo.findById(verificationId)
                .orElseThrow(() -> new NotFoundException("Verification not found: " + verificationId));

        if (verif.getStatus() != OwnershipVerificationStatus.MINISTRY_LANDS_CHECK)
            throw new VerificationException(
                    "Ministry check can only be recorded when status is MINISTRY_LANDS_CHECK. Current: "
                            + verif.getStatus());

        verif.setMinistryLandsConfirmed(ministryConfirmed);

        if (!ministryConfirmed) {
            verif.setStatus(OwnershipVerificationStatus.REJECTED);
            verif.setRejectionReason(
                    "Ministry of Lands / Ardhisasa check FAILED. "
                            + "The title deed or parcel number could not be confirmed. Notes: " + notes);
            ownershipRepo.save(verif);
            auditService.log(verif.getId(), "OWNERSHIP", "MINISTRY_CHECK_FAILED",
                    adminId, "ADMIN", "MINISTRY_LANDS_CHECK", "REJECTED", notes);
            return toResponse(verif);
        }

        verif.setStatus(OwnershipVerificationStatus.ENCUMBRANCE_CHECK);
        ownershipRepo.save(verif);
        auditService.log(verif.getId(), "OWNERSHIP", "MINISTRY_CHECK_PASSED",
                adminId, "ADMIN", "MINISTRY_LANDS_CHECK", "ENCUMBRANCE_CHECK", notes);
        return toResponse(verif);
    }

    public OwnershipVerificationResponse recordEncumbranceCheck(UUID verificationId,
                                                                boolean encumbranceClear,
                                                                UUID adminId, String notes) {
        PropertyOwnershipVerification verif = ownershipRepo.findById(verificationId)
                .orElseThrow(() -> new NotFoundException("Verification not found: " + verificationId));

        if (verif.getStatus() != OwnershipVerificationStatus.ENCUMBRANCE_CHECK)
            throw new VerificationException(
                    "Encumbrance check can only be recorded when status is ENCUMBRANCE_CHECK. Current: "
                            + verif.getStatus());

        verif.setEncumbranceClear(encumbranceClear);

        if (!encumbranceClear) {
            verif.setStatus(OwnershipVerificationStatus.REJECTED);
            verif.setRejectionReason(
                    "Encumbrance check FAILED. Property has active caveats, charges, or court orders. Notes: "
                            + notes);
            ownershipRepo.save(verif);
            auditService.log(verif.getId(), "OWNERSHIP", "ENCUMBRANCE_CHECK_FAILED",
                    adminId, "ADMIN", "ENCUMBRANCE_CHECK", "REJECTED", notes);
            return toResponse(verif);
        }

        verif.setOwnershipScore(scoringEngine.computeOwnershipScore(verif));
        verif.setStatus(OwnershipVerificationStatus.LEGAL_REVIEW);
        verif = ownershipRepo.save(verif);
        auditService.log(verif.getId(), "OWNERSHIP", "ENCUMBRANCE_CHECK_PASSED",
                adminId, "ADMIN", "ENCUMBRANCE_CHECK", "LEGAL_REVIEW",
                "Score: " + verif.getOwnershipScore() + " " + notes);
        return toResponse(verif);
    }

    public OwnershipVerificationResponse submitLegalCheckResults(UUID verificationId,
                                                                 OwnershipDocumentLegalCheckRequest req,
                                                                 UUID reviewerId) {
        PropertyOwnershipVerification verif = ownershipRepo.findById(verificationId)
                .orElseThrow(() -> new NotFoundException("Verification not found: " + verificationId));

        if (verif.getStatus() != OwnershipVerificationStatus.LEGAL_REVIEW)
            throw new VerificationException(
                    "Legal check can only be submitted when status is LEGAL_REVIEW. Current: "
                            + verif.getStatus());

        PropertyOwnershipDocument doc = verif.getDocuments().stream()
                .filter(d -> d.getId().equals(req.getDocumentId()))
                .findFirst()
                .orElseThrow(() -> new NotFoundException("Document not found: " + req.getDocumentId()));

        doc.setLcAdvocateStampPresent(req.getLcAdvocateStampPresent());
        doc.setLcAdvocateSignaturePresent(req.getLcAdvocateSignaturePresent());
        doc.setLcCommissionerOathsPresent(req.getLcCommissionerOathsPresent());
        doc.setLcOfficialSealPresent(req.getLcOfficialSealPresent());
        doc.setLcOwnerSignaturePresent(req.getLcOwnerSignaturePresent());
        doc.setLcWitnessSignaturesPresent(req.getLcWitnessSignaturesPresent());
        doc.setLcDatePresent(req.getLcDatePresent());
        doc.setLcParcelNumberMatches(req.getLcParcelNumberMatches());
        doc.setLcOriginalDocumentConfirmed(req.getLcOriginalDocumentConfirmed());
        doc.setHumanLegalApproved(req.getHumanLegalApproved());
        doc.setHumanReviewerId(reviewerId);
        doc.setHumanReviewNotes(req.getHumanReviewNotes());
        doc.setHumanReviewedAt(LocalDateTime.now());

        verif.setOwnershipScore(scoringEngine.computeOwnershipScore(verif));

        boolean allLegalDone = verif.getDocuments().stream()
                .filter(d -> Boolean.TRUE.equals(d.getIsRequired()))
                .allMatch(d -> d.getHumanReviewedAt() != null);

        if (allLegalDone) {
            verif.setStatus(OwnershipVerificationStatus.HUMAN_REVIEW);
            auditService.log(verif.getId(), "OWNERSHIP", "LEGAL_REVIEW_COMPLETE",
                    reviewerId, "LEGAL_REVIEWER", "LEGAL_REVIEW", "HUMAN_REVIEW",
                    "Final score: " + verif.getOwnershipScore());
        }

        return toResponse(ownershipRepo.save(verif));
    }

    public OwnershipVerificationResponse adminFinalDecision(UUID verificationId,
                                                            AdminOwnershipReviewRequest req,
                                                            UUID adminId, String adminJwt) {
        PropertyOwnershipVerification verif = ownershipRepo.findById(verificationId)
                .orElseThrow(() -> new NotFoundException("Verification not found: " + verificationId));

        if (verif.getStatus() != OwnershipVerificationStatus.HUMAN_REVIEW)
            throw new VerificationException(
                    "Final decision requires HUMAN_REVIEW status. Current: " + verif.getStatus());

        String prev = verif.getStatus().name();
        switch (req.getDecision().toUpperCase()) {
            case "APPROVED" -> {
                verif.setStatus(OwnershipVerificationStatus.APPROVED);
                verif.setReviewedBy(adminId);
                verif.setReviewedAt(LocalDateTime.now());
                if (req.getMinistryLandsConfirmed() != null)
                    verif.setMinistryLandsConfirmed(req.getMinistryLandsConfirmed());
                if (req.getEncumbranceClear() != null)
                    verif.setEncumbranceClear(req.getEncumbranceClear());
                propertyClient.markPropertyOwnershipVerified(verif.getPropertyId(), adminJwt,
                        verif.getParcelNumber(), verif.getTitleDeedNumber());
                UUID sellerId = verif.getSellerIdentityVerification().getUserId();
                eventPublisher.publishOwnershipApproved(sellerId, verif.getId(),
                        verif.getPropertyId(), LocalDateTime.now(),
                        verif.getParcelNumber(), verif.getTitleDeedNumber());
                trustStatusService.evictCache(sellerId);
            }
            case "REJECTED" -> {
                verif.setStatus(OwnershipVerificationStatus.REJECTED);
                verif.setRejectionReason(req.getNotes());
                verif.setReviewedBy(adminId);
                verif.setReviewedAt(LocalDateTime.now());
            }
            case "REQUIRES_RESUBMISSION" -> {
                verif.setStatus(OwnershipVerificationStatus.REQUIRES_RESUBMISSION);
                verif.setRejectionReason(req.getNotes());
                verif.setReviewedBy(adminId);
                verif.setReviewedAt(LocalDateTime.now());
            }
            default -> throw new VerificationException(
                    "Invalid decision. Must be APPROVED, REJECTED, or REQUIRES_RESUBMISSION");
        }

        verif = ownershipRepo.save(verif);
        auditService.log(verif.getId(), "OWNERSHIP",
                "ADMIN_FINAL_DECISION_" + req.getDecision().toUpperCase(),
                adminId, "ADMIN", prev, verif.getStatus().name(), req.getNotes());
        return toResponse(verif);
    }

    @Transactional(readOnly = true)
    public OwnershipVerificationResponse getByPropertyId(UUID propertyId) {
        return toResponse(ownershipRepo.findByPropertyId(propertyId)
                .orElseThrow(() -> new NotFoundException(
                        "No ownership verification found for property: " + propertyId)));
    }

    @Transactional(readOnly = true)
    public List<OwnershipVerificationResponse> getByUserId(UUID userId) {
        return ownershipRepo.findBySellerIdentityVerificationUserId(userId)
                .stream().map(this::toResponse).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public Page<OwnershipVerificationResponse> getQueue(OwnershipVerificationStatus status, Pageable pageable) {
        return ownershipRepo.findByStatus(status, pageable).map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public boolean isOwnershipVerified(UUID propertyId) {
        return ownershipRepo.existsByPropertyIdAndStatus(propertyId, OwnershipVerificationStatus.APPROVED);
    }

    @FunctionalInterface
    private interface LandIdentifierCheck {
        boolean exists(String identifier, UUID propertyId, OwnershipVerificationStatus excludedStatus);
    }

    private void rejectIfLandIdentifierReused(String label, String value, UUID propertyId,
                                              LandIdentifierCheck check) {
        if (StringUtils.hasText(value)
                && check.exists(value, propertyId, OwnershipVerificationStatus.REJECTED)) {
            throw new VerificationException(
                    "This " + label + " (" + value + ") is already under verification for a different property.");
        }
    }

    private PropertyOwnershipVerification getAndAuthorize(UUID verificationId, UUID userId) {
        PropertyOwnershipVerification verif = ownershipRepo.findById(verificationId)
                .orElseThrow(() -> new NotFoundException("Verification not found: " + verificationId));
        if (!verif.getSellerIdentityVerification().getUserId().equals(userId))
            throw new VerificationException("Access denied: this verification does not belong to you.");
        return verif;
    }

    private boolean isUnderReview(OwnershipVerificationStatus s) {
        return s == OwnershipVerificationStatus.AI_SCREENING
                || s == OwnershipVerificationStatus.MINISTRY_LANDS_CHECK
                || s == OwnershipVerificationStatus.ENCUMBRANCE_CHECK
                || s == OwnershipVerificationStatus.LEGAL_REVIEW
                || s == OwnershipVerificationStatus.HUMAN_REVIEW;
    }

    private void flagOwnershipFraud(PropertyOwnershipVerification verif, UUID userId,
                                    String fraudType, String docCategory,
                                    String docHash, String details) {
        int strikes = fraudFlagService.flagOwnershipFraud(
                verif.getId(), userId, fraudType, docCategory, docHash, details);
        verif.setFraudStrikeCount(strikes);
    }

    private boolean isDuplicateHashViolation(DataIntegrityViolationException e) {
        String msg = e.getMostSpecificCause().getMessage();
        return msg != null && msg.contains("uk_pod_file_hash");
    }

    private List<DocumentRequirementResponse> getMissingMandatoryDocs(PropertyOwnershipVerification verif) {
        Set<String> uploaded = verif.getDocuments().stream()
                .map(d -> d.getDocumentCategory().name()).collect(Collectors.toSet());
        return requirementRepo.findByPropertyTypeAndIsMandatoryTrue(verif.getPropertyType().name())
                .stream()
                .filter(r -> !uploaded.contains(r.getDocumentCategory()))
                .map(r -> DocumentRequirementResponse.builder()
                        .documentCategory(r.getDocumentCategory())
                        .isMandatory(r.getIsMandatory())
                        .description(r.getDescription())
                        .kenyaLawRef(r.getKenyaLawRef())
                        .uploaded(false).build())
                .collect(Collectors.toList());
    }

    private List<DocumentRequirementResponse> getAllRequiredDocs(PropertyOwnershipVerification verif) {
        Set<String> uploaded = verif.getDocuments().stream()
                .map(d -> d.getDocumentCategory().name()).collect(Collectors.toSet());
        return requirementRepo.findByPropertyType(verif.getPropertyType().name())
                .stream()
                .map(r -> DocumentRequirementResponse.builder()
                        .documentCategory(r.getDocumentCategory())
                        .isMandatory(r.getIsMandatory())
                        .description(r.getDescription())
                        .kenyaLawRef(r.getKenyaLawRef())
                        .uploaded(uploaded.contains(r.getDocumentCategory())).build())
                .collect(Collectors.toList());
    }

    private OwnershipVerificationResponse toResponse(PropertyOwnershipVerification v) {
        List<OwnershipDocumentResponse> docs = v.getDocuments().stream()
                .map(d -> OwnershipDocumentResponse.builder()
                        .id(d.getId()).documentCategory(d.getDocumentCategory()).documentUrl(d.getDocumentUrl())
                        .isRequired(d.getIsRequired())
                        .humanReviewedAt(d.getHumanReviewedAt())
                        .lcAdvocateStampPresent(d.getLcAdvocateStampPresent())
                        .lcAdvocateSignaturePresent(d.getLcAdvocateSignaturePresent())
                        .lcCommissionerOathsPresent(d.getLcCommissionerOathsPresent())
                        .lcOfficialSealPresent(d.getLcOfficialSealPresent())
                        .lcOwnerSignaturePresent(d.getLcOwnerSignaturePresent())
                        .lcWitnessSignaturesPresent(d.getLcWitnessSignaturesPresent())
                        .lcDatePresent(d.getLcDatePresent())
                        .lcParcelNumberMatches(d.getLcParcelNumberMatches())
                        .lcOriginalDocumentConfirmed(d.getLcOriginalDocumentConfirmed())
                        .aiAuthenticityScore(d.getAiAuthenticityScore())
                        .aiTamperDetected(d.getAiTamperDetected())
                        .aiAlterationDetected(d.getAiAlterationDetected())
                        .aiFontConsistency(d.getAiFontConsistency())
                        .aiDateSequenceValid(d.getAiDateSequenceValid())
                        .aiScreeningNotes(d.getAiScreeningNotes())
                        .humanLegalApproved(d.getHumanLegalApproved())
                        .humanReviewNotes(d.getHumanReviewNotes())
                        .uploadedAt(d.getUploadedAt()).build())
                .collect(Collectors.toList());

        return OwnershipVerificationResponse.builder()
                .id(v.getId()).propertyId(v.getPropertyId())
                .sellerIdentityVerificationId(v.getSellerIdentityVerification().getId())
                .status(v.getStatus()).propertyType(v.getPropertyType())
                .county(v.getCounty()).parcelNumber(v.getParcelNumber())
                .titleDeedNumber(v.getTitleDeedNumber()).lrNumber(v.getLrNumber())
                .ownershipScore(v.getOwnershipScore())
                .ministryLandsConfirmed(v.getMinistryLandsConfirmed())
                .encumbranceClear(v.getEncumbranceClear())
                .rejectionReason(v.getRejectionReason())
                .createdAt(v.getCreatedAt()).updatedAt(v.getUpdatedAt())
                .documents(docs)
                .missingDocuments(getMissingMandatoryDocs(v))
                .allRequiredDocuments(getAllRequiredDocs(v))
                .build();
    }
}
