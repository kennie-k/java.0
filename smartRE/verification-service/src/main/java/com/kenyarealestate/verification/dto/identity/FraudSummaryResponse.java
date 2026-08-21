package com.kenyarealestate.verification.dto.identity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FraudSummaryResponse {
    private long totalFraudStrikes;
    private long permanentlyBanned;
}
