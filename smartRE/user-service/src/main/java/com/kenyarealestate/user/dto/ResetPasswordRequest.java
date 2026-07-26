package com.kenyarealestate.user.dto;
import jakarta.validation.constraints.*; import lombok.Data;
@Data public class ResetPasswordRequest {
    @NotBlank private String token;
    @NotBlank @Size(min=8,message="Password must be at least 8 characters") private String newPassword;
}
