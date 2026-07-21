package com.kenyarealestate.property.dto;
import lombok.Data; import java.math.BigDecimal; import java.util.List;
@Data public class UpdatePropertyRequest {
    private String title, description, county, subCounty, city, locationDescription;
    private Double latitude, longitude, areaSqm;
    private BigDecimal price; private Integer bedrooms, bathrooms, yearBuilt;
    private List<String> imageUrls;
}
