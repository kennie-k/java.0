package com.kenyarealestate.user.service;

import com.kenyarealestate.user.dto.*;
import com.kenyarealestate.user.entity.*;
import com.kenyarealestate.user.repository.UserRepository;
import com.kenyarealestate.user.security.JwtUtil;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.*;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.UUID;

@Service @Transactional
public class UserService {
    private final UserRepository repo;
    private final PasswordEncoder encoder;
    private final JwtUtil jwtUtil;
    private final LoginAttemptService loginAttemptService;
    private final org.springframework.data.redis.core.RedisTemplate<String, Object> redis;
    private final EmailService emailService;

    private static final String PROFILE_ACCESS_PREFIX = "profile:access:";
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    @Value("${frontend.url:http://localhost:3000}")
    private String frontendUrl;

    public UserService(UserRepository repo, PasswordEncoder encoder, JwtUtil jwtUtil, LoginAttemptService loginAttemptService,
                        org.springframework.data.redis.core.RedisTemplate<String, Object> redis, EmailService emailService) {
        this.repo=repo; this.encoder=encoder; this.jwtUtil=jwtUtil; this.loginAttemptService=loginAttemptService; this.redis=redis;
        this.emailService=emailService;
    }

    public AuthResponse register(RegisterRequest req) {
        if (repo.existsByEmail(req.getEmail())) throw new RuntimeException("Email already registered");
        if (req.getPhone() != null && repo.existsByPhone(req.getPhone())) throw new RuntimeException("Phone already registered");
        String requestedRole = req.getRole() == null ? "" : req.getRole().toUpperCase();
        if (requestedRole.equals("ADMIN")) {
            if (repo.existsByRole(Role.ADMIN)) {
                throw new RuntimeException("Admin registration is closed. Ask an existing admin to promote your account.");
            }
        } else if (!requestedRole.equals("BUYER") && !requestedRole.equals("SELLER")) {
            throw new RuntimeException("Role must be BUYER or SELLER");
        }
        User u = User.builder()
                .fullName(req.getFullName()).email(req.getEmail().toLowerCase().trim())
                .password(encoder.encode(req.getPassword())).phone(req.getPhone())
                .role(Role.valueOf(requestedRole)).build();
        User saved = repo.save(u);
        return toAuth(saved, jwtUtil.generateToken(saved.getEmail(), saved.getRole().name(), saved.getId()));
    }

    public AuthResponse login(LoginRequest req) {
        String email = req.getEmail().toLowerCase().trim();
        if (loginAttemptService.isLocked(email)) {
            throw new RuntimeException("Too many failed login attempts. Try again in a few minutes.");
        }
        User u = repo.findByEmail(email).orElseThrow(() -> {
            loginAttemptService.recordFailure(email);
            return new RuntimeException("Invalid email or password");
        });
        if (!u.isActive()) throw new RuntimeException("Account is deactivated");
        if (!encoder.matches(req.getPassword(), u.getPassword())) {
            loginAttemptService.recordFailure(email);
            throw new RuntimeException("Invalid email or password");
        }
        loginAttemptService.recordSuccess(email);
        return toAuth(u, jwtUtil.generateToken(u.getEmail(), u.getRole().name(), u.getId()));
    }

    @Transactional(readOnly = true)
    public UserResponse getById(UUID id, String callerEmail) {
        UserResponse resp = toResp(repo.findById(id).orElseThrow(() -> new RuntimeException("User not found")));
        if (callerEmail != null) {
            User caller = repo.findByEmail(callerEmail).orElse(null);
            if (caller != null) {
                if (caller.getId().equals(id)) return resp;
                String key = PROFILE_ACCESS_PREFIX + caller.getId() + ":" + id;
                if (Boolean.TRUE.equals(redis.hasKey(key))) return redactPayoutDetails(resp);
            }
        }
        return redactAll(resp);
    }

    private UserResponse redactPayoutDetails(UserResponse resp) {
        resp.setKraPin(null);
        resp.setPaybillNumber(null);
        resp.setTillNumber(null);
        resp.setBankAccountName(null);
        resp.setBankAccountNumber(null);
        resp.setBankName(null);
        resp.setBankBranch(null);
        resp.setBankSwiftCode(null);
        resp.setPayoutPhone(null);
        return resp;
    }

    private UserResponse redactAll(UserResponse resp) {
        resp.setPhone(null);
        resp.setEmail(null);
        return redactPayoutDetails(resp);
    }

    @Transactional(readOnly = true)
    public UserResponse getProfile(String email) {
        return toResp(repo.findByEmail(email).orElseThrow(() -> new RuntimeException("User not found")));
    }

