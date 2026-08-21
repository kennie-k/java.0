package com.kenyarealestate.verification.service;

import com.kenyarealestate.verification.client.ArdhisasaClient;
import com.kenyarealestate.verification.client.PropertyServiceClient;
import com.kenyarealestate.verification.dto.ownership.*;
import com.kenyarealestate.verification.entity.*;
import com.kenyarealestate.verification.enums.*;
import com.kenyarealestate.verification.exception.NotFoundException;
import com.kenyarealestate.verification.exception.VerificationException;
import com.kenyarealestate.verification.kafka.VerificationEventPublisher;
import com.kenyarealestate.verification.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Covers the property-ownership verification state machine: the getAndAuthorize IDOR guard
 * (a caller must own the identity verification backing the ownership record), the document
 * upload/duplicate-hash fraud path, and the admin final-decision transitions. This service
 * previously had zero test coverage.
 */
@ExtendWith(MockitoExtension.class)
class PropertyOwnershipVerificationServiceTest {

    @Mock private PropertyOwnershipVerificationRepository ownershipRepo;
    @Mock private PropertyOwnershipDocumentRepository docRepo;
    @Mock private SellerIdentityVerificationRepository identityRepo;
    @Mock private OwnershipDocumentRequirementRepository requirementRepo;
    @Mock private VerificationFraudFlagRepository fraudRepo;
    @Mock private AuditService auditService;
    @Mock private ScoringEngine scoringEngine;
    @Mock private PropertyServiceClient propertyClient;
    @Mock private DocumentAnalysisService documentAnalysisService;
    @Mock private ArdhisasaClient ardhisasaClient;
    @Mock private VerificationEventPublisher eventPublisher;
    @Mock private TrustStatusService trustStatusService;
    @Mock private FraudFlagService fraudFlagService;

    private PropertyOwnershipVerificationService service;

    private static final UUID SELLER_ID = UUID.randomUUID();
    private static final UUID OTHER_USER_ID = UUID.randomUUID();

    @BeforeEach
    void setup() {
        service = new PropertyOwnershipVerificationService(
                ownershipRepo, docRepo, identityRepo, requirementRepo, fraudRepo,
                auditService, scoringEngine, propertyClient, documentAnalysisService,
                ardhisasaClient, eventPublisher, trustStatusService, fraudFlagService);
        lenient().when(ownershipRepo.save(any(PropertyOwnershipVerification.class)))
                .thenAnswer(inv -> inv.getArgument(0));
    }

    private SellerIdentityVerification approvedIdentity() {
        return SellerIdentityVerification.builder()
                .id(UUID.randomUUID())
                .userId(SELLER_ID)
                .status(IdentityVerificationStatus.APPROVED)
                .expiresAt(LocalDateTime.now().plusYears(1))
                .build();
    }

    private PropertyOwnershipVerification verification(SellerIdentityVerification identity,
                                                         OwnershipVerificationStatus status) {
        return PropertyOwnershipVerification.builder()
                .id(UUID.randomUUID())
                .propertyId(UUID.randomUUID())
                .sellerIdentityVerification(identity)
                .status(status)
                .propertyType(PropertyTypeEnum.FREEHOLD)
                .county("Nairobi")
                .build();
    }

    private StartOwnershipVerificationRequest startRequest() {
        StartOwnershipVerificationRequest req = new StartOwnershipVerificationRequest();
        req.setPropertyId(UUID.randomUUID());
        req.setPropertyType(PropertyTypeEnum.FREEHOLD);
        req.setCounty("Nairobi");
        return req;
    }

    // ---------- startOwnershipVerification ----------

    @Test
    void start_rejectsWhenIdentityNotVerifiedYet() {
        when(identityRepo.findByUserId(SELLER_ID)).thenReturn(Optional.empty());

        assertThrows(VerificationException.class,
                () -> service.startOwnershipVerification(SELLER_ID, startRequest()));
        verify(ownershipRepo, never()).save(any());
    }

    @Test
    void start_rejectsWhenIdentityNotApproved() {
        SellerIdentityVerification identity = approvedIdentity();
        identity.setStatus(IdentityVerificationStatus.HUMAN_REVIEW);
        when(identityRepo.findByUserId(SELLER_ID)).thenReturn(Optional.of(identity));

        assertThrows(VerificationException.class,
                () -> service.startOwnershipVerification(SELLER_ID, startRequest()));
    }

