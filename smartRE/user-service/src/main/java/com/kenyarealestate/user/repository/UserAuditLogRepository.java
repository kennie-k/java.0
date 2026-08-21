package com.kenyarealestate.user.repository;

import com.kenyarealestate.user.entity.UserAuditLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface UserAuditLogRepository extends JpaRepository<UserAuditLog, UUID> {
    List<UserAuditLog> findByTargetUserIdOrderByCreatedAtAsc(UUID targetUserId);
}
