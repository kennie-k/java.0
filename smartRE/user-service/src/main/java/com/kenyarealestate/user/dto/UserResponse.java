package com.kenyarealestate.user.dto;
import lombok.*; import java.time.LocalDateTime; import java.util.UUID;
@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class UserResponse {
    private UUID id; private String fullName, email, phone, role;
    private boolean isVerified; private boolean isActive; private String profileImage;
    private String accountType, companyName, companyRegNumber, kraPin;
    private String paybillNumber, tillNumber, bankAccountName, bankAccountNumber;
    private String bankName, bankBranch, bankSwiftCode;
    private String preferredPayoutMethod, payoutPhone;
    private LocalDateTime createdAt;
}
