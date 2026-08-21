package com.kenyarealestate.property.repository;

import com.kenyarealestate.property.entity.PropertyImageHash;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PropertyImageHashRepository extends JpaRepository<PropertyImageHash, UUID> {
    Optional<PropertyImageHash> findFirstByImageHashAndSellerIdNot(String imageHash, UUID sellerId);
    List<PropertyImageHash> findByPropertyId(UUID propertyId);
    void deleteByPropertyId(UUID propertyId);
}
