package com.kenyarealestate.user.service;

import com.kenyarealestate.user.client.VerificationClient;
import com.kenyarealestate.user.dto.AdminAgentApplicationReviewRequest;
import com.kenyarealestate.user.dto.AgentApplicationResponse;
import com.kenyarealestate.user.dto.SubmitAgentApplicationRequest;
import com.kenyarealestate.user.entity.AgentApplication;
import com.kenyarealestate.user.entity.AgentApplicationStatus;
import com.kenyarealestate.user.entity.Role;
import com.kenyarealestate.user.entity.User;
import com.kenyarealestate.user.repository.AgentApplicationRepository;
import com.kenyarealestate.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AgentApplicationServiceTest {

    @Mock private AgentApplicationRepository repo;
    @Mock private UserRepository userRepo;
    @Mock private VerificationClient verificationClient;

    @InjectMocks private AgentApplicationService service;

    private User seller() {
        return User.builder().id(UUID.randomUUID()).fullName("Jane Seller")
                .email("jane@smartre.co.ke").role(Role.SELLER).build();
    }

    private SubmitAgentApplicationRequest submitRequest() {
        SubmitAgentApplicationRequest req = new SubmitAgentApplicationRequest();
        req.setBusinessName("Jane Realty Ltd");
        req.setBusinessDocUrl("http://localhost:8080/api/documents/files/documents/business_doc/abc.pdf");
        return req;
    }

    @BeforeEach
    void setup() {
        when(repo.save(any(AgentApplication.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void submit_rejectsWhenUserIsAlreadyAnAgent() {
        User agent = seller();
        agent.setRole(Role.AGENT);
        when(userRepo.findByEmail(agent.getEmail())).thenReturn(Optional.of(agent));

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> service.submit(agent.getEmail(), submitRequest()));

        assertTrue(ex.getMessage().contains("already an approved agent"));
        verify(repo, never()).save(any());
    }

    @Test
    void submit_rejectsNonSellerRoles() {
        User buyer = seller();
        buyer.setRole(Role.BUYER);
        when(userRepo.findByEmail(buyer.getEmail())).thenReturn(Optional.of(buyer));

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> service.submit(buyer.getEmail(), submitRequest()));

        assertTrue(ex.getMessage().contains("Only sellers"));
        verify(repo, never()).save(any());
    }

    @Test
    void submit_rejectsWhenIdentityNotVerified() {
        User seller = seller();
        when(userRepo.findByEmail(seller.getEmail())).thenReturn(Optional.of(seller));
        when(verificationClient.isIdentityVerified(seller.getId())).thenReturn(false);

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> service.submit(seller.getEmail(), submitRequest()));

        assertTrue(ex.getMessage().contains("identity must be verified"));
        verify(repo, never()).save(any());
    }

    @Test
    void submit_createsNewApplication_whenNoneExists() {
        User seller = seller();
        when(userRepo.findByEmail(seller.getEmail())).thenReturn(Optional.of(seller));
        when(verificationClient.isIdentityVerified(seller.getId())).thenReturn(true);
        when(repo.existsByUserId(seller.getId())).thenReturn(false);

        AgentApplicationResponse res = service.submit(seller.getEmail(), submitRequest());

        assertEquals(seller.getId(), res.getUserId());
        assertEquals("Jane Realty Ltd", res.getBusinessName());
        verify(repo).save(any(AgentApplication.class));
    }

    @Test
    void submit_rejectsResubmission_whenApplicationAlreadyUnderReview() {
        User seller = seller();
        AgentApplication existing = AgentApplication.builder()
                .userId(seller.getId()).status(AgentApplicationStatus.SUBMITTED).build();
        when(userRepo.findByEmail(seller.getEmail())).thenReturn(Optional.of(seller));
        when(verificationClient.isIdentityVerified(seller.getId())).thenReturn(true);
        when(repo.existsByUserId(seller.getId())).thenReturn(true);
        when(repo.findByUserId(seller.getId())).thenReturn(Optional.of(existing));

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> service.submit(seller.getEmail(), submitRequest()));

        assertTrue(ex.getMessage().contains("already have an application under review"));
        verify(repo, never()).save(any());
    }

    @Test
    void submit_rejectsResubmission_whenAlreadyApproved() {
        User seller = seller();
        AgentApplication existing = AgentApplication.builder()
                .userId(seller.getId()).status(AgentApplicationStatus.APPROVED).build();
        when(userRepo.findByEmail(seller.getEmail())).thenReturn(Optional.of(seller));
        when(verificationClient.isIdentityVerified(seller.getId())).thenReturn(true);
        when(repo.existsByUserId(seller.getId())).thenReturn(true);
        when(repo.findByUserId(seller.getId())).thenReturn(Optional.of(existing));

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> service.submit(seller.getEmail(), submitRequest()));

        assertTrue(ex.getMessage().contains("already approved"));
        verify(repo, never()).save(any());
    }

    @Test
    void submit_allowsResubmission_afterRejection() {
        User seller = seller();
        AgentApplication existing = AgentApplication.builder()
                .userId(seller.getId()).status(AgentApplicationStatus.REJECTED)
                .rejectionReason("Business doc unreadable").build();
        when(userRepo.findByEmail(seller.getEmail())).thenReturn(Optional.of(seller));
        when(verificationClient.isIdentityVerified(seller.getId())).thenReturn(true);
        when(repo.existsByUserId(seller.getId())).thenReturn(true);
        when(repo.findByUserId(seller.getId())).thenReturn(Optional.of(existing));

        AgentApplicationResponse res = service.submit(seller.getEmail(), submitRequest());

        assertEquals(AgentApplicationStatus.SUBMITTED, res.getStatus());
        assertNull(res.getRejectionReason());
        verify(repo).save(existing);
    }

    @Test
    void review_approvingPromotesUserToAgent() {
        User seller = seller();
        User admin = User.builder().id(UUID.randomUUID()).email("admin@smartre.co.ke").role(Role.ADMIN).build();
        AgentApplication application = AgentApplication.builder()
                .id(UUID.randomUUID()).userId(seller.getId()).status(AgentApplicationStatus.SUBMITTED).build();
        when(repo.findById(application.getId())).thenReturn(Optional.of(application));
        when(userRepo.findByEmail(admin.getEmail())).thenReturn(Optional.of(admin));
        when(userRepo.findById(seller.getId())).thenReturn(Optional.of(seller));

        AdminAgentApplicationReviewRequest req = new AdminAgentApplicationReviewRequest();
        req.setDecision("APPROVED");

        AgentApplicationResponse res = service.review(application.getId(), req, admin.getEmail());

        assertEquals(AgentApplicationStatus.APPROVED, res.getStatus());
        assertEquals(Role.AGENT, seller.getRole());
        assertEquals(admin.getId(), res.getReviewedBy());
        verify(userRepo).save(seller);
    }

    @Test
    void review_rejectingSetsReasonAndDoesNotPromote() {
        User seller = seller();
        User admin = User.builder().id(UUID.randomUUID()).email("admin@smartre.co.ke").role(Role.ADMIN).build();
        AgentApplication application = AgentApplication.builder()
                .id(UUID.randomUUID()).userId(seller.getId()).status(AgentApplicationStatus.SUBMITTED).build();
        when(repo.findById(application.getId())).thenReturn(Optional.of(application));
        when(userRepo.findByEmail(admin.getEmail())).thenReturn(Optional.of(admin));

        AdminAgentApplicationReviewRequest req = new AdminAgentApplicationReviewRequest();
        req.setDecision("REJECTED");
        req.setNotes("Business registration document expired");

        AgentApplicationResponse res = service.review(application.getId(), req, admin.getEmail());

        assertEquals(AgentApplicationStatus.REJECTED, res.getStatus());
        assertEquals("Business registration document expired", res.getRejectionReason());
        assertEquals(Role.SELLER, seller.getRole());
        verify(userRepo, never()).save(any());
    }

    @Test
    void review_rejectsInvalidDecision() {
        User admin = User.builder().id(UUID.randomUUID()).email("admin@smartre.co.ke").role(Role.ADMIN).build();
        AgentApplication application = AgentApplication.builder()
                .id(UUID.randomUUID()).userId(UUID.randomUUID()).status(AgentApplicationStatus.SUBMITTED).build();
        when(repo.findById(application.getId())).thenReturn(Optional.of(application));
        when(userRepo.findByEmail(admin.getEmail())).thenReturn(Optional.of(admin));

        AdminAgentApplicationReviewRequest req = new AdminAgentApplicationReviewRequest();
        req.setDecision("MAYBE_LATER");

        assertThrows(RuntimeException.class, () -> service.review(application.getId(), req, admin.getEmail()));
        verify(repo, never()).save(any());
    }
}