    @Test
    void start_rejectsWhenIdentityExpired() {
        SellerIdentityVerification identity = approvedIdentity();
        identity.setExpiresAt(LocalDateTime.now().minusDays(1));
        when(identityRepo.findByUserId(SELLER_ID)).thenReturn(Optional.of(identity));

        assertThrows(VerificationException.class,
                () -> service.startOwnershipVerification(SELLER_ID, startRequest()));
    }

    @Test
    void start_rejectsWhenPropertyAlreadyApproved() {
        SellerIdentityVerification identity = approvedIdentity();
        when(identityRepo.findByUserId(SELLER_ID)).thenReturn(Optional.of(identity));
        StartOwnershipVerificationRequest req = startRequest();
        when(ownershipRepo.existsByPropertyIdAndStatus(req.getPropertyId(), OwnershipVerificationStatus.APPROVED))
                .thenReturn(true);

        assertThrows(VerificationException.class, () -> service.startOwnershipVerification(SELLER_ID, req));
        verify(ownershipRepo, never()).save(any());
    }

    @Test
    void start_succeeds_andAudits() {
        SellerIdentityVerification identity = approvedIdentity();
        when(identityRepo.findByUserId(SELLER_ID)).thenReturn(Optional.of(identity));
        StartOwnershipVerificationRequest req = startRequest();

        var res = service.startOwnershipVerification(SELLER_ID, req);

        assertEquals(OwnershipVerificationStatus.DRAFT, res.getStatus());
        verify(ownershipRepo).save(any(PropertyOwnershipVerification.class));
        verify(auditService).log(any(), eq("OWNERSHIP"), eq("STARTED"), eq(SELLER_ID), eq("SELLER"), any(), eq("DRAFT"), any());
    }

    // ---------- getAndAuthorize IDOR guard (uploadDocument / deleteDocument / submitForReview) ----------

    @Test
    void uploadDocument_rejectsCallerWhoDoesNotOwnTheVerification() {
        SellerIdentityVerification identity = approvedIdentity(); // owned by SELLER_ID
        PropertyOwnershipVerification verif = verification(identity, OwnershipVerificationStatus.DRAFT);
        when(ownershipRepo.findById(verif.getId())).thenReturn(Optional.of(verif));

        UploadOwnershipDocumentRequest req = new UploadOwnershipDocumentRequest();
        req.setDocumentCategory(OwnershipDocumentCategory.TITLE_DEED);
        req.setDocumentUrl("http://localhost:8080/api/documents/files/documents/title_deed/abc.pdf");

        VerificationException ex = assertThrows(VerificationException.class,
                () -> service.uploadDocument(OTHER_USER_ID, verif.getId(), req));

        assertTrue(ex.getMessage().contains("does not belong to you"));
        verify(docRepo, never()).existsByFileHashSha256(any());
        verifyNoInteractions(documentAnalysisService);
    }

    @Test
    void deleteDocument_rejectsCallerWhoDoesNotOwnTheVerification() {
        SellerIdentityVerification identity = approvedIdentity();
        PropertyOwnershipVerification verif = verification(identity, OwnershipVerificationStatus.DRAFT);
        when(ownershipRepo.findById(verif.getId())).thenReturn(Optional.of(verif));

        assertThrows(VerificationException.class,
                () -> service.deleteDocument(OTHER_USER_ID, verif.getId(), UUID.randomUUID()));
        verify(ownershipRepo, never()).save(any());
    }

    @Test
    void submitForReview_rejectsCallerWhoDoesNotOwnTheVerification() {
        SellerIdentityVerification identity = approvedIdentity();
        PropertyOwnershipVerification verif = verification(identity, OwnershipVerificationStatus.DRAFT);
        when(ownershipRepo.findById(verif.getId())).thenReturn(Optional.of(verif));

        assertThrows(VerificationException.class,
                () -> service.submitForReview(OTHER_USER_ID, verif.getId()));
    }

    @Test
    void uploadDocument_allowsTheOwningSeller() {
        SellerIdentityVerification identity = approvedIdentity();
        PropertyOwnershipVerification verif = verification(identity, OwnershipVerificationStatus.DRAFT);
        when(ownershipRepo.findById(verif.getId())).thenReturn(Optional.of(verif));
        when(documentAnalysisService.computeSha256FromUrl(anyString())).thenReturn("a".repeat(64));
        when(docRepo.existsByFileHashSha256(anyString())).thenReturn(false);
        when(fraudRepo.existsByDocumentHash(anyString())).thenReturn(false);
        when(requirementRepo.findByPropertyTypeAndIsMandatoryTrue("FREEHOLD")).thenReturn(List.of());

        UploadOwnershipDocumentRequest req = new UploadOwnershipDocumentRequest();
        req.setDocumentCategory(OwnershipDocumentCategory.TITLE_DEED);
        req.setDocumentUrl("http://localhost:8080/api/documents/files/documents/title_deed/abc.pdf");

        var res = service.uploadDocument(SELLER_ID, verif.getId(), req);

        assertEquals(1, res.getDocuments().size());
        verify(auditService).log(any(), eq("OWNERSHIP"), eq("DOCUMENT_UPLOADED"), eq(SELLER_ID), eq("SELLER"), any(), any(), any());
    }

