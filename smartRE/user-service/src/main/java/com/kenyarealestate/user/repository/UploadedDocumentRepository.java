package com.kenyarealestate.user.repository;

import com.kenyarealestate.user.entity.UploadedDocument;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface UploadedDocumentRepository extends JpaRepository<UploadedDocument, UUID> {
    Optional<UploadedDocument> findByObjectKey(String objectKey);
}
