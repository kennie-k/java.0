package com.kenyarealestate.user.dto;
import jakarta.validation.constraints.*; import lombok.Data;
@Data public class RegisterRequest {
    @NotBlank private String fullName;
    @NotBlank @Email private String email;
    @NotBlank @Size(min=8,message="Password must be at least 8 characters") private String password;
    private String phone;
    @NotBlank private String role;
}