    // ---------- uploadDocument: fraud / duplicate detection ----------

    @Test
    void uploadDocument_rejectsWhenHashAlreadyExists_andFlagsFraud() {
        SellerIdentityVerification identity = approvedIdentity();
        PropertyOwnershipVerification verif = verification(identity, OwnershipVerificationStatus.DRAFT);
        when(ownershipRepo.findById(verif.getId())).thenReturn(Optional.of(verif));
        when(documentAnalysisService.computeSha256FromUrl(anyString())).thenReturn("b".repeat(64));
        when(docRepo.existsByFileHashSha256(anyString())).thenReturn(true);
        when(fraudFlagService.flagOwnershipFraud(any(), any(), any(), any(), any(), any())).thenReturn(1);

        UploadOwnershipDocumentRequest req = new UploadOwnershipDocumentRequest();
        req.setDocumentCategory(OwnershipDocumentCategory.TITLE_DEED);
        req.setDocumentUrl("http://localhost:8080/api/documents/files/documents/title_deed/abc.pdf");

        assertThrows(com.kenyarealestate.verification.exception.DuplicateDocumentException.class,
                () -> service.uploadDocument(SELLER_ID, verif.getId(), req));

        verify(fraudFlagService).flagOwnershipFraud(eq(verif.getId()), eq(SELLER_ID),
                eq("DUPLICATE_DOCUMENT_HASH"), any(), any(), any());
    }

    @Test
    void uploadDocument_rejectsWhenDocumentUrlCannotBeFetched() {
        SellerIdentityVerification identity = approvedIdentity();
        PropertyOwnershipVerification verif = verification(identity, OwnershipVerificationStatus.DRAFT);
        when(ownershipRepo.findById(verif.getId())).thenReturn(Optional.of(verif));
        when(documentAnalysisService.computeSha256FromUrl(anyString())).thenReturn(null);

        UploadOwnershipDocumentRequest req = new UploadOwnershipDocumentRequest();
        req.setDocumentCategory(OwnershipDocumentCategory.TITLE_DEED);
        req.setDocumentUrl("http://evil.example.com/not-a-real-doc");

        assertThrows(VerificationException.class, () -> service.uploadDocument(SELLER_ID, verif.getId(), req));
        verifyNoInteractions(fraudFlagService);
    }

    @Test
    void uploadDocument_rejectsWhenAlreadyApproved() {
        SellerIdentityVerification identity = approvedIdentity();
        PropertyOwnershipVerification verif = verification(identity, OwnershipVerificationStatus.APPROVED);
        when(ownershipRepo.findById(verif.getId())).thenReturn(Optional.of(verif));

        UploadOwnershipDocumentRequest req = new UploadOwnershipDocumentRequest();
        req.setDocumentCategory(OwnershipDocumentCategory.TITLE_DEED);
        req.setDocumentUrl("http://localhost:8080/api/documents/files/documents/title_deed/abc.pdf");

        assertThrows(VerificationException.class, () -> service.uploadDocument(SELLER_ID, verif.getId(), req));
        verifyNoInteractions(documentAnalysisService);
    }

    // ---------- submitForReview ----------

    @Test
    void submitForReview_rejectsWhenMandatoryDocumentsMissing() {
        SellerIdentityVerification identity = approvedIdentity();
        PropertyOwnershipVerification verif = verification(identity, OwnershipVerificationStatus.DRAFT);
        when(ownershipRepo.findById(verif.getId())).thenReturn(Optional.of(verif));
        when(requirementRepo.findByPropertyTypeAndIsMandatoryTrue("FREEHOLD")).thenReturn(List.of(
                OwnershipDocumentRequirement.builder().documentCategory("TITLE_DEED").isMandatory(true)
                        .kenyaLawRef("Land Registration Act 2012").build()));

        VerificationException ex = assertThrows(VerificationException.class,
                () -> service.submitForReview(SELLER_ID, verif.getId()));
        assertTrue(ex.getMessage().contains("Missing mandatory documents"));
    }

