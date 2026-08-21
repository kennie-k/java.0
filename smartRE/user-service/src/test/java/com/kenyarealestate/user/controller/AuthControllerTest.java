package com.kenyarealestate.user.controller;

import com.kenyarealestate.user.dto.*;
import com.kenyarealestate.user.service.TokenBlacklistService;
import com.kenyarealestate.user.service.UserService;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Covers the register/login/forgot-password/reset-password/logout flows at the controller
 * level: that the auth cookie is set/cleared correctly and that logout actually blacklists
 * the token (the other half of the JWT-revocation fix — UserService's unit tests cover the
 * business-rule side of these flows).
 */
@ExtendWith(MockitoExtension.class)
class AuthControllerTest {

    @Mock private UserService userService;
    @Mock private TokenBlacklistService blacklist;

    private AuthController controller;

    @BeforeEach
    void setup() {
        controller = new AuthController(userService, blacklist);
        ReflectionTestUtils.setField(controller, "jwtExpirationMs", 86_400_000L);
        ReflectionTestUtils.setField(controller, "cookieSecure", false);
        ReflectionTestUtils.setField(controller, "cookieDomain", "");
    }

    private AuthResponse sampleAuthResponse() {
        return AuthResponse.builder()
                .token("jwt-token-value")
                .userId(UUID.randomUUID())
                .fullName("Jane Seller")
                .email("jane@smartre.co.ke")
                .role("SELLER")
                .isVerified(false)
                .build();
    }

    @Test
    void register_setsAuthCookie_andReturns201() {
        when(userService.register(any())).thenReturn(sampleAuthResponse());

        var res = controller.register(new RegisterRequest());

        assertEquals(HttpStatus.CREATED, res.getStatusCode());
        String setCookie = res.getHeaders().getFirst("Set-Cookie");
        assertNotNull(setCookie);
        assertTrue(setCookie.contains("sre_token=jwt-token-value"));
        assertTrue(setCookie.contains("HttpOnly"));
    }

    @Test
    void login_setsAuthCookie_andReturns200() {
        when(userService.login(any())).thenReturn(sampleAuthResponse());

        var res = controller.login(new LoginRequest());

        assertEquals(HttpStatus.OK, res.getStatusCode());
        String setCookie = res.getHeaders().getFirst("Set-Cookie");
        assertNotNull(setCookie);
        assertTrue(setCookie.contains("sre_token=jwt-token-value"));
    }

    @Test
    void login_propagatesServiceException_withoutSettingCookie() {
        when(userService.login(any())).thenThrow(new RuntimeException("Invalid email or password"));

        assertThrows(RuntimeException.class, () -> controller.login(new LoginRequest()));
    }

    @Test
    void forgotPassword_delegatesToService_andReturns200() {
        ForgotPasswordRequest req = new ForgotPasswordRequest();
        req.setEmail("jane@smartre.co.ke");

        var res = controller.forgotPassword(req);

        assertEquals(HttpStatus.OK, res.getStatusCode());
        verify(userService).requestPasswordReset("jane@smartre.co.ke");
    }

    @Test
    void resetPassword_delegatesToService_andReturns200() {
        ResetPasswordRequest req = new ResetPasswordRequest();
        req.setToken("raw-token");
        req.setNewPassword("NewPass1!");

        var res = controller.resetPassword(req);

        assertEquals(HttpStatus.OK, res.getStatusCode());
        verify(userService).resetPassword("raw-token", "NewPass1!");
    }

    @Test
    void logout_blacklistsTokenFromCookie_andClearsCookie() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setCookies(new Cookie("sre_token", "jwt-token-value"));

        var res = controller.logout(request);

        assertEquals(HttpStatus.NO_CONTENT, res.getStatusCode());
        verify(blacklist).blacklist("jwt-token-value");
        String setCookie = res.getHeaders().getFirst("Set-Cookie");
        assertNotNull(setCookie);
        assertTrue(setCookie.contains("sre_token="));
        assertTrue(setCookie.contains("Max-Age=0"));
    }

    @Test
    void logout_blacklistsTokenFromAuthorizationHeader_whenNoCookiePresent() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer header-token-value");

        controller.logout(request);

        verify(blacklist).blacklist("header-token-value");
    }

    @Test
    void logout_doesNotBlacklist_whenNoTokenPresentAtAll() {
        MockHttpServletRequest request = new MockHttpServletRequest();

        var res = controller.logout(request);

        assertEquals(HttpStatus.NO_CONTENT, res.getStatusCode());
        verify(blacklist, never()).blacklist(any());
    }
}
