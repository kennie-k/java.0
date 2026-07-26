package com.kenyarealestate.verification.service;

import com.kenyarealestate.verification.client.PropertyServiceClient;
import com.kenyarealestate.verification.kafka.VerificationEventPublisher;
import com.kenyarealestate.verification.service.TrustStatusService;
import com.kenyarealestate.verification.client.SmileIdentityClient;
import com.kenyarealestate.verification.dto.identity.*;
import com.kenyarealestate.verification.entity.*;
import com.kenyarealestate.verification.enums.*;
import com.kenyarealestate.verification.exception.*;
import com.kenyarealestate.verification.repository.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@Transactional
public class SellerIdentityVerificationService {

    private final SellerIdentityVerificationRepository identityRepo;
    private final SellerIdentityDocumentRepository docRepo;
    private final VerificationFraudFlagRepository fraudRepo;
    private final AuditService auditService;
    private final ScoringEngine scoringEngine;
    private final PropertyServiceClient propertyClient;
    private final DocumentAnalysisService documentAnalysisService;
    private final SmileIdentityClient smileIdentityClient;
    private final VerificationEventPublisher eventPublisher;
    private final TrustStatusService trustStatusService;

    @Value("${verification.max-fraud-strikes:3}")
    private int maxFraudStrikes;

    @Value("${verification.identity-expiry-years:2}")
    private int identityExpiryYears;

    @Value("${verification.identity-auto-approve-score:85}")
    private int autoApproveScore;

    @Value("${verification.biometric-hard-block:true}")
    private boolean biometricHardBlock;

    private static final List<IdentityDocumentCategory> REQUIRED_DOCS = Arrays.asList(
            IdentityDocumentCategory.NATIONAL_ID_FRONT,
            IdentityDocumentCategory.NATIONAL_ID_BACK,
            IdentityDocumentCategory.KRA_PIN_CERTIFICATE,
            IdentityDocumentCategory.SELFIE_WITH_ID
    );

    public SellerIdentityVerificationService(
            SellerIdentityVerificationRepository identityRepo,
            SellerIdentityDocumentRepository docRepo,
            VerificationFraudFlagRepository fraudRepo,
            AuditService auditService,
            ScoringEngine scoringEngine,
            PropertyServiceClient propertyClient,
            DocumentAnalysisService documentAnalysisService,
            SmileIdentityClient smileIdentityClient,
            VerificationEventPublisher eventPublisher,
            TrustStatusService trustStatusService) {
        this.identityRepo            = identityRepo;
        this.docRepo                 = docRepo;
        this.fraudRepo               = fraudRepo;
        this.auditService            = auditService;
        this.scoringEngine           = scoringEngine;
        this.propertyClient          = propertyClient;
        this.documentAnalysisService = documentAnalysisService;
        this.smileIdentityClient     = smileIdentityClient;
        this.eventPublisher          = eventPublisher;
        this.trustStatusService      = trustStatusService;
    }

    public IdentityVerificationResponse startVerification(UUID userId) {
        if (identityRepo.existsByUserId(userId)) {
            SellerIdentityVerification existing = identityRepo.findByUserId(userId).orElseThrow();
            if (existing.isPermanentlyBanned())
                throw new VerificationException(
                        "This account is permanently banned from verification. " +
                                "Reason: " + existing.getBanReason());
            if (existing.getStatus() == IdentityVerificationStatus.APPROVED)
                throw new VerificationException("Your identity is already verified.");
            return toResponse(existing);
        }
        SellerIdentityVerification verif = identityRepo.save(
                SellerIdentityVerification.builder().userId(userId).build());
        auditService.log(verif.getId(), "IDENTITY", "STARTED", userId, "SELLER", null, "DRAFT", null);
        return toResponse(verif);
    }

