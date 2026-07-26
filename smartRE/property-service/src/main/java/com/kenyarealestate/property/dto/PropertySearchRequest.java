package com.kenyarealestate.property.dto;

import lombok.Data;
import java.math.BigDecimal;

@Data
public class PropertySearchRequest {
    private String county, city, propertyType, listingType, keyword;
    private BigDecimal minPrice, maxPrice;
    private Integer minBedrooms;
    private boolean verifiedOnly = false;
    private int page = 0;
    private int size = 20;
    private String sortBy = "createdAt";
    private String direction = "DESC";
}
