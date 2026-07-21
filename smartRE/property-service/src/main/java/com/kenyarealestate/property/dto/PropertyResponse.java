package com.kenyarealestate.property.dto;
import lombok.*; import java.math.BigDecimal; import java.time.LocalDateTime; import java.util.*;
@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class PropertyResponse {
    private UUID id, sellerId; private String title, description;
    private String propertyType, listingType, status;
    private String county, subCounty, city, locationDescription;
    private Double latitude, longitude;
    private BigDecimal price; private Integer bedrooms, bathrooms, yearBuilt;
    private Double areaSqm; private List<String> imageUrls;
    private boolean sellerIdentityVerified, propertyOwnershipVerified, fullyTrusted;
    private String parcelNumber, titleDeedNumber;
    private Integer viewCount; private LocalDateTime createdAt, updatedAt;
}
