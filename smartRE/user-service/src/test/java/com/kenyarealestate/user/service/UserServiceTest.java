package com.kenyarealestate.user.service;

import com.kenyarealestate.user.client.PropertyServiceClient;
import com.kenyarealestate.user.dto.AuthResponse;
import com.kenyarealestate.user.dto.LoginRequest;
import com.kenyarealestate.user.dto.RegisterRequest;
import com.kenyarealestate.user.entity.Role;
import com.kenyarealestate.user.entity.User;
import com.kenyarealestate.user.exception.*;
import com.kenyarealestate.user.repository.UserRepository;
import com.kenyarealestate.user.security.JwtUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock private UserRepository repo;
    @Mock private PasswordEncoder encoder;
    @Mock private JwtUtil jwtUtil;
    @Mock private LoginAttemptService loginAttemptService;
    @Mock private org.springframework.data.redis.core.RedisTemplate<String, Object> redis;
    @Mock private org.springframework.data.redis.core.ValueOperations<String, Object> valueOperations;
    @Mock private EmailService emailService;
    @Mock private PropertyServiceClient propertyServiceClient;
    @Mock private TokenBlacklistService tokenBlacklistService;
    @Mock private AuditService auditService;

    @InjectMocks private UserService userService;

    private static final String ADMIN_EMAIL = "admin@smartre.co.ke";

    private RegisterRequest registerRequest(String role) {
        RegisterRequest req = new RegisterRequest();
        req.setFullName("Test User");
        req.setEmail("test@smartre.co.ke");
        req.setPassword("Password1!");
        req.setPhone("254712345678");
        req.setRole(role);
        return req;
    }

    private User buildUser(Role role) {
        return User.builder()
                .id(UUID.randomUUID())
                .fullName("Test User")
                .email("test@smartre.co.ke")
                .password("hashed")
                .role(role)
                .build();
    }

    private User superAdmin() {
        return User.builder()
                .id(UUID.randomUUID())
                .fullName("Super Admin")
                .email(ADMIN_EMAIL)
                .password("hashed")
                .role(Role.ADMIN)
                .superAdmin(true)
                .build();
    }

    private User ordinaryAdmin() {
        return User.builder()
                .id(UUID.randomUUID())
                .fullName("Ordinary Admin")
                .email(ADMIN_EMAIL)
                .password("hashed")
                .role(Role.ADMIN)
                .superAdmin(false)
                .build();
    }

    @BeforeEach
    void setup() {
        when(repo.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));
        lenient().when(redis.opsForValue()).thenReturn(valueOperations);
    }

    // ---------- register ----------

    @Test
    void register_bootstrapsFirstAdmin_whenNoneExists() {
        when(repo.existsByEmail(anyString())).thenReturn(false);
        when(repo.existsByRole(Role.ADMIN)).thenReturn(false);
        when(encoder.encode(anyString())).thenReturn("hashed");
        when(jwtUtil.generateToken(anyString(), anyString(), any(UUID.class))).thenReturn("mock-jwt-token");

        AuthResponse res = userService.register(registerRequest("ADMIN"));

        assertEquals("ADMIN", res.getRole());
        verify(repo).save(any(User.class));
    }

    @Test
    void register_rejectsAdmin_whenAdminAlreadyExists() {
        when(repo.existsByEmail(anyString())).thenReturn(false);
        when(repo.existsByRole(Role.ADMIN)).thenReturn(true);

        ForbiddenException ex = assertThrows(ForbiddenException.class,
                () -> userService.register(registerRequest("ADMIN")));

        assertTrue(ex.getMessage().contains("Admin registration is closed"));
        verify(repo, never()).save(any());
    }

    @Test
    void register_rejectsUnknownRole() {
        when(repo.existsByEmail(anyString())).thenReturn(false);

        BadRequestException ex = assertThrows(BadRequestException.class,
                () -> userService.register(registerRequest("SUPERUSER")));

        assertTrue(ex.getMessage().contains("Role must be BUYER or SELLER"));
        verify(repo, never()).save(any());
    }

    @Test
    void register_succeedsForBuyer() {
        when(repo.existsByEmail(anyString())).thenReturn(false);
        when(encoder.encode(anyString())).thenReturn("hashed");
        when(jwtUtil.generateToken(anyString(), anyString(), any(UUID.class))).thenReturn("mock-jwt-token");

        AuthResponse res = userService.register(registerRequest("BUYER"));

        assertEquals("BUYER", res.getRole());
        assertEquals("mock-jwt-token", res.getToken());
    }

    @Test
    void register_rejectsDuplicateEmail() {
        when(repo.existsByEmail(anyString())).thenReturn(true);

        assertThrows(ConflictException.class, () -> userService.register(registerRequest("BUYER")));
        verify(repo, never()).save(any());
    }

    @Test
    void register_rejectsDuplicatePhone() {
        when(repo.existsByEmail(anyString())).thenReturn(false);
        when(repo.existsByPhone(anyString())).thenReturn(true);

        assertThrows(ConflictException.class, () -> userService.register(registerRequest("BUYER")));
        verify(repo, never()).save(any());
    }

    // ---------- login ----------

    @Test
    void login_rejectsWhenLockedOut() {
        when(loginAttemptService.isLocked("test@smartre.co.ke")).thenReturn(true);

        LoginRequest req = new LoginRequest();
        req.setEmail("test@smartre.co.ke");
        req.setPassword("Password1!");

        TooManyRequestsException ex = assertThrows(TooManyRequestsException.class, () -> userService.login(req));

        assertTrue(ex.getMessage().contains("Too many failed login attempts"));
        verify(repo, never()).findByEmail(anyString());
    }

    @Test
    void login_recordsFailure_onWrongPassword() {
        User u = buildUser(Role.BUYER);
        u.setActive(true);
        when(loginAttemptService.isLocked(anyString())).thenReturn(false);
        when(repo.findByEmail(anyString())).thenReturn(Optional.of(u));
        when(encoder.matches(anyString(), anyString())).thenReturn(false);

        LoginRequest req = new LoginRequest();
        req.setEmail("test@smartre.co.ke");
        req.setPassword("wrong-password");

        assertThrows(UnauthorizedException.class, () -> userService.login(req));
        verify(loginAttemptService).recordFailure("test@smartre.co.ke");
    }

    @Test
    void login_rejectsUnknownEmail_withUnauthorized() {
        when(loginAttemptService.isLocked(anyString())).thenReturn(false);
        when(repo.findByEmail(anyString())).thenReturn(Optional.empty());

        LoginRequest req = new LoginRequest();
        req.setEmail("nobody@smartre.co.ke");
        req.setPassword("whatever");

        assertThrows(UnauthorizedException.class, () -> userService.login(req));
        verify(loginAttemptService).recordFailure("nobody@smartre.co.ke");
    }

    @Test
    void login_rejectsDeactivatedAccount() {
        User u = buildUser(Role.BUYER);
        u.setActive(false);
        when(loginAttemptService.isLocked(anyString())).thenReturn(false);
        when(repo.findByEmail(anyString())).thenReturn(Optional.of(u));

        LoginRequest req = new LoginRequest();
        req.setEmail("test@smartre.co.ke");
        req.setPassword("Password1!");

        assertThrows(ForbiddenException.class, () -> userService.login(req));
    }

    @Test
    void login_recordsSuccess_onCorrectPassword() {
        User u = buildUser(Role.BUYER);
        u.setActive(true);
        when(loginAttemptService.isLocked(anyString())).thenReturn(false);
        when(repo.findByEmail(anyString())).thenReturn(Optional.of(u));
        when(encoder.matches(anyString(), anyString())).thenReturn(true);
        when(jwtUtil.generateToken(anyString(), anyString(), any(UUID.class))).thenReturn("mock-jwt-token");

        LoginRequest req = new LoginRequest();
        req.setEmail("test@smartre.co.ke");
        req.setPassword("Password1!");

        AuthResponse res = userService.login(req);

        assertEquals("mock-jwt-token", res.getToken());
        verify(loginAttemptService).recordSuccess("test@smartre.co.ke");
        verify(loginAttemptService, never()).recordFailure(anyString());
    }

    // ---------- promoteToAdmin (guard + audit) ----------

    @Test
    void promoteToAdmin_setsRoleToAdmin_whenCallerIsSuperAdmin() {
        User target = buildUser(Role.SELLER);
        User caller = superAdmin();
        when(repo.findByEmail(ADMIN_EMAIL)).thenReturn(Optional.of(caller));
        when(repo.findById(target.getId())).thenReturn(Optional.of(target));

        userService.promoteToAdmin(target.getId(), ADMIN_EMAIL);

        assertEquals(Role.ADMIN, target.getRole());
        verify(repo).save(target);
        verify(auditService).log(eq(target.getId()), eq("PROMOTED_TO_ADMIN"), eq(caller.getId()),
                eq("SUPER_ADMIN"), eq("SELLER"), eq("ADMIN"), any());
    }

    @Test
    void promoteToAdmin_rejectsNonSuperAdminCaller() {
        User target = buildUser(Role.SELLER);
        User caller = ordinaryAdmin();
        when(repo.findByEmail(ADMIN_EMAIL)).thenReturn(Optional.of(caller));

        ForbiddenException ex = assertThrows(ForbiddenException.class,
                () -> userService.promoteToAdmin(target.getId(), ADMIN_EMAIL));

        assertTrue(ex.getMessage().contains("super admin"));
        verify(repo, never()).save(any());
        verify(auditService, never()).log(any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void promoteToAdmin_rejectsWhenCallerNotFound() {
        when(repo.findByEmail(ADMIN_EMAIL)).thenReturn(Optional.empty());

        assertThrows(ForbiddenException.class,
                () -> userService.promoteToAdmin(UUID.randomUUID(), ADMIN_EMAIL));
        verify(repo, never()).save(any());
    }

    @Test
    void promoteToAdmin_throwsNotFound_whenTargetMissing() {
        User caller = superAdmin();
        UUID missingId = UUID.randomUUID();
        when(repo.findByEmail(ADMIN_EMAIL)).thenReturn(Optional.of(caller));
        when(repo.findById(missingId)).thenReturn(Optional.empty());

        assertThrows(NotFoundException.class, () -> userService.promoteToAdmin(missingId, ADMIN_EMAIL));
    }

    // ---------- getById (IDOR / redaction) ----------

    @Test
    void getById_returnsFullDetails_whenViewingOwnProfile() {
        User seller = User.builder().id(UUID.randomUUID()).fullName("Jane Seller").email("jane@smartre.co.ke").phone("254712345678").role(Role.SELLER).build();
        when(repo.findById(seller.getId())).thenReturn(Optional.of(seller));
        when(repo.findByEmail("jane@smartre.co.ke")).thenReturn(Optional.of(seller));

        var res = userService.getById(seller.getId(), "jane@smartre.co.ke");

        assertEquals("254712345678", res.getPhone());
        assertEquals("jane@smartre.co.ke", res.getEmail());
    }

    @Test
    void getById_redactsPhone_whenCallerHasNotPaid() {
        User seller = User.builder().id(UUID.randomUUID()).fullName("Jane Seller").email("jane@smartre.co.ke").phone("254712345678").kraPin("A123456789Z").role(Role.SELLER).build();
        User buyer = User.builder().id(UUID.randomUUID()).fullName("Bob Buyer").email("bob@smartre.co.ke").role(Role.BUYER).build();
        when(repo.findById(seller.getId())).thenReturn(Optional.of(seller));
        when(repo.findByEmail("bob@smartre.co.ke")).thenReturn(Optional.of(buyer));
        when(redis.hasKey("profile:access:" + buyer.getId() + ":" + seller.getId())).thenReturn(false);

        var res = userService.getById(seller.getId(), "bob@smartre.co.ke");

        assertNull(res.getPhone());
        assertNull(res.getEmail());
        assertNull(res.getKraPin());
    }

    @Test
    void getById_neverReturnsPayoutDetails_evenAfterPayment() {
        User seller = User.builder().id(UUID.randomUUID()).fullName("Jane Seller").email("jane@smartre.co.ke").phone("254712345678")
                .kraPin("A123456789Z").bankAccountNumber("0011223344").paybillNumber("522522").role(Role.SELLER).build();
        User buyer = User.builder().id(UUID.randomUUID()).fullName("Bob Buyer").email("bob@smartre.co.ke").role(Role.BUYER).build();
        when(repo.findById(seller.getId())).thenReturn(Optional.of(seller));
        when(repo.findByEmail("bob@smartre.co.ke")).thenReturn(Optional.of(buyer));
        when(redis.hasKey("profile:access:" + buyer.getId() + ":" + seller.getId())).thenReturn(true);

        var res = userService.getById(seller.getId(), "bob@smartre.co.ke");

        assertEquals("254712345678", res.getPhone());
        assertNull(res.getKraPin());
        assertNull(res.getBankAccountNumber());
        assertNull(res.getPaybillNumber());
    }

    @Test
    void getById_returnsFullDetails_whenCallerHasPaid() {
        User seller = User.builder().id(UUID.randomUUID()).fullName("Jane Seller").email("jane@smartre.co.ke").phone("254712345678").role(Role.SELLER).build();
        User buyer = User.builder().id(UUID.randomUUID()).fullName("Bob Buyer").email("bob@smartre.co.ke").role(Role.BUYER).build();
        when(repo.findById(seller.getId())).thenReturn(Optional.of(seller));
        when(repo.findByEmail("bob@smartre.co.ke")).thenReturn(Optional.of(buyer));
        when(redis.hasKey("profile:access:" + buyer.getId() + ":" + seller.getId())).thenReturn(true);

        var res = userService.getById(seller.getId(), "bob@smartre.co.ke");

        assertEquals("254712345678", res.getPhone());
        assertEquals("jane@smartre.co.ke", res.getEmail());
    }

    @Test
    void getById_redactsPhone_whenNoCallerContext() {
        User seller = User.builder().id(UUID.randomUUID()).fullName("Jane Seller").email("jane@smartre.co.ke").phone("254712345678").role(Role.SELLER).build();
        when(repo.findById(seller.getId())).thenReturn(Optional.of(seller));

        var res = userService.getById(seller.getId(), null);

        assertNull(res.getPhone());
        assertNull(res.getEmail());
    }

    @Test
    void getById_throwsNotFound_whenUserMissing() {
        UUID missingId = UUID.randomUUID();
        when(repo.findById(missingId)).thenReturn(Optional.empty());

        assertThrows(NotFoundException.class, () -> userService.getById(missingId, null));
    }

    // ---------- changePassword / resetPassword: session invalidation (item #1) ----------

    @Test
    void changePassword_succeedsWithCorrectCurrentPassword_andInvalidatesExistingTokens() {
        User u = buildUser(Role.BUYER);
        when(repo.findByEmail("test@smartre.co.ke")).thenReturn(Optional.of(u));
        when(encoder.matches("OldPass1!", u.getPassword())).thenReturn(true);
        when(encoder.encode("NewPass1!")).thenReturn("new-hashed");

        var req = new com.kenyarealestate.user.dto.ChangePasswordRequest();
        req.setCurrentPassword("OldPass1!");
        req.setNewPassword("NewPass1!");

        userService.changePassword("test@smartre.co.ke", req);

        assertEquals("new-hashed", u.getPassword());
        verify(repo).save(u);
        verify(tokenBlacklistService).invalidateTokensBefore(u.getId());
    }

    @Test
    void changePassword_rejectsWrongCurrentPassword() {
        User u = buildUser(Role.BUYER);
        when(repo.findByEmail("test@smartre.co.ke")).thenReturn(Optional.of(u));
        when(encoder.matches("WrongPass", u.getPassword())).thenReturn(false);

        var req = new com.kenyarealestate.user.dto.ChangePasswordRequest();
        req.setCurrentPassword("WrongPass");
        req.setNewPassword("NewPass1!");

        UnauthorizedException ex = assertThrows(UnauthorizedException.class, () -> userService.changePassword("test@smartre.co.ke", req));

        assertEquals("Current password is incorrect", ex.getMessage());
        verify(repo, never()).save(any());
        verify(tokenBlacklistService, never()).invalidateTokensBefore(any());
    }

    @Test
    void resetPassword_invalidatesExistingTokens_onSuccess() {
        User u = buildUser(Role.BUYER);
        u.setResetTokenHash(sha256Hex("raw-token"));
        u.setResetTokenExpiry(java.time.LocalDateTime.now().plusMinutes(10));
        when(repo.findByResetTokenHash(sha256Hex("raw-token"))).thenReturn(Optional.of(u));
        when(encoder.encode("NewPass1!")).thenReturn("new-hashed");

        userService.resetPassword("raw-token", "NewPass1!");

        assertEquals("new-hashed", u.getPassword());
        assertNull(u.getResetTokenHash());
        verify(tokenBlacklistService).invalidateTokensBefore(u.getId());
    }

    @Test
    void resetPassword_rejectsExpiredToken() {
        User u = buildUser(Role.BUYER);
        u.setResetTokenHash(sha256Hex("raw-token"));
        u.setResetTokenExpiry(java.time.LocalDateTime.now().minusMinutes(1));
        when(repo.findByResetTokenHash(sha256Hex("raw-token"))).thenReturn(Optional.of(u));

        assertThrows(BadRequestException.class, () -> userService.resetPassword("raw-token", "NewPass1!"));
        verify(tokenBlacklistService, never()).invalidateTokensBefore(any());
    }

    @Test
    void resetPassword_rejectsUnknownToken() {
        when(repo.findByResetTokenHash(anyString())).thenReturn(Optional.empty());

        assertThrows(BadRequestException.class, () -> userService.resetPassword("bogus-token", "NewPass1!"));
    }

    private static String sha256Hex(String raw) {
        try {
            var digest = java.security.MessageDigest.getInstance("SHA-256");
            return java.util.HexFormat.of().formatHex(digest.digest(raw.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    // ---------- ban / unban (transactional side-effect ordering + audit) ----------

    @Test
    void ban_deactivatesUser_andRunsSideEffectsImmediately_whenNoTransactionActive() {
        User u = buildUser(Role.SELLER);
        u.setActive(true);
        User admin = ordinaryAdmin();
        when(repo.findByEmail(ADMIN_EMAIL)).thenReturn(Optional.of(admin));
        when(repo.findById(u.getId())).thenReturn(Optional.of(u));

        userService.ban(u.getId(), ADMIN_EMAIL);

        assertFalse(u.isActive());
        verify(repo).save(u);
        // Outside of an active Spring transaction (as in this unit test), the side effects run
        // synchronously rather than being silently dropped.
        verify(valueOperations).set("user:banned:" + u.getId(), "true");
        verify(propertyServiceClient).suspendAllListingsForSeller(u.getId(), "Seller account banned by admin");
        verify(auditService).log(u.getId(), "BANNED", admin.getId(), "ADMIN", "ACTIVE", "INACTIVE", null);
    }

    @Test
    void ban_rejectsBanningAnAdmin() {
        User admin = buildUser(Role.ADMIN);
        admin.setActive(true);
        when(repo.findByEmail(ADMIN_EMAIL)).thenReturn(Optional.empty());
        when(repo.findById(admin.getId())).thenReturn(Optional.of(admin));

        ForbiddenException ex = assertThrows(ForbiddenException.class, () -> userService.ban(admin.getId(), ADMIN_EMAIL));

        assertEquals("Cannot ban an admin account", ex.getMessage());
        assertTrue(admin.isActive());
        verify(repo, never()).save(any());
        verifyNoInteractions(propertyServiceClient);
    }

    @Test
    void ban_rejectsBanningSuperAdmin() {
        User superAdmin = superAdmin();
        superAdmin.setActive(true);
        when(repo.findByEmail(ADMIN_EMAIL)).thenReturn(Optional.empty());
        when(repo.findById(superAdmin.getId())).thenReturn(Optional.of(superAdmin));

        assertThrows(ForbiddenException.class, () -> userService.ban(superAdmin.getId(), ADMIN_EMAIL));
        verify(repo, never()).save(any());
    }

    @Test
    void ban_throwsWhenUserNotFound() {
        UUID missingId = UUID.randomUUID();
        when(repo.findByEmail(ADMIN_EMAIL)).thenReturn(Optional.empty());
        when(repo.findById(missingId)).thenReturn(Optional.empty());

        assertThrows(NotFoundException.class, () -> userService.ban(missingId, ADMIN_EMAIL));
        verify(repo, never()).save(any());
        verifyNoInteractions(propertyServiceClient);
    }

    @Test
    void unban_reactivatesUser_andClearsRedisFlag() {
        User u = buildUser(Role.SELLER);
        u.setActive(false);
        User admin = ordinaryAdmin();
        when(repo.findByEmail(ADMIN_EMAIL)).thenReturn(Optional.of(admin));
        when(repo.findById(u.getId())).thenReturn(Optional.of(u));

        userService.unban(u.getId(), ADMIN_EMAIL);

        assertTrue(u.isActive());
        verify(repo).save(u);
        verify(redis).delete("user:banned:" + u.getId());
        verify(auditService).log(u.getId(), "UNBANNED", admin.getId(), "ADMIN", "INACTIVE", "ACTIVE", null);
    }

    @Test
    void unban_throwsWhenUserNotFound() {
        UUID missingId = UUID.randomUUID();
        when(repo.findByEmail(ADMIN_EMAIL)).thenReturn(Optional.empty());
        when(repo.findById(missingId)).thenReturn(Optional.empty());

        assertThrows(NotFoundException.class, () -> userService.unban(missingId, ADMIN_EMAIL));
        verify(repo, never()).save(any());
    }
}
