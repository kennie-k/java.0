package com.kenyarealestate.property.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;

@Entity @Table(name="properties")
@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class Property {
    @Id @GeneratedValue(strategy=GenerationType.UUID) private UUID id;
    @Column(name="seller_id",nullable=false) private UUID sellerId;
    @Column(nullable=false) private String title;
    @Column(columnDefinition="TEXT") private String description;
    @Enumerated(EnumType.STRING) @Column(name="property_type",nullable=false) private PropertyType propertyType;
    @Enumerated(EnumType.STRING) @Column(name="listing_type",nullable=false) @Builder.Default private ListingType listingType=ListingType.SALE;
    @Enumerated(EnumType.STRING) @Builder.Default private ListingStatus status=ListingStatus.DRAFT;
    @Column(nullable=false) private String county;
    @Column(name="sub_county") private String subCounty;
    private String city;
    @Column(name="location_description",columnDefinition="TEXT") private String locationDescription;
    private Double latitude, longitude;
    @Column(nullable=false,precision=15,scale=2) private BigDecimal price;
    private Integer bedrooms, bathrooms, yearBuilt;
    @Column(name="area_sqm") private Double areaSqm;
    @ElementCollection @CollectionTable(name="property_images",joinColumns=@JoinColumn(name="property_id")) @Column(name="url") @Builder.Default private List<String> imageUrls=new ArrayList<>();
    @Builder.Default @Column(name="seller_identity_verified") private boolean sellerIdentityVerified=false;
    @Builder.Default @Column(name="property_ownership_verified") private boolean propertyOwnershipVerified=false;
    @Column(name="parcel_number") private String parcelNumber;
    @Column(name="title_deed_number") private String titleDeedNumber;
    @Builder.Default @Column(name="view_count") private Integer viewCount=0;
    @CreationTimestamp private LocalDateTime createdAt;
    @UpdateTimestamp   private LocalDateTime updatedAt;
}
