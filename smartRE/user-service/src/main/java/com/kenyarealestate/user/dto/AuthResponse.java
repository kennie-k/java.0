package com.kenyarealestate.user.dto;
import lombok.*; import java.util.UUID;
@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class AuthResponse {
    private String token; private UUID userId;
    private String fullName, email, role;
    private boolean isVerified;
}
