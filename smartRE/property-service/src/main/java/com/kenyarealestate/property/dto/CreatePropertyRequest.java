package com.kenyarealestate.property.dto;
import jakarta.validation.constraints.*; import lombok.Data;
import java.math.BigDecimal; import java.util.List;
@Data public class CreatePropertyRequest {
    @NotBlank private String title; private String description;
    @NotBlank private String propertyType; @NotBlank private String listingType;
    @NotBlank private String county; private String subCounty, city, locationDescription;
    private Double latitude, longitude;
    @NotNull @DecimalMin("0.01") private BigDecimal price;
    private Integer bedrooms, bathrooms, yearBuilt; private Double areaSqm;
    private List<String> imageUrls;
}
