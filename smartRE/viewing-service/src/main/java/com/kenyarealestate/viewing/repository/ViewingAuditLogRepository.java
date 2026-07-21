package com.kenyarealestate.viewing.repository;

import com.kenyarealestate.viewing.entity.ViewingAuditLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface ViewingAuditLogRepository extends JpaRepository<ViewingAuditLog, UUID> {
    List<ViewingAuditLog> findByViewingIdOrderByCreatedAtAsc(UUID viewingId);
}
