package com.kenyarealestate.verification.repository;

import com.kenyarealestate.verification.entity.OwnershipDocumentRequirement;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface OwnershipDocumentRequirementRepository extends JpaRepository<OwnershipDocumentRequirement, UUID> {
    List<OwnershipDocumentRequirement> findByPropertyType(String propertyType);
    List<OwnershipDocumentRequirement> findByPropertyTypeAndIsMandatoryTrue(String propertyType);
}
