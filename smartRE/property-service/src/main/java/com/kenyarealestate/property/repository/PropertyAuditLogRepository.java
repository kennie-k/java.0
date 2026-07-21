package com.kenyarealestate.property.repository;

import com.kenyarealestate.property.entity.PropertyAuditLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface PropertyAuditLogRepository extends JpaRepository<PropertyAuditLog, UUID> {
    List<PropertyAuditLog> findByPropertyIdOrderByCreatedAtAsc(UUID propertyId);
}
