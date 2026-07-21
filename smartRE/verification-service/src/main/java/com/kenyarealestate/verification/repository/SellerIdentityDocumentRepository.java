package com.kenyarealestate.verification.repository;

import com.kenyarealestate.verification.entity.SellerIdentityDocument;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface SellerIdentityDocumentRepository extends JpaRepository<SellerIdentityDocument, UUID> {
    boolean existsByFileHashSha256(String hash);
}
