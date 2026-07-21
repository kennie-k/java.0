package com.kenyarealestate.verification.entity;

import jakarta.persistence.*;
import lombok.*;

import java.util.UUID;

@Entity
@Table(name = "ownership_document_requirements")
@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class OwnershipDocumentRequirement {

    @Id @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "property_type",     nullable = false) private String propertyType;
    @Column(name = "document_category", nullable = false) private String documentCategory;
    @Column(name = "is_mandatory")  private Boolean isMandatory;
    @Column(columnDefinition = "TEXT")  private String description;
    @Column(name = "kenya_law_ref")     private String kenyaLawRef;
}
