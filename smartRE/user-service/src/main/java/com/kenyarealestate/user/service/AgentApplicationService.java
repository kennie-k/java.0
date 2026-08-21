package com.kenyarealestate.user.service;

import com.kenyarealestate.user.client.VerificationClient;
import com.kenyarealestate.user.dto.*;
import com.kenyarealestate.user.entity.*;
import com.kenyarealestate.user.exception.BadRequestException;
import com.kenyarealestate.user.exception.ConflictException;
import com.kenyarealestate.user.exception.ForbiddenException;
import com.kenyarealestate.user.exception.NotFoundException;
import com.kenyarealestate.user.repository.AgentApplicationRepository;
import com.kenyarealestate.user.repository.UserRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.InputStream;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.UUID;

@Slf4j
@Service
@Transactional
public class AgentApplicationService {

    private final AgentApplicationRepository repo;
    private final UserRepository userRepo;
    private final VerificationClient verificationClient;

    @Value("${storage.local-dir:/app/uploads}")
    private String localDir;

    public AgentApplicationService(AgentApplicationRepository repo, UserRepository userRepo,
                                    VerificationClient verificationClient) {
        this.repo = repo;
        this.userRepo = userRepo;
        this.verificationClient = verificationClient;
    }

    public AgentApplicationResponse submit(String email, SubmitAgentApplicationRequest req) {
        User user = userRepo.findByEmail(email).orElseThrow(() -> new NotFoundException("User not found"));

        if (user.getRole() == Role.AGENT) throw new ConflictException("You are already an approved agent.");
        if (user.getRole() != Role.SELLER)
            throw new ForbiddenException("Only sellers may apply to become an agent.");
        if (!verificationClient.isIdentityVerified(user.getId()))
            throw new ForbiddenException("Your identity must be verified before applying to become an agent.");

        String hash = computeSha256FromUrl(req.getBusinessDocUrl());
        if (hash == null)
            throw new BadRequestException("Could not retrieve the business document from its URL. Please re-upload it and try again.");
        if (repo.existsByBusinessDocHashAndUserIdNot(hash, user.getId())) {
            log.warn("ALERT: duplicate business document hash submitted by userId={} — this exact document " +
                    "already backs another agent application.", user.getId());
            throw new ConflictException("This exact document has already been submitted by another applicant. This incident is logged.");
        }

        if (repo.existsByUserId(user.getId())) {
            AgentApplication existing = repo.findByUserId(user.getId()).orElseThrow();
            if (existing.getStatus() == AgentApplicationStatus.SUBMITTED)
                throw new ConflictException("You already have an application under review.");
            if (existing.getStatus() == AgentApplicationStatus.APPROVED)
                throw new ConflictException("Your agent application was already approved.");
            existing.setStatus(AgentApplicationStatus.SUBMITTED);
            existing.setBusinessName(req.getBusinessName());
            existing.setBusinessDocUrl(req.getBusinessDocUrl());
            existing.setBusinessDocHash(hash);
            existing.setRejectionReason(null);
            existing.setReviewedBy(null);
            existing.setReviewedAt(null);
            return toResponse(repo.save(existing));
        }

        AgentApplication application = AgentApplication.builder()
                .userId(user.getId())
                .businessName(req.getBusinessName())
                .businessDocUrl(req.getBusinessDocUrl())
                .businessDocHash(hash)
                .build();
        return toResponse(repo.save(application));
    }

    // Mirrors DocumentAnalysisService's local-file/S3 URL handling in verification-service:
    // local-disk URLs point at this container's own public-facing host and aren't reachable
    // from inside the container network, so read the file straight off disk instead. S3 URLs
    // are genuinely internet-reachable and can be fetched directly.
    private String computeSha256FromUrl(String documentUrl) {
        try {
            int idx = documentUrl.indexOf("/api/documents/files/");
            if (idx != -1) {
                String key = documentUrl.substring(idx + "/api/documents/files/".length());
                Path root = Paths.get(localDir).toAbsolutePath().normalize();
                Path target = root.resolve(key).normalize();
                if (!target.startsWith(root) || !Files.isRegularFile(target)) return null;
                try (InputStream in = Files.newInputStream(target)) {
                    return digest(in);
                }
            }
            try (InputStream in = URI.create(documentUrl).toURL().openStream()) {
                return digest(in);
            }
        } catch (Exception e) {
            log.error("Failed to compute SHA-256 for business document URL {}: {}", documentUrl, e.getMessage());
            return null;
        }
    }

    private String digest(InputStream in) throws Exception {
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        byte[] buf = new byte[8192];
        int read;
        while ((read = in.read(buf)) != -1) {
            md.update(buf, 0, read);
        }
        return HexFormat.of().formatHex(md.digest());
    }

    @Transactional(readOnly = true)
    public AgentApplicationResponse getMine(String email) {
        User user = userRepo.findByEmail(email).orElseThrow(() -> new NotFoundException("User not found"));
        return repo.findByUserId(user.getId()).map(this::toResponse)
                .orElseThrow(() -> new NotFoundException("No agent application found"));
    }

    @Transactional(readOnly = true)
    public Page<AgentApplicationResponse> getQueue(AgentApplicationStatus status, Pageable pageable) {
        return repo.findByStatus(status, pageable).map(this::toResponse);
    }

    public AgentApplicationResponse review(UUID applicationId, AdminAgentApplicationReviewRequest req, String adminEmail) {
        AgentApplication application = repo.findById(applicationId)
                .orElseThrow(() -> new NotFoundException("Agent application not found: " + applicationId));
        UUID adminId = userRepo.findByEmail(adminEmail)
                .orElseThrow(() -> new NotFoundException("Admin user not found")).getId();

        switch (req.getDecision().toUpperCase()) {
            case "APPROVED" -> {
                User user = userRepo.findById(application.getUserId())
                        .orElseThrow(() -> new NotFoundException("User not found"));
                if (!user.isActive())
                    throw new ConflictException("Cannot approve — this applicant's account is currently banned.");
                application.setStatus(AgentApplicationStatus.APPROVED);
                user.setRole(Role.AGENT);
                userRepo.save(user);
            }
            case "REJECTED" -> {
                application.setStatus(AgentApplicationStatus.REJECTED);
                application.setRejectionReason(req.getNotes());
            }
            default -> throw new BadRequestException("Invalid decision. Must be APPROVED or REJECTED");
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
