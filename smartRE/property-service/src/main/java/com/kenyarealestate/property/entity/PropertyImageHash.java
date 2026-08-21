package com.kenyarealestate.property.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "property_image_hashes")
@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class PropertyImageHash {

    @Id @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "property_id", nullable = false)
    private UUID propertyId;

    @Column(name = "seller_id", nullable = false)
    private UUID sellerId;

    @Column(name = "image_url", nullable = false, columnDefinition = "TEXT")
    private String imageUrl;

    @Column(name = "image_hash", nullable = false, length = 64)
    private String imageHash;

    @CreationTimestamp
    private LocalDateTime createdAt;
}
