package com.kenyarealestate.property.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PropertyAdminStatsResponse {
    private long active;
    private long draft;
    private long pendingVerification;
    private long sold;
    private long rented;
    private long suspended;
    private long withdrawn;
    private BigDecimal avgActivePrice;
    private long totalViews;
    private List<TypeCount> byType;
    private List<CountyCount> topCounties;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TypeCount {
        private String name;
        private long value;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CountyCount {
        private String name;
        private long value;
    }
}
