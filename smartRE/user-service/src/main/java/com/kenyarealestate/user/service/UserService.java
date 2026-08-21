package com.kenyarealestate.user.service;

import com.kenyarealestate.user.client.PropertyServiceClient;
import com.kenyarealestate.user.dto.*;
import com.kenyarealestate.user.entity.*;
import com.kenyarealestate.user.exception.*;
import com.kenyarealestate.user.repository.UserRepository;
import com.kenyarealestate.user.security.JwtUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.*;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.StringUtils;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.UUID;

@Slf4j
@Service @Transactional
public class UserService {
    private final UserRepository repo;
    private final PasswordEncoder encoder;
    private final JwtUtil jwtUtil;
    private final LoginAttemptService loginAttemptService;
    private final org.springframework.data.redis.core.RedisTemplate<String, Object> redis;
    private final EmailService emailService;
    private final PropertyServiceClient propertyServiceClient;
    private final TokenBlacklistService tokenBlacklistService;
    private final AuditService auditService;

    private static final String PROFILE_ACCESS_PREFIX = "profile:access:";
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    @Value("${frontend.url:http://localhost:3000}")
    private String frontendUrl;

    public UserService(UserRepository repo, PasswordEncoder encoder, JwtUtil jwtUtil, LoginAttemptService loginAttemptService,
                        org.springframework.data.redis.core.RedisTemplate<String, Object> redis, EmailService emailService,
                        PropertyServiceClient propertyServiceClient, TokenBlacklistService tokenBlacklistService,
                        AuditService auditService) {
        this.repo=repo; this.encoder=encoder; this.jwtUtil=jwtUtil; this.loginAttemptService=loginAttemptService; this.redis=redis;
        this.emailService=emailService; this.propertyServiceClient=propertyServiceClient;
        this.tokenBlacklistService = tokenBlacklistService;
        this.auditService = auditService;
    }

    public AuthResponse register(RegisterRequest req) {
        if (repo.existsByEmail(req.getEmail())) throw new ConflictException("Email already registered");
        if (req.getPhone() != null && repo.existsByPhone(req.getPhone())) throw new ConflictException("Phone already registered");
        String requestedRole = req.getRole() == null ? "" : req.getRole().toUpperCase();
        if (requestedRole.equals("ADMIN")) {
            if (repo.existsByRole(Role.ADMIN)) {
                throw new ForbiddenException("Admin registration is closed. Ask an existing admin to promote your account.");
            }
        } else if (!requestedRole.equals("BUYER") && !requestedRole.equals("SELLER")) {
            throw new BadRequestException("Role must be BUYER or SELLER");
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
            throw new TooManyRequestsException("Too many failed login attempts. Try again in a few minutes.");
        }
        User u = repo.findByEmail(email).orElseThrow(() -> {
            loginAttemptService.recordFailure(email);
            return new UnauthorizedException("Invalid email or password");
        });
        if (!u.isActive()) throw new ForbiddenException("Account is deactivated");
        if (!encoder.matches(req.getPassword(), u.getPassword())) {
            loginAttemptService.recordFailure(email);
            throw new UnauthorizedException("Invalid email or password");
        }
        loginAttemptService.recordSuccess(email);
        return toAuth(u, jwtUtil.generateToken(u.getEmail(), u.getRole().name(), u.getId()));
    }

    @Transactional(readOnly = true)
    public UserResponse getById(UUID id, String callerEmail) {
        UserResponse resp = toResp(repo.findById(id).orElseThrow(() -> new NotFoundException("User not found")));
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
        return toResp(repo.findByEmail(email).orElseThrow(() -> new NotFoundException("User not found")));
    }

