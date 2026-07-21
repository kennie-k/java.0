package com.kenyarealestate.user.dto;
import lombok.Data;
@Data public class UpdateProfileRequest {
    private String fullName;
    private String phone;
    private String profileImage;
    private String accountType;
    private String companyName;
    private String companyRegNumber;
    private String kraPin;
    private String paybillNumber;
    private String tillNumber;
    private String bankAccountName;
    private String bankAccountNumber;
    private String bankName;
    private String bankBranch;
    private String bankSwiftCode;
    private String preferredPayoutMethod;
    private String payoutPhone;
}