    public IdentityVerificationResponse uploadDocument(UUID userId, UploadIdentityDocumentRequest req) {
        SellerIdentityVerification verif = getVerifByUserId(userId);

        if (verif.isPermanentlyBanned())
            throw new VerificationException("Account permanently banned. Reason: " + verif.getBanReason());
        if (verif.getStatus() == IdentityVerificationStatus.APPROVED)
            throw new VerificationException("Identity already approved.");
        if (verif.getStatus() == IdentityVerificationStatus.HUMAN_REVIEW
                || verif.getStatus() == IdentityVerificationStatus.AI_SCREENING)
            throw new VerificationException("Cannot upload while status is " + verif.getStatus() + ".");

        String serverHash = documentAnalysisService.computeSha256FromUrl(req.getDocumentUrl());
        if (serverHash == null)
            throw new VerificationException(
                    "Could not retrieve document from URL. Ensure the URL is publicly accessible.");

        if (docRepo.existsByFileHashSha256(serverHash)) {
            flagFraud(verif, "DUPLICATE_DOCUMENT_HASH", req.getDocumentCategory().name(),
                    serverHash, "Document hash already exists in system");
            throw new DuplicateDocumentException(
                    "This exact document already exists in the system. This incident is logged.");
        }

        if (fraudRepo.existsByDocumentHash(serverHash)) {
            flagFraud(verif, "FRAUDULENT_DOCUMENT_REUSE", req.getDocumentCategory().name(),
                    serverHash, "Document hash matches a previously flagged document");
            checkAndApplyBan(verif);
            throw new DuplicateDocumentException(
                    "This document has been flagged in our fraud detection system.");
        }

        verif.getDocuments().removeIf(d -> d.getDocumentCategory() == req.getDocumentCategory());

        SellerIdentityDocument doc = SellerIdentityDocument.builder()
                .sellerIdentityVerification(verif)
                .documentCategory(req.getDocumentCategory())
                .documentUrl(req.getDocumentUrl())
                .fileHashSha256(serverHash)
                .fileSizeBytes(req.getFileSizeBytes())
                .mimeType(req.getMimeType())
                .isRequired(REQUIRED_DOCS.contains(req.getDocumentCategory()))
                .build();
        verif.getDocuments().add(doc);

        if (verif.getStatus() == IdentityVerificationStatus.REQUIRES_RESUBMISSION)
            verif.setStatus(IdentityVerificationStatus.DRAFT);

        verif = identityRepo.save(verif);
        auditService.log(verif.getId(), "IDENTITY", "DOCUMENT_UPLOADED", userId, "SELLER",
                null, null, "Category: " + req.getDocumentCategory()
                        + " Hash: " + serverHash.substring(0, 12) + "...");
        return toResponse(verif);
    }

    public IdentityVerificationResponse submitForReview(UUID userId) {
        SellerIdentityVerification verif = getVerifByUserId(userId);

        if (verif.isPermanentlyBanned())
            throw new VerificationException("Account permanently banned.");
        if (verif.getStatus() == IdentityVerificationStatus.APPROVED)
            throw new VerificationException("Already approved.");
        if (verif.getStatus() == IdentityVerificationStatus.HUMAN_REVIEW
                || verif.getStatus() == IdentityVerificationStatus.AI_SCREENING)
            throw new VerificationException("Already under review.");

        List<String> missing = getMissingRequiredDocs(verif);
        if (!missing.isEmpty())
            throw new VerificationException("Missing required documents: " + String.join(", ", missing));

        runBiometricCheck(verif, userId);

        verif = identityRepo.findByUserId(userId).orElseThrow();
        if (verif.isPermanentlyBanned())
            throw new VerificationException(
                    "Submission blocked: biometric verification failed critically. " +
                            "Reason: " + verif.getBanReason());

        if (biometricHardBlock && verif.getFraudStrikeCount() > 0
                && smileIdentityClient.isEnabled()) {
            throw new VerificationException(
                    "Submission blocked: biometric checks failed. " +
                            "Your National ID could not be verified or your face does not match the ID. " +
                            "Please ensure you are submitting genuine documents.");
        }

        String prev = verif.getStatus().name();

        if (!documentAnalysisService.isEnabled()) {
            verif.setStatus(IdentityVerificationStatus.HUMAN_REVIEW);
            verif = identityRepo.save(verif);
            auditService.log(verif.getId(), "IDENTITY", "SUBMITTED_NO_AI_ANALYSIS", userId, "SELLER",
                    prev, "HUMAN_REVIEW",
                    "Document analysis provider disabled - sent directly to human review, no automated screening ran");
            return toResponse(verif);
        }

        verif.setStatus(IdentityVerificationStatus.SUBMITTED);
        verif = identityRepo.save(verif);
        auditService.log(verif.getId(), "IDENTITY", "SUBMITTED", userId, "SELLER", prev, "SUBMITTED", null);
        return toResponse(verif);
    }

