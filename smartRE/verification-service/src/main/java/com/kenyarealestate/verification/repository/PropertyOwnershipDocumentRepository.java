package com.kenyarealestate.verification.repository;

import com.kenyarealestate.verification.entity.PropertyOwnershipDocument;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface PropertyOwnershipDocumentRepository extends JpaRepository<PropertyOwnershipDocument, UUID> {
    boolean existsByFileHashSha256(String hash);
}
