package com.kenyarealestate.property.service;

import com.kenyarealestate.property.entity.PropertyAuditLog;
import com.kenyarealestate.property.repository.PropertyAuditLogRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Slf4j
@Service
public class PropertyAuditService {

    private final PropertyAuditLogRepository repo;

    public PropertyAuditService(PropertyAuditLogRepository repo) {
        this.repo = repo;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void log(UUID propertyId, String eventType, String previousStatus, String newStatus,
                    UUID actorId, String actorRole, String actorIp, String detail) {
        try {
            repo.save(PropertyAuditLog.builder()
                    .propertyId(propertyId)
                    .eventType(eventType)
                    .previousStatus(previousStatus)
                    .newStatus(newStatus)
                    .actorId(actorId)
                    .actorRole(actorRole)
                    .actorIp(actorIp)
                    .detail(detail)
                    .build());
        } catch (Exception e) {
            log.error("PROPERTY AUDIT FAILURE propertyId={} event={}: {}", propertyId, eventType, e.getMessage());
        }
    }
}