    public IdentityVerificationResponse processAiScreening(UUID verificationId, AiScreeningRequest req) {
        SellerIdentityVerification verif = identityRepo.findById(verificationId)
                .orElseThrow(() -> new NotFoundException("Verification not found: " + verificationId));

        SellerIdentityDocument doc = verif.getDocuments().stream()
                .filter(d -> d.getId().equals(req.getDocumentId()))
                .findFirst()
                .orElseThrow(() -> new NotFoundException("Document not found: " + req.getDocumentId()));

        doc.setAiAuthenticityScore(req.getAiAuthenticityScore());
        doc.setAiTamperDetected(req.getAiTamperDetected());
        doc.setAiMetadataClean(req.getAiMetadataClean());
        doc.setAiFontConsistency(req.getAiFontConsistency());
        doc.setAiSignatureDetected(req.getAiSignatureDetected());
        doc.setAiSealDetected(req.getAiSealDetected());
        doc.setAiScreeningNotes(req.getAiScreeningNotes());
        doc.setAiScreenedAt(LocalDateTime.now());

        if (Boolean.TRUE.equals(req.getAiTamperDetected())) {
            flagFraud(verif, "DOCUMENT_TAMPER_DETECTED", doc.getDocumentCategory().name(),
                    doc.getFileHashSha256(), "AI screening detected tampering");
            checkAndApplyBan(verif);
            verif.setStatus(IdentityVerificationStatus.REJECTED);
            verif.setRejectionReason(
                    "Automated screening detected TAMPERING on: " + doc.getDocumentCategory().name()
                            + ". Permanently rejected. Submitting falsified documents is a criminal offence.");
            identityRepo.save(verif);
            auditService.log(verif.getId(), "IDENTITY", "AUTO_REJECTED_TAMPER",
                    null, "SYSTEM", "AI_SCREENING", "REJECTED",
                    "Doc: " + doc.getDocumentCategory() + " | Strikes: " + verif.getFraudStrikeCount());
            return toResponse(verif);
        }

        int score = scoringEngine.computeIdentityScore(verif);
        verif.setIdentityScore(score);
        verif.setBadgeLevel(scoringEngine.computeBadgeLevel(score));

        boolean allDone = verif.getDocuments().stream()
                .filter(d -> Boolean.TRUE.equals(d.getIsRequired()))
                .allMatch(d -> d.getAiScreenedAt() != null);

        if (allDone) {
            if (score >= autoApproveScore && verif.getFraudStrikeCount() == 0) {
                verif.setStatus(IdentityVerificationStatus.APPROVED);
                verif.setReviewedAt(LocalDateTime.now());
                verif.setExpiresAt(LocalDateTime.now().plusYears(identityExpiryYears));
                verif = identityRepo.save(verif);
                auditService.log(verif.getId(), "IDENTITY", "AUTO_APPROVED",
                        null, "SYSTEM", "AI_SCREENING", "APPROVED",
                        "Score " + score + " >= threshold " + autoApproveScore + ". Zero fraud strikes.");
                propertyClient.activateAllListingsForSeller(verif.getUserId(), null);
                eventPublisher.publishIdentityApproved(verif.getUserId(), verif.getId(),
                        LocalDateTime.now(), verif.getExpiresAt());
                trustStatusService.evictCache(verif.getUserId());
            } else {
                verif.setStatus(IdentityVerificationStatus.HUMAN_REVIEW);
                verif = identityRepo.save(verif);
                auditService.log(verif.getId(), "IDENTITY", "SENT_TO_HUMAN_REVIEW",
                        null, "SYSTEM", "AI_SCREENING", "HUMAN_REVIEW",
                        "Score: " + score + " | Badge: " + verif.getBadgeLevel()
                                + " | Strikes: " + verif.getFraudStrikeCount());
            }
        } else {
            verif.setStatus(IdentityVerificationStatus.AI_SCREENING);
            identityRepo.save(verif);
        }

        return toResponse(verif);
    }

