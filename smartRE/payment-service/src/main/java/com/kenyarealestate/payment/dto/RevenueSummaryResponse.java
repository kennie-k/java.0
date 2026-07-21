package com.kenyarealestate.payment.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RevenueSummaryResponse {
    private BigDecimal totalPlatformFees;
    private BigDecimal viewingFeeRevenue;
    private BigDecimal commissionRevenue;
    private BigDecimal thisMonthRevenue;
    private BigDecimal lastMonthRevenue;
    private long totalTransactions;
    private long thisMonthTransactions;
    private String currency;
}
