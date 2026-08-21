package com.kenyarealestate.user.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

/** Tracks who uploaded each stored document, so serveFile() can enforce ownership. */
@Entity
@Table(name = "uploaded_documents")
@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class UploadedDocument {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "object_key", nullable = false, unique = true, length = 512)
    private String objectKey;

    @Column(nullable = false, length = 100)
    private String category;

    @Column(name = "uploader_id")
    private UUID uploaderId;

    @Column(name = "content_type", length = 100)
    private String contentType;

    @Column(name = "size_bytes")
    private Long sizeBytes;

    @CreationTimestamp
    @Column(name = "created_at")
    private LocalDateTime createdAt;
}