    public IdentityVerificationResponse adminReview(UUID verificationId,
                                                    AdminIdentityReviewRequest req,
                                                    UUID adminId, String adminJwt) {
        SellerIdentityVerification verif = identityRepo.findById(verificationId)
                .orElseThrow(() -> new NotFoundException("Verification not found: " + verificationId));

        String prev = verif.getStatus().name();
        switch (req.getDecision().toUpperCase()) {
            case "APPROVED" -> {
                verif.setStatus(IdentityVerificationStatus.APPROVED);
                verif.setReviewedBy(adminId);
                verif.setReviewedAt(LocalDateTime.now());
                verif.setExpiresAt(LocalDateTime.now().plusYears(identityExpiryYears));
                propertyClient.activateAllListingsForSeller(verif.getUserId(), adminJwt);
                eventPublisher.publishIdentityApproved(verif.getUserId(), verif.getId(),
                        LocalDateTime.now(), verif.getExpiresAt());
                trustStatusService.evictCache(verif.getUserId());
            }
            case "REJECTED" -> {
                verif.setStatus(IdentityVerificationStatus.REJECTED);
                verif.setRejectionReason(req.getNotes());
                verif.setReviewedBy(adminId);
                verif.setReviewedAt(LocalDateTime.now());
                trustStatusService.evictCache(verif.getUserId());
            }
            case "REQUIRES_RESUBMISSION" -> {
                verif.setStatus(IdentityVerificationStatus.REQUIRES_RESUBMISSION);
                verif.setResubmissionNotes(req.getResubmissionNotes());
                verif.setReviewedBy(adminId);
                verif.setReviewedAt(LocalDateTime.now());
                trustStatusService.evictCache(verif.getUserId());
            }
            default -> throw new VerificationException(
                    "Invalid decision. Must be APPROVED, REJECTED, or REQUIRES_RESUBMISSION");
        }

        verif = identityRepo.save(verif);
        auditService.log(verif.getId(), "IDENTITY", "ADMIN_DECISION_" + req.getDecision().toUpperCase(),
                adminId, "ADMIN", prev, verif.getStatus().name(), req.getNotes());
        return toResponse(verif);
    }

    @Transactional(readOnly = true)
    public IdentityVerificationResponse getMyVerification(UUID userId) {
        return toResponse(getVerifByUserId(userId));
    }

