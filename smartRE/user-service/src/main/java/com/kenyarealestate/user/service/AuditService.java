package com.kenyarealestate.user.service;

import com.kenyarealestate.user.entity.UserAuditLog;
import com.kenyarealestate.user.repository.UserAuditLogRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

/**
 * Mirrors verification-service's AuditService: records an immutable trail of sensitive
 * admin actions performed against user accounts (promotion, ban, unban).
 */
@Service
public class AuditService {

    private final UserAuditLogRepository repo;

    public AuditService(UserAuditLogRepository repo) {
        this.repo = repo;
    }

    public void log(UUID targetUserId, String action, UUID performedBy, String performedByRole,
                     String fromValue, String toValue, String notes) {
        repo.save(UserAuditLog.builder()
                .targetUserId(targetUserId)
                .action(action)
                .performedBy(performedBy)
                .performedByRole(performedByRole)
                .fromValue(fromValue)
                .toValue(toValue)
                .notes(notes)
                .build());
    }

    public List<UserAuditLog> getHistory(UUID targetUserId) {
        return repo.findByTargetUserIdOrderByCreatedAtAsc(targetUserId);
    }
}
