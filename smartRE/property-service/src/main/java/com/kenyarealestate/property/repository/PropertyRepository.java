package com.kenyarealestate.property.repository;

import com.kenyarealestate.property.entity.*;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.*;

@Repository
public interface PropertyRepository extends JpaRepository<Property, UUID> {

    Page<Property> findBySellerId(UUID sellerId, Pageable p);
    List<Property> findBySellerIdAndStatus(UUID sellerId, ListingStatus status);
    List<Property> findByParcelNumberAndIdNot(String parcelNumber, UUID id);
    Page<Property> findByStatus(ListingStatus status, Pageable p);

    @Modifying
    @Query("UPDATE Property p SET p.viewCount = p.viewCount + 1 WHERE p.id = :id")
    void incrementViewCount(@Param("id") UUID id);

    @Query(value =
            "SELECT * FROM properties p WHERE p.status = 'ACTIVE'" +
                    " AND (:county IS NULL OR LOWER(p.county) LIKE LOWER(CONCAT('%',:county,'%')))" +
                    " AND (:city IS NULL OR LOWER(p.city) LIKE LOWER(CONCAT('%',:city,'%')))" +
                    " AND (CAST(:type AS VARCHAR) IS NULL OR p.property_type = CAST(:type AS VARCHAR))" +
                    " AND (CAST(:lt AS VARCHAR) IS NULL OR p.listing_type = CAST(:lt AS VARCHAR))" +
                    " AND (:minPrice IS NULL OR p.price >= CAST(:minPrice AS NUMERIC))" +
                    " AND (:maxPrice IS NULL OR p.price <= CAST(:maxPrice AS NUMERIC))" +
                    " AND (:minBed IS NULL OR p.bedrooms >= :minBed)" +
                    " AND (:kw IS NULL OR (LOWER(p.title) LIKE LOWER(CONCAT('%',:kw,'%'))" +
                    "     OR LOWER(p.description) LIKE LOWER(CONCAT('%',:kw,'%'))))" +
                    " AND (:verifiedOnly = FALSE OR (p.seller_identity_verified = TRUE AND p.property_ownership_verified = TRUE))" +
                    "  ",
            countQuery =
                    "SELECT COUNT(*) FROM properties p WHERE p.status = 'ACTIVE'" +
                            " AND (:county IS NULL OR LOWER(p.county) LIKE LOWER(CONCAT('%',:county,'%')))" +
                            " AND (:city IS NULL OR LOWER(p.city) LIKE LOWER(CONCAT('%',:city,'%')))" +
                            " AND (CAST(:type AS VARCHAR) IS NULL OR p.property_type = CAST(:type AS VARCHAR))" +
                            " AND (CAST(:lt AS VARCHAR) IS NULL OR p.listing_type = CAST(:lt AS VARCHAR))" +
                            " AND (:minPrice IS NULL OR p.price >= CAST(:minPrice AS NUMERIC))" +
                            " AND (:maxPrice IS NULL OR p.price <= CAST(:maxPrice AS NUMERIC))" +
                            " AND (:minBed IS NULL OR p.bedrooms >= :minBed)" +
                            " AND (:kw IS NULL OR (LOWER(p.title) LIKE LOWER(CONCAT('%',:kw,'%'))" +
                            "     OR LOWER(p.description) LIKE LOWER(CONCAT('%',:kw,'%'))))" +
                            " AND (:verifiedOnly = FALSE OR (p.seller_identity_verified = TRUE AND p.property_ownership_verified = TRUE))",
            nativeQuery = true)
    Page<Property> search(
            @Param("county") String county,
            @Param("city") String city,
            @Param("type") String type,
            @Param("lt") String lt,
            @Param("minPrice") BigDecimal minPrice,
            @Param("maxPrice") BigDecimal maxPrice,
            @Param("minBed") Integer minBed,
            @Param("kw") String kw,
            @Param("verifiedOnly") boolean verifiedOnly,
            Pageable p);
}