    public UserResponse updateProfile(String email, UpdateProfileRequest req) {
        User u = repo.findByEmail(email).orElseThrow(() -> new RuntimeException("User not found"));
        if (StringUtils.hasText(req.getFullName())) u.setFullName(req.getFullName());
        if (req.getPhone() != null && !req.getPhone().equals(u.getPhone())) {
            if (repo.existsByPhone(req.getPhone())) throw new RuntimeException("Phone already in use");
            u.setPhone(req.getPhone());
        }
        if (StringUtils.hasText(req.getProfileImage())) u.setProfileImage(req.getProfileImage());
        if (StringUtils.hasText(req.getAccountType())) u.setAccountType(req.getAccountType());
        if (StringUtils.hasText(req.getCompanyName())) u.setCompanyName(req.getCompanyName());
        if (StringUtils.hasText(req.getCompanyRegNumber())) u.setCompanyRegNumber(req.getCompanyRegNumber());
        if (StringUtils.hasText(req.getKraPin())) u.setKraPin(req.getKraPin());
        if (StringUtils.hasText(req.getPaybillNumber())) u.setPaybillNumber(req.getPaybillNumber());
        if (StringUtils.hasText(req.getTillNumber())) u.setTillNumber(req.getTillNumber());
        if (StringUtils.hasText(req.getBankAccountName())) u.setBankAccountName(req.getBankAccountName());
        if (StringUtils.hasText(req.getBankAccountNumber())) u.setBankAccountNumber(req.getBankAccountNumber());
        if (StringUtils.hasText(req.getBankName())) u.setBankName(req.getBankName());
        if (StringUtils.hasText(req.getBankBranch())) u.setBankBranch(req.getBankBranch());
        if (StringUtils.hasText(req.getBankSwiftCode())) u.setBankSwiftCode(req.getBankSwiftCode());
        if (StringUtils.hasText(req.getPreferredPayoutMethod())) u.setPreferredPayoutMethod(req.getPreferredPayoutMethod());
        if (StringUtils.hasText(req.getPayoutPhone())) u.setPayoutPhone(req.getPayoutPhone());
        return toResp(repo.save(u));
    }

    @Transactional(readOnly = true)
    public Page<UserResponse> getAll(Pageable p) { return repo.findAll(p).map(this::toResp); }

    public void changePassword(String email, ChangePasswordRequest req) {
        User u = repo.findByEmail(email).orElseThrow(() -> new RuntimeException("User not found"));
        if (!encoder.matches(req.getCurrentPassword(), u.getPassword())) {
            throw new RuntimeException("Current password is incorrect");
        }
        u.setPassword(encoder.encode(req.getNewPassword()));
        repo.save(u);
    }

    public void requestPasswordReset(String email) {
        User u = repo.findByEmail(email.toLowerCase().trim()).orElse(null);
        if (u == null) return;

        byte[] tokenBytes = new byte[32];
        SECURE_RANDOM.nextBytes(tokenBytes);
        String rawToken = Base64.getUrlEncoder().withoutPadding().encodeToString(tokenBytes);

        u.setResetTokenHash(hashToken(rawToken));
        u.setResetTokenExpiry(LocalDateTime.now().plusMinutes(30));
        repo.save(u);

        String link = frontendUrl + "/reset-password?token=" + rawToken;
        emailService.send(u.getEmail(), "Reset your smartRE password",
                "We received a request to reset your smartRE password.\n\n" +
                "This link expires in 30 minutes:\n" + link + "\n\n" +
                "If you didn't request this, you can safely ignore this email.");
    }

    public void resetPassword(String rawToken, String newPassword) {
        User u = repo.findByResetTokenHash(hashToken(rawToken))
                .orElseThrow(() -> new RuntimeException("Invalid or expired reset link"));
        if (u.getResetTokenExpiry() == null || u.getResetTokenExpiry().isBefore(LocalDateTime.now())) {
            throw new RuntimeException("Invalid or expired reset link");
        }
        u.setPassword(encoder.encode(newPassword));
        u.setResetTokenHash(null);
        u.setResetTokenExpiry(null);
        repo.save(u);
    }

    private String hashToken(String rawToken) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(rawToken.getBytes(StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }

    public UserResponse promoteToAdmin(UUID id) {
        User u = repo.findById(id).orElseThrow(() -> new RuntimeException("User not found"));
        u.setRole(Role.ADMIN);
        return toResp(repo.save(u));
    }

    public UserResponse ban(UUID id) {
        User u = repo.findById(id).orElseThrow(() -> new RuntimeException("User not found"));
        if (u.getRole() == Role.ADMIN) throw new RuntimeException("Cannot ban an admin account");
        u.setActive(false);
        return toResp(repo.save(u));
    }

    public UserResponse unban(UUID id) {
        User u = repo.findById(id).orElseThrow(() -> new RuntimeException("User not found"));
        u.setActive(true);
        return toResp(repo.save(u));
    }

    private AuthResponse toAuth(User u, String token) {
        return AuthResponse.builder().token(token).userId(u.getId()).fullName(u.getFullName())
                .email(u.getEmail()).role(u.getRole().name()).isVerified(u.isVerified()).build();
    }
    private UserResponse toResp(User u) {
        return UserResponse.builder().id(u.getId()).fullName(u.getFullName()).email(u.getEmail())
                .phone(u.getPhone()).role(u.getRole().name()).isVerified(u.isVerified()).isActive(u.isActive())
                .profileImage(u.getProfileImage()).createdAt(u.getCreatedAt())
                .accountType(u.getAccountType()).companyName(u.getCompanyName())
                .companyRegNumber(u.getCompanyRegNumber()).kraPin(u.getKraPin())
                .paybillNumber(u.getPaybillNumber()).tillNumber(u.getTillNumber())
                .bankAccountName(u.getBankAccountName()).bankAccountNumber(u.getBankAccountNumber())
                .bankName(u.getBankName()).bankBranch(u.getBankBranch()).bankSwiftCode(u.getBankSwiftCode())
                .preferredPayoutMethod(u.getPreferredPayoutMethod()).payoutPhone(u.getPayoutPhone())
                .build();
    }
}
