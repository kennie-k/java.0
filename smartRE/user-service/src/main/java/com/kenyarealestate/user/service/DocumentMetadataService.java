package com.kenyarealestate.user.service;

import com.kenyarealestate.user.entity.UploadedDocument;
import com.kenyarealestate.user.entity.User;
import com.kenyarealestate.user.repository.UploadedDocumentRepository;
import com.kenyarealestate.user.repository.UserRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * Records the uploader of each document and answers "can this caller read this document",
 * so DocumentUploadController can enforce that non-public documents (national ID scans,
 * ownership/business documents, etc.) are only served to the uploader or an admin.
 */
@Slf4j
@Service
public class DocumentMetadataService {

    private final UploadedDocumentRepository repo;
    private final UserRepository userRepo;

    public DocumentMetadataService(UploadedDocumentRepository repo, UserRepository userRepo) {
        this.repo = repo;
        this.userRepo = userRepo;
    }

    public void recordUpload(String objectKey, String category, Authentication auth, String contentType, long sizeBytes) {
        UUID uploaderId = resolveUploaderId(auth);
        if (uploaderId == null) {
            log.warn("Document uploaded without a resolvable uploader identity: objectKey={}", objectKey);
        }
        repo.save(UploadedDocument.builder()
                .objectKey(objectKey)
                .category(category)
                .uploaderId(uploaderId)
                .contentType(contentType)
                .sizeBytes(sizeBytes)
                .build());
    }

    /**
     * True if the caller may read the given object key: an admin, or the original uploader.
     * Unknown documents (uploaded before this tracking existed, or never recorded) are denied
     * by default rather than fail-open.
     */
    public boolean canAccess(String objectKey, Authentication auth) {
        if (auth == null || !auth.isAuthenticated()) return false;

        if (hasRole(auth, "ROLE_ADMIN")) return true;

        User requester = userRepo.findByEmail(auth.getName()).orElse(null);
        if (requester == null) return false;

        return repo.findByObjectKey(objectKey)
                .map(d -> requester.getId().equals(d.getUploaderId()))
                .orElse(false);
    }

    private UUID resolveUploaderId(Authentication auth) {
        if (auth == null || auth.getName() == null) return null;
        return userRepo.findByEmail(auth.getName()).map(User::getId).orElse(null);
    }

    private boolean hasRole(Authentication auth, String role) {
        for (GrantedAuthority a : auth.getAuthorities()) {
            if (role.equals(a.getAuthority())) return true;
        }
        return false;
    }
}
