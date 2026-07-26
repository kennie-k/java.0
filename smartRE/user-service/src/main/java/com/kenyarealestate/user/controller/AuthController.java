package com.kenyarealestate.user.controller;

import com.kenyarealestate.user.dto.*;
import com.kenyarealestate.user.service.TokenBlacklistService;
import com.kenyarealestate.user.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

@RestController @RequestMapping("/api/auth")
public class AuthController {
    private final UserService svc;
    private final TokenBlacklistService blacklist;

    @Value("${jwt.expiration}")
    private long jwtExpirationMs;

    @Value("${cookie.secure:false}")
    private boolean cookieSecure;

    @Value("${cookie.domain:}")
    private String cookieDomain;

    public AuthController(UserService svc, TokenBlacklistService blacklist) {
        this.svc = svc; this.blacklist = blacklist;
    }

    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterRequest req) {
        AuthResponse res = svc.register(req);
        return ResponseEntity.status(HttpStatus.CREATED)
                .header(HttpHeaders.SET_COOKIE, buildCookie(res.getToken()).toString())
                .body(res);
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest req) {
        AuthResponse res = svc.login(req);
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, buildCookie(res.getToken()).toString())
                .body(res);
    }

    @PostMapping("/forgot-password")
    public ResponseEntity<Void> forgotPassword(@Valid @RequestBody ForgotPasswordRequest req) {
        svc.requestPasswordReset(req.getEmail());
        return ResponseEntity.ok().build();
    }

    @PostMapping("/reset-password")
    public ResponseEntity<Void> resetPassword(@Valid @RequestBody ResetPasswordRequest req) {
        svc.resetPassword(req.getToken(), req.getNewPassword());
        return ResponseEntity.ok().build();
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(HttpServletRequest req) {
        String token = extractToken(req);
        if (StringUtils.hasText(token)) {
            blacklist.blacklist(token);
        }
        return ResponseEntity.noContent()
                .header(HttpHeaders.SET_COOKIE, clearCookie().toString())
                .build();
    }

    private ResponseCookie buildCookie(String token) {
        var b = ResponseCookie.from("sre_token", token)
                .httpOnly(true)
                .secure(cookieSecure)
                .sameSite("Lax")
                .path("/")
                .maxAge(jwtExpirationMs / 1000);
        if (StringUtils.hasText(cookieDomain)) b.domain(cookieDomain);
        return b.build();
    }

    private ResponseCookie clearCookie() {
        var b = ResponseCookie.from("sre_token", "")
                .httpOnly(true)
                .secure(cookieSecure)
                .sameSite("Lax")
                .path("/")
                .maxAge(0);
        if (StringUtils.hasText(cookieDomain)) b.domain(cookieDomain);
        return b.build();
    }

    private String extractToken(HttpServletRequest req) {
        if (req.getCookies() != null) {
            for (var c : req.getCookies()) {
                if ("sre_token".equals(c.getName()) && StringUtils.hasText(c.getValue())) return c.getValue();
            }
        }
        String h = req.getHeader("Authorization");
        return (StringUtils.hasText(h) && h.startsWith("Bearer ")) ? h.substring(7) : null;
    }
}