    @Test
    void submitForReview_goesToMinistryCheck_whenDocumentAnalysisDisabled() {
        SellerIdentityVerification identity = approvedIdentity();
        PropertyOwnershipVerification verif = verification(identity, OwnershipVerificationStatus.DRAFT);
        when(ownershipRepo.findById(verif.getId())).thenReturn(Optional.of(verif));
        when(requirementRepo.findByPropertyTypeAndIsMandatoryTrue("FREEHOLD")).thenReturn(List.of());
        when(documentAnalysisService.isEnabled()).thenReturn(false);

        var res = service.submitForReview(SELLER_ID, verif.getId());

        assertEquals(OwnershipVerificationStatus.MINISTRY_LANDS_CHECK, res.getStatus());
        verify(auditService).log(any(), eq("OWNERSHIP"), eq("SUBMITTED_NO_AI_ANALYSIS"),
                eq(SELLER_ID), eq("SELLER"), any(), eq("MINISTRY_LANDS_CHECK"), any());
    }

    @Test
    void submitForReview_rejectsWhenAlreadyUnderReview() {
        SellerIdentityVerification identity = approvedIdentity();
        PropertyOwnershipVerification verif = verification(identity, OwnershipVerificationStatus.LEGAL_REVIEW);
        when(ownershipRepo.findById(verif.getId())).thenReturn(Optional.of(verif));

        assertThrows(VerificationException.class, () -> service.submitForReview(SELLER_ID, verif.getId()));
    }

    @Test
    void submitForReview_autoRejects_whenAiScreeningDetectsTampering() {
        SellerIdentityVerification identity = approvedIdentity();
        PropertyOwnershipVerification verif = verification(identity, OwnershipVerificationStatus.DRAFT);
        PropertyOwnershipDocument doc = PropertyOwnershipDocument.builder()
                .id(UUID.randomUUID())
                .propertyOwnershipVerification(verif)
                .documentCategory(OwnershipDocumentCategory.TITLE_DEED)
                .documentUrl("http://localhost:8080/api/documents/files/documents/title_deed/abc.pdf")
                .isRequired(true)
                .build();
        verif.getDocuments().add(doc);

        when(ownershipRepo.findById(verif.getId())).thenReturn(Optional.of(verif));
        when(requirementRepo.findByPropertyTypeAndIsMandatoryTrue("FREEHOLD")).thenReturn(List.of());
        when(documentAnalysisService.isEnabled()).thenReturn(true);
        when(documentAnalysisService.analyseDocument(anyString(), anyString()))
                .thenReturn(new DocumentAnalysisService.DocumentAnalysisResult(
                        "hash", 20, true, false, false, false, false, false, false, "tampered"));
        when(fraudFlagService.flagOwnershipFraud(any(), any(), any(), any(), any(), any())).thenReturn(1);

        var res = service.submitForReview(SELLER_ID, verif.getId());

        assertEquals(OwnershipVerificationStatus.REJECTED, res.getStatus());
        assertTrue(res.getRejectionReason().contains("TAMPERING"));
        verify(fraudFlagService).flagOwnershipFraud(eq(verif.getId()), eq(SELLER_ID),
                eq("DOCUMENT_TAMPERING"), any(), any(), any());
    }

    // ---------- admin final decision ----------

    @Test
    void adminFinalDecision_rejectsWhenNotInHumanReview() {
        SellerIdentityVerification identity = approvedIdentity();
        PropertyOwnershipVerification verif = verification(identity, OwnershipVerificationStatus.DRAFT);
        when(ownershipRepo.findById(verif.getId())).thenReturn(Optional.of(verif));

        AdminOwnershipReviewRequest req = new AdminOwnershipReviewRequest();
        req.setDecision("APPROVED");

        assertThrows(VerificationException.class,
                () -> service.adminFinalDecision(verif.getId(), req, UUID.randomUUID(), "jwt"));
        verifyNoInteractions(propertyClient, eventPublisher);
    }

    @Test
    void adminFinalDecision_approves_andNotifiesDownstreamSystems() {
        SellerIdentityVerification identity = approvedIdentity();
        PropertyOwnershipVerification verif = verification(identity, OwnershipVerificationStatus.HUMAN_REVIEW);
        when(ownershipRepo.findById(verif.getId())).thenReturn(Optional.of(verif));
        UUID adminId = UUID.randomUUID();

        AdminOwnershipReviewRequest req = new AdminOwnershipReviewRequest();
        req.setDecision("approved");

        var res = service.adminFinalDecision(verif.getId(), req, adminId, "admin-jwt");

        assertEquals(OwnershipVerificationStatus.APPROVED, res.getStatus());
        assertEquals(adminId, verif.getReviewedBy());
        verify(propertyClient).markPropertyOwnershipVerified(verif.getPropertyId(), "admin-jwt",
                verif.getParcelNumber(), verif.getTitleDeedNumber());
        verify(eventPublisher).publishOwnershipApproved(eq(SELLER_ID), eq(verif.getId()),
                eq(verif.getPropertyId()), any(), any(), any());
        verify(trustStatusService).evictCache(SELLER_ID);
        verify(auditService).log(any(), eq("OWNERSHIP"), eq("ADMIN_FINAL_DECISION_APPROVED"),
                eq(adminId), eq("ADMIN"), eq("HUMAN_REVIEW"), eq("APPROVED"), any());
    }

