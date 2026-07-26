package com.kenyarealestate.user.service;

import com.kenyarealestate.user.client.VerificationClient;
import com.kenyarealestate.user.dto.*;
import com.kenyarealestate.user.entity.*;
import com.kenyarealestate.user.repository.AgentApplicationRepository;
import com.kenyarealestate.user.repository.UserRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
@Transactional
public class AgentApplicationService {

    private final AgentApplicationRepository repo;
    private final UserRepository userRepo;
    private final VerificationClient verificationClient;

    public AgentApplicationService(AgentApplicationRepository repo, UserRepository userRepo,
                                    VerificationClient verificationClient) {
        this.repo = repo;
        this.userRepo = userRepo;
        this.verificationClient = verificationClient;
    }

    public AgentApplicationResponse submit(String email, SubmitAgentApplicationRequest req) {
        User user = userRepo.findByEmail(email).orElseThrow(() -> new RuntimeException("User not found"));

        if (user.getRole() == Role.AGENT) throw new RuntimeException("You are already an approved agent.");
        if (user.getRole() != Role.SELLER)
            throw new RuntimeException("Only sellers may apply to become an agent.");
        if (!verificationClient.isIdentityVerified(user.getId()))
            throw new RuntimeException("Your identity must be verified before applying to become an agent.");

        if (repo.existsByUserId(user.getId())) {
            AgentApplication existing = repo.findByUserId(user.getId()).orElseThrow();
            if (existing.getStatus() == AgentApplicationStatus.SUBMITTED)
                throw new RuntimeException("You already have an application under review.");
            if (existing.getStatus() == AgentApplicationStatus.APPROVED)
                throw new RuntimeException("Your agent application was already approved.");
            existing.setStatus(AgentApplicationStatus.SUBMITTED);
            existing.setBusinessName(req.getBusinessName());
            existing.setBusinessDocUrl(req.getBusinessDocUrl());
            existing.setRejectionReason(null);
            existing.setReviewedBy(null);
            existing.setReviewedAt(null);
            return toResponse(repo.save(existing));
        }

        AgentApplication application = AgentApplication.builder()
                .userId(user.getId())
                .businessName(req.getBusinessName())
                .businessDocUrl(req.getBusinessDocUrl())
                .build();
        return toResponse(repo.save(application));
    }

    @Transactional(readOnly = true)
    public AgentApplicationResponse getMine(String email) {
        User user = userRepo.findByEmail(email).orElseThrow(() -> new RuntimeException("User not found"));
        return repo.findByUserId(user.getId()).map(this::toResponse)
                .orElseThrow(() -> new RuntimeException("No agent application found"));
    }

    @Transactional(readOnly = true)
    public Page<AgentApplicationResponse> getQueue(AgentApplicationStatus status, Pageable pageable) {
        return repo.findByStatus(status, pageable).map(this::toResponse);
    }

    public AgentApplicationResponse review(UUID applicationId, AdminAgentApplicationReviewRequest req, String adminEmail) {
        AgentApplication application = repo.findById(applicationId)
                .orElseThrow(() -> new RuntimeException("Agent application not found: " + applicationId));
        UUID adminId = userRepo.findByEmail(adminEmail)
                .orElseThrow(() -> new RuntimeException("Admin user not found")).getId();

        switch (req.getDecision().toUpperCase()) {
            case "APPROVED" -> {
                application.setStatus(AgentApplicationStatus.APPROVED);
                User user = userRepo.findById(application.getUserId())
                        .orElseThrow(() -> new RuntimeException("User not found"));
                user.setRole(Role.AGENT);
                userRepo.save(user);
            }
            case "REJECTED" -> {
                application.setStatus(AgentApplicationStatus.REJECTED);
                application.setRejectionReason(req.getNotes());
            }
            default -> throw new RuntimeException("Invalid decision. Must be APPROVED or REJECTED");
        }
        application.setReviewedBy(adminId);
        application.setReviewedAt(LocalDateTime.now());
        return toResponse(repo.save(application));
    }

    private AgentApplicationResponse toResponse(AgentApplication a) {
        return AgentApplicationResponse.builder()
                .id(a.getId()).userId(a.getUserId()).status(a.getStatus())
                .businessName(a.getBusinessName()).businessDocUrl(a.getBusinessDocUrl())
                .rejectionReason(a.getRejectionReason())
                .reviewedBy(a.getReviewedBy()).reviewedAt(a.getReviewedAt())
                .createdAt(a.getCreatedAt()).updatedAt(a.getUpdatedAt())
                .build();
    }
}
