package com.kenyarealestate.payment.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class ReleaseEscrowRequest {

    @NotBlank
    private String payoutMethod;

    private String sellerPhone;

    private String paybillNumber;

    private String tillNumber;

    private String accountNumber;

    private String bankAccountNumber;

    private String bankName;

    private String bankBranch;

    private String accountName;

    private String notes;
}
