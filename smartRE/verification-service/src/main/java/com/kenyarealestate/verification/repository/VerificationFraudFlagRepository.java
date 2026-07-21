package com.kenyarealestate.verification.repository;

import com.kenyarealestate.verification.entity.VerificationFraudFlag;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface VerificationFraudFlagRepository extends JpaRepository<VerificationFraudFlag, UUID> {
    List<VerificationFraudFlag> findByUserIdOrderByFlaggedAtDesc(UUID userId);
    boolean existsByDocumentHash(String hash);
    long countByUserId(UUID userId);
}
