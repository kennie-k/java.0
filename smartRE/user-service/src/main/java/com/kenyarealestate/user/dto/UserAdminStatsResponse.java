package com.kenyarealestate.user.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserAdminStatsResponse {
    private long buyers;
    private long sellers;
    private long agents;
    private long admins;
    private long total;
    private long verified;
}
