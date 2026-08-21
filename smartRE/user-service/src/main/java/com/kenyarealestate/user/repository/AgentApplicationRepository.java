package com.kenyarealestate.user.repository;

import com.kenyarealestate.user.entity.AgentApplication;
import com.kenyarealestate.user.entity.AgentApplicationStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface AgentApplicationRepository extends JpaRepository<AgentApplication, UUID> {
    Optional<AgentApplication> findByUserId(UUID userId);
    boolean existsByUserId(UUID userId);
    Page<AgentApplication> findByStatus(AgentApplicationStatus status, Pageable pageable);
    boolean existsByBusinessDocHashAndUserIdNot(String businessDocHash, UUID userId);
}