    public UserResponse updateProfile(String email, UpdateProfileRequest req) {
        User u = repo.findByEmail(email).orElseThrow(() -> new NotFoundException("User not found"));
        if (StringUtils.hasText(req.getFullName())) u.setFullName(req.getFullName());
        if (req.getPhone() != null && !req.getPhone().equals(u.getPhone())) {
            if (repo.existsByPhone(req.getPhone())) throw new ConflictException("Phone already in use");
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

    @Transactional(readOnly = true)
    public UserAdminStatsResponse getAdminStats() {
        return UserAdminStatsResponse.builder()
                .buyers(repo.countByRole(Role.BUYER))
                .sellers(repo.countByRole(Role.SELLER))
                .agents(repo.countByRole(Role.AGENT))
                .admins(repo.countByRole(Role.ADMIN))
                .total(repo.count())
                .verified(repo.countByVerifiedTrue())
                .build();
    }

    public void changePassword(String email, ChangePasswordRequest req) {
        User u = repo.findByEmail(email).orElseThrow(() -> new NotFoundException("User not found"));
        if (!encoder.matches(req.getCurrentPassword(), u.getPassword())) {
            throw new UnauthorizedException("Current password is incorrect");
        }
        u.setPassword(encoder.encode(req.getNewPassword()));
        repo.save(u);
        tokenBlacklistService.invalidateTokensBefore(u.getId());
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
                .orElseThrow(() -> new BadRequestException("Invalid or expired reset link"));
        if (u.getResetTokenExpiry() == null || u.getResetTokenExpiry().isBefore(LocalDateTime.now())) {
            throw new BadRequestException("Invalid or expired reset link");
        }
        u.setPassword(encoder.encode(newPassword));
        u.setResetTokenHash(null);
        u.setResetTokenExpiry(null);
        repo.save(u);
        tokenBlacklistService.invalidateTokensBefore(u.getId());
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

    /**
     * Only the (single, undeletable) super admin may mint new admins. This closes the gap where
     * any ordinary ADMIN could promote arbitrary users to ADMIN, bypassing the "admin
     * registration is closed after the first admin" rule enforced in register().
     */
    public UserResponse promoteToAdmin(UUID id, String callerEmail) {
        User caller = repo.findByEmail(callerEmail)
                .orElseThrow(() -> new ForbiddenException("Caller not found"));
        if (!caller.isSuperAdmin()) {
            throw new ForbiddenException("Only the super admin can promote users to ADMIN.");
        }
        User u = repo.findById(id).orElseThrow(() -> new NotFoundException("User not found"));
        String previousRole = u.getRole().name();
        u.setRole(Role.ADMIN);
        UserResponse resp = toResp(repo.save(u));
        auditService.log(u.getId(), "PROMOTED_TO_ADMIN", caller.getId(), "SUPER_ADMIN",
                previousRole, Role.ADMIN.name(), null);
        return resp;
    }

    private static final String BANNED_USER_PREFIX = "user:banned:";

    public UserResponse ban(UUID id, String callerEmail) {
        User caller = repo.findByEmail(callerEmail).orElse(null);
        User u = repo.findById(id).orElseThrow(() -> new NotFoundException("User not found"));
        if (u.isSuperAdmin()) throw new ForbiddenException("The super admin account cannot be banned");
        if (u.getRole() == Role.ADMIN) throw new ForbiddenException("Cannot ban an admin account");
        u.setActive(false);
        UserResponse resp = toResp(repo.save(u));
        auditService.log(id, "BANNED", caller != null ? caller.getId() : null, "ADMIN",
                "ACTIVE", "INACTIVE", null);

        // The Redis flag and the downstream property-service call are side effects that must
        // never fire unless the ban itself actually commits — otherwise a rolled-back
        // transaction (e.g. a later validation failure) would leave Redis/property-service
        // believing the user is banned while the DB says they're still active.
        runAfterCommitOrNow(() -> {
            redis.opsForValue().set(BANNED_USER_PREFIX + id, "true");
            propertyServiceClient.suspendAllListingsForSeller(id, "Seller account banned by admin");
        });
        return resp;
    }

    public UserResponse unban(UUID id, String callerEmail) {
        User caller = repo.findByEmail(callerEmail).orElse(null);
        User u = repo.findById(id).orElseThrow(() -> new NotFoundException("User not found"));
        u.setActive(true);
        UserResponse resp = toResp(repo.save(u));
        auditService.log(id, "UNBANNED", caller != null ? caller.getId() : null, "ADMIN",
                "INACTIVE", "ACTIVE", null);

        runAfterCommitOrNow(() -> redis.delete(BANNED_USER_PREFIX + id));
        return resp;
    }

    /**
     * Runs the given side effect after the current transaction commits, so it can never run
     * following a rollback. Falls back to running immediately when there's no active
     * transaction synchronization (e.g. plain unit tests invoking the service directly).
     */
    private void runAfterCommitOrNow(Runnable action) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    action.run();
                }
            });
        } else {
            action.run();
        }
    }

    private AuthResponse toAuth(User u, String token) {
        return AuthResponse.builder().token(token).userId(u.getId()).fullName(u.getFullName())
                .email(u.getEmail()).role(u.getRole().name()).isVerified(u.isVerified()).build();
    }
    private UserResponse toResp(User u) {
        return UserResponse.builder().id(u.getId()).fullName(u.getFullName()).email(u.getEmail())
                .phone(u.getPhone()).role(u.getRole().name()).isVerified(u.isVerified()).isActive(u.isActive())
                .isSuperAdmin(u.isSuperAdmin())
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
