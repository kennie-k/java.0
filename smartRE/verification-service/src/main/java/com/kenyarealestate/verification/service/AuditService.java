package com.kenyarealestate.verification.service;

import com.kenyarealestate.verification.entity.VerificationAuditLog;
import com.kenyarealestate.verification.repository.VerificationAuditLogRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
public class AuditService {

    private final VerificationAuditLogRepository repo;

    public AuditService(VerificationAuditLogRepository repo) {
        this.repo = repo;
    }

    public void log(UUID verificationId, String track, String action,
                    UUID performedBy, String performedByRole,
                    String fromStatus, String toStatus, String notes) {
        repo.save(VerificationAuditLog.builder()
                .verificationId(verificationId)
                .verificationTrack(track)
                .action(action)
                .performedBy(performedBy)
                .performedByRole(performedByRole)
                .fromStatus(fromStatus)
                .toStatus(toStatus)
                .notes(notes)
                .build());
    }

    public List<VerificationAuditLog> getHistory(UUID verificationId) {
        return repo.findByVerificationIdOrderByCreatedAtAsc(verificationId);
    }
}
