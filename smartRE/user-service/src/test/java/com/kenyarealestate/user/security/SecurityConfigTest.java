package com.kenyarealestate.user.security;

import com.kenyarealestate.user.controller.AuthController;
import com.kenyarealestate.user.controller.UserController;
import com.kenyarealestate.user.dto.UserResponse;
import com.kenyarealestate.user.service.TokenBlacklistService;
import com.kenyarealestate.user.service.UserService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Exercises the real SecurityConfig authorization rules end-to-end through MockMvc (JWT filter,
 * internal-secret filter, and the authorizeHttpRequests() matchers included) rather than just
 * asserting on the declarative config, so a regression in the matcher order/roles actually fails
 * a test.
 */
@WebMvcTest(controllers = {UserController.class, AuthController.class})
@Import({SecurityConfig.class, JwtAuthenticationFilter.class, InternalSecretFilter.class, JwtUtil.class})
@TestPropertySource(properties = {
        "jwt.secret=test-only-secret-key-must-be-at-least-256-bits-long-for-hs256!!",
        "jwt.expiration=86400000",
        "services.internal-secret=test-internal-secret",
        "cookie.secure=false"
})
class SecurityConfigTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private JwtUtil jwtUtil;

    @MockitoBean private UserService userService;
    @MockitoBean private TokenBlacklistService tokenBlacklistService;
    @MockitoBean private UserDetailsService userDetailsService;

    private String tokenFor(String email, String role) {
        when(userDetailsService.loadUserByUsername(email)).thenReturn(
                User.withUsername(email).password("x").authorities(List.of(
                        new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_" + role)
                )).build());
        return jwtUtil.generateToken(email, role, UUID.randomUUID());
    }

    @Test
    void adminEndpoint_rejectsRequestWithNoToken() throws Exception {
        mockMvc.perform(get("/api/users/admin/all"))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminEndpoint_rejectsNonAdminRole() throws Exception {
        String token = tokenFor("buyer@smartre.co.ke", "BUYER");

        mockMvc.perform(get("/api/users/admin/all").header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminEndpoint_allowsAdminRole() throws Exception {
        when(userService.getAll(any())).thenReturn(Page.empty());
        String token = tokenFor("admin@smartre.co.ke", "ADMIN");

        mockMvc.perform(get("/api/users/admin/all").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
    }

    @Test
    void meEndpoint_rejectsUnauthenticatedRequest() throws Exception {
        mockMvc.perform(get("/api/users/me"))
                .andExpect(status().isForbidden());
    }

    @Test
    void meEndpoint_allowsAuthenticatedUser() throws Exception {
        when(userService.getProfile(anyString())).thenReturn(UserResponse.builder().build());
        String token = tokenFor("jane@smartre.co.ke", "SELLER");

        mockMvc.perform(get("/api/users/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
    }

    @Test
    void getUserById_isPubliclyReadable_withoutAuthentication() throws Exception {
        when(userService.getById(any(), any())).thenReturn(UserResponse.builder().build());

        mockMvc.perform(get("/api/users/" + UUID.randomUUID()))
                .andExpect(status().isOk());
    }

    @Test
    void authRegisterAndLogin_arePubliclyReachable_withoutAuthentication() throws Exception {
        // A malformed/empty body still proves the request reached the controller (400 from
        // bean validation) rather than being blocked by the security filter chain (401/403).
        mockMvc.perform(post("/api/auth/register").contentType("application/json").content("{}"))
                .andExpect(status().isBadRequest());

        mockMvc.perform(post("/api/auth/login").contentType("application/json").content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void unrevokedValidToken_isAuthenticated_onProtectedEndpoint() throws Exception {
        when(userService.getProfile(anyString())).thenReturn(UserResponse.builder().build());
        String token = tokenFor("jane@smartre.co.ke", "SELLER");
        when(tokenBlacklistService.isBlacklisted(token)).thenReturn(false);
        when(tokenBlacklistService.isIssuedBeforeInvalidation(any(), any())).thenReturn(false);

        mockMvc.perform(get("/api/users/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
    }

    @Test
    void blacklistedToken_isRejectedWithUnauthorized_onProtectedEndpoint() throws Exception {
        String token = tokenFor("jane@smartre.co.ke", "SELLER");
        when(tokenBlacklistService.isBlacklisted(token)).thenReturn(true);

        mockMvc.perform(get("/api/users/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized());
    }
}