    @Transactional(readOnly = true)
    public Page<IdentityVerificationResponse> getQueue(IdentityVerificationStatus status, Pageable pageable) {
        return identityRepo.findByStatus(status, pageable).map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public boolean isIdentityVerified(UUID userId) {
        return identityRepo.findByUserId(userId).map(v -> {
            if (v.getStatus() != IdentityVerificationStatus.APPROVED) return false;
            if (v.getExpiresAt() != null && v.getExpiresAt().isBefore(LocalDateTime.now())) return false;
            return !v.isPermanentlyBanned();
        }).orElse(false);
    }

    private void runBiometricCheck(SellerIdentityVerification verif, UUID userId) {
        SellerIdentityDocument idFront = verif.getDocuments().stream()
                .filter(d -> d.getDocumentCategory() == IdentityDocumentCategory.NATIONAL_ID_FRONT)
                .findFirst().orElse(null);
        SellerIdentityDocument selfie = verif.getDocuments().stream()
                .filter(d -> d.getDocumentCategory() == IdentityDocumentCategory.SELFIE_WITH_ID)
                .findFirst().orElse(null);

        if (idFront == null || selfie == null) return;

        SmileIdentityClient.BiometricVerificationResult result =
                smileIdentityClient.verifyNationalId(null, selfie.getDocumentUrl(), idFront.getDocumentUrl());

        if (result.resultCode() == null
                || result.resultCode().equals("PENDING")
                || result.resultCode().equals("ERROR")) {
            auditService.log(verif.getId(), "IDENTITY", "BIOMETRIC_CHECK_SKIPPED", userId, "SYSTEM",
                    null, null, "Smile Identity unavailable: " + result.resultText());
            return;
        }

        boolean biometricPassed = result.idValid() && result.faceMatch();

        if (!result.idValid()) {
            flagFraud(verif, "INVALID_NATIONAL_ID", "NATIONAL_ID_FRONT", null,
                    "Smile Identity: National ID could not be verified. " + result.resultText());
        }
        if (!result.faceMatch()) {
            flagFraud(verif, "FACE_MISMATCH", "SELFIE_WITH_ID", null,
                    "Smile Identity: Face does not match ID. Confidence: " + result.faceMatchConfidence());
        }

        if (!biometricPassed) checkAndApplyBan(verif);

        auditService.log(verif.getId(), "IDENTITY", "BIOMETRIC_CHECK_COMPLETE", userId, "SYSTEM",
                null, null,
                "ID valid: " + result.idValid()
                        + " | Face match: " + result.faceMatch()
                        + " | Confidence: " + result.faceMatchConfidence()
                        + " | Blocked: " + (!biometricPassed && biometricHardBlock));
    }

    private void flagFraud(SellerIdentityVerification verif, String fraudType,
                           String docCategory, String docHash, String details) {
        fraudRepo.save(VerificationFraudFlag.builder()
                .userId(verif.getUserId()).verificationId(verif.getId())
                .verificationTrack("IDENTITY").fraudType(fraudType)
                .documentCategory(docCategory).documentHash(docHash)
                .details(details).build());
        verif.setFraudStrikeCount(verif.getFraudStrikeCount() + 1);
        identityRepo.save(verif);
    }

    private void checkAndApplyBan(SellerIdentityVerification verif) {
        if (verif.getFraudStrikeCount() >= maxFraudStrikes) {
            verif.setPermanentlyBanned(true);
            verif.setBanReason(
                    "Permanently banned after " + verif.getFraudStrikeCount() + " fraud strikes. "
                            + "Submitting falsified documents is a criminal offence under Kenya Penal Code Cap 63.");
            identityRepo.save(verif);
            auditService.log(verif.getId(), "IDENTITY", "ACCOUNT_PERMANENTLY_BANNED",
                    null, "SYSTEM", null, "BANNED",
                    "Total fraud strikes: " + verif.getFraudStrikeCount());
        }
    }

    private SellerIdentityVerification getVerifByUserId(UUID userId) {
        return identityRepo.findByUserId(userId)
                .orElseThrow(() -> new NotFoundException(
                        "No identity verification found. Call POST /api/verification/identity/start first."));
    }

    private List<String> getMissingRequiredDocs(SellerIdentityVerification verif) {
        List<String> uploaded = verif.getDocuments().stream()
                .map(d -> d.getDocumentCategory().name()).collect(Collectors.toList());
        return REQUIRED_DOCS.stream().map(Enum::name)
                .filter(r -> !uploaded.contains(r)).collect(Collectors.toList());
    }

    private IdentityVerificationResponse toResponse(SellerIdentityVerification v) {
        List<IdentityDocumentResponse> docs = v.getDocuments().stream()
                .map(d -> IdentityDocumentResponse.builder()
                        .id(d.getId()).documentCategory(d.getDocumentCategory())
                        .documentUrl(d.getDocumentUrl())
                        .aiAuthenticityScore(d.getAiAuthenticityScore())
                        .aiTamperDetected(d.getAiTamperDetected())
                        .aiSignatureDetected(d.getAiSignatureDetected())
                        .aiSealDetected(d.getAiSealDetected())
                        .aiScreeningNotes(d.getAiScreeningNotes())
                        .humanVerified(d.getHumanVerified())
                        .humanReviewNotes(d.getHumanReviewNotes())
                        .uploadedAt(d.getUploadedAt()).build())
                .collect(Collectors.toList());

        boolean expired = v.getExpiresAt() != null && v.getExpiresAt().isBefore(LocalDateTime.now());

        return IdentityVerificationResponse.builder()
                .id(v.getId()).userId(v.getUserId()).status(v.getStatus())
                .identityScore(v.getIdentityScore()).badgeLevel(v.getBadgeLevel())
                .rejectionReason(v.getRejectionReason()).resubmissionNotes(v.getResubmissionNotes())
                .expiresAt(v.getExpiresAt()).expired(expired)
                .createdAt(v.getCreatedAt()).updatedAt(v.getUpdatedAt())
                .documents(docs).missingRequiredDocuments(getMissingRequiredDocs(v))
                .fraudStrikeCount(v.getFraudStrikeCount()).permanentlyBanned(v.isPermanentlyBanned())
                .build();
    }
}