    @Test
    void adminFinalDecision_rejects_setsReasonWithoutNotifyingDownstream() {
        SellerIdentityVerification identity = approvedIdentity();
        PropertyOwnershipVerification verif = verification(identity, OwnershipVerificationStatus.HUMAN_REVIEW);
        when(ownershipRepo.findById(verif.getId())).thenReturn(Optional.of(verif));

        AdminOwnershipReviewRequest req = new AdminOwnershipReviewRequest();
        req.setDecision("REJECTED");
        req.setNotes("Title deed does not match parcel records");

        var res = service.adminFinalDecision(verif.getId(), req, UUID.randomUUID(), "jwt");

        assertEquals(OwnershipVerificationStatus.REJECTED, res.getStatus());
        assertEquals("Title deed does not match parcel records", res.getRejectionReason());
        verifyNoInteractions(propertyClient, eventPublisher);
    }

    @Test
    void adminFinalDecision_rejectsInvalidDecisionString() {
        SellerIdentityVerification identity = approvedIdentity();
        PropertyOwnershipVerification verif = verification(identity, OwnershipVerificationStatus.HUMAN_REVIEW);
        when(ownershipRepo.findById(verif.getId())).thenReturn(Optional.of(verif));

        AdminOwnershipReviewRequest req = new AdminOwnershipReviewRequest();
        req.setDecision("MAYBE_LATER");

        assertThrows(VerificationException.class,
                () -> service.adminFinalDecision(verif.getId(), req, UUID.randomUUID(), "jwt"));
    }

    // ---------- ministry / encumbrance check state guards ----------

    @Test
    void recordMinistryCheck_rejectsWhenNotInMinistryCheckStatus() {
        SellerIdentityVerification identity = approvedIdentity();
        PropertyOwnershipVerification verif = verification(identity, OwnershipVerificationStatus.DRAFT);
        when(ownershipRepo.findById(verif.getId())).thenReturn(Optional.of(verif));

        assertThrows(VerificationException.class,
                () -> service.recordMinistryCheck(verif.getId(), true, UUID.randomUUID(), "notes"));
    }

    @Test
    void recordMinistryCheck_failure_rejectsVerification() {
        SellerIdentityVerification identity = approvedIdentity();
        PropertyOwnershipVerification verif = verification(identity, OwnershipVerificationStatus.MINISTRY_LANDS_CHECK);
        when(ownershipRepo.findById(verif.getId())).thenReturn(Optional.of(verif));

        var res = service.recordMinistryCheck(verif.getId(), false, UUID.randomUUID(), "not found in registry");

        assertEquals(OwnershipVerificationStatus.REJECTED, res.getStatus());
    }

    @Test
    void recordMinistryCheck_success_movesToEncumbranceCheck() {
        SellerIdentityVerification identity = approvedIdentity();
        PropertyOwnershipVerification verif = verification(identity, OwnershipVerificationStatus.MINISTRY_LANDS_CHECK);
        when(ownershipRepo.findById(verif.getId())).thenReturn(Optional.of(verif));

        var res = service.recordMinistryCheck(verif.getId(), true, UUID.randomUUID(), "confirmed");

        assertEquals(OwnershipVerificationStatus.ENCUMBRANCE_CHECK, res.getStatus());
    }

    @Test
    void isOwnershipVerified_reflectsApprovedStatusOnly() {
        UUID propertyId = UUID.randomUUID();
        when(ownershipRepo.existsByPropertyIdAndStatus(propertyId, OwnershipVerificationStatus.APPROVED))
                .thenReturn(true);

        assertTrue(service.isOwnershipVerified(propertyId));
    }

    @Test
    void getByPropertyId_throwsNotFound_whenNoneExists() {
        UUID propertyId = UUID.randomUUID();
        when(ownershipRepo.findByPropertyId(propertyId)).thenReturn(Optional.empty());

        assertThrows(NotFoundException.class, () -> service.getByPropertyId(propertyId));
    }
}
