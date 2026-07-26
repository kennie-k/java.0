package com.kenyarealestate.user.service;

import com.kenyarealestate.user.dto.AuthResponse;
import com.kenyarealestate.user.dto.LoginRequest;
import com.kenyarealestate.user.dto.RegisterRequest;
import com.kenyarealestate.user.entity.Role;
import com.kenyarealestate.user.entity.User;
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

    @InjectMocks private UserService userService;

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

    @BeforeEach
    void setup() {
        when(repo.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));
        when(encoder.encode(anyString())).thenReturn("hashed");
        when(jwtUtil.generateToken(anyString(), anyString(), any(UUID.class))).thenReturn("mock-jwt-token");
    }

    @Test
    void register_bootstrapsFirstAdmin_whenNoneExists() {
        when(repo.existsByEmail(anyString())).thenReturn(false);
        when(repo.existsByRole(Role.ADMIN)).thenReturn(false);

        AuthResponse res = userService.register(registerRequest("ADMIN"));

        assertEquals("ADMIN", res.getRole());
        verify(repo).save(any(User.class));
    }

    @Test
    void register_rejectsAdmin_whenAdminAlreadyExists() {
        when(repo.existsByEmail(anyString())).thenReturn(false);
        when(repo.existsByRole(Role.ADMIN)).thenReturn(true);

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> userService.register(registerRequest("ADMIN")));

        assertTrue(ex.getMessage().contains("Admin registration is closed"));
        verify(repo, never()).save(any());
    }

    @Test
    void register_rejectsUnknownRole() {
        when(repo.existsByEmail(anyString())).thenReturn(false);

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> userService.register(registerRequest("SUPERUSER")));

        assertTrue(ex.getMessage().contains("Role must be BUYER or SELLER"));
        verify(repo, never()).save(any());
    }

    @Test
    void register_succeedsForBuyer() {
        when(repo.existsByEmail(anyString())).thenReturn(false);

        AuthResponse res = userService.register(registerRequest("BUYER"));

        assertEquals("BUYER", res.getRole());
        assertEquals("mock-jwt-token", res.getToken());
    }

    @Test
    void register_rejectsDuplicateEmail() {
        when(repo.existsByEmail(anyString())).thenReturn(true);

        assertThrows(RuntimeException.class, () -> userService.register(registerRequest("BUYER")));
        verify(repo, never()).save(any());
    }

    @Test
    void login_rejectsWhenLockedOut() {
        when(loginAttemptService.isLocked("test@smartre.co.ke")).thenReturn(true);

        LoginRequest req = new LoginRequest();
        req.setEmail("test@smartre.co.ke");
        req.setPassword("Password1!");

        RuntimeException ex = assertThrows(RuntimeException.class, () -> userService.login(req));

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

        assertThrows(RuntimeException.class, () -> userService.login(req));
        verify(loginAttemptService).recordFailure("test@smartre.co.ke");
    }

    @Test
    void login_recordsSuccess_onCorrectPassword() {
        User u = buildUser(Role.BUYER);
        u.setActive(true);
        when(loginAttemptService.isLocked(anyString())).thenReturn(false);
        when(repo.findByEmail(anyString())).thenReturn(Optional.of(u));
        when(encoder.matches(anyString(), anyString())).thenReturn(true);

        LoginRequest req = new LoginRequest();
        req.setEmail("test@smartre.co.ke");
        req.setPassword("Password1!");

        AuthResponse res = userService.login(req);

        assertEquals("mock-jwt-token", res.getToken());
        verify(loginAttemptService).recordSuccess("test@smartre.co.ke");
        verify(loginAttemptService, never()).recordFailure(anyString());
    }

    @Test
    void promoteToAdmin_setsRoleToAdmin() {
        User u = buildUser(Role.SELLER);
        when(repo.findById(u.getId())).thenReturn(Optional.of(u));

        userService.promoteToAdmin(u.getId());

        assertEquals(Role.ADMIN, u.getRole());
        verify(repo).save(u);
    }

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
    void changePassword_succeedsWithCorrectCurrentPassword() {
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
    }

    @Test
    void changePassword_rejectsWrongCurrentPassword() {
        User u = buildUser(Role.BUYER);
        when(repo.findByEmail("test@smartre.co.ke")).thenReturn(Optional.of(u));
        when(encoder.matches("WrongPass", u.getPassword())).thenReturn(false);

        var req = new com.kenyarealestate.user.dto.ChangePasswordRequest();
        req.setCurrentPassword("WrongPass");
        req.setNewPassword("NewPass1!");

        RuntimeException ex = assertThrows(RuntimeException.class, () -> userService.changePassword("test@smartre.co.ke", req));

        assertEquals("Current password is incorrect", ex.getMessage());
        verify(repo, never()).save(any());
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
    void ban_deactivatesUser() {
        User u = buildUser(Role.SELLER);
        u.setActive(true);
        when(repo.findById(u.getId())).thenReturn(Optional.of(u));

        userService.ban(u.getId());

        assertFalse(u.isActive());
        verify(repo).save(u);
    }

    @Test
    void ban_rejectsBanningAnAdmin() {
        User admin = buildUser(Role.ADMIN);
        admin.setActive(true);
        when(repo.findById(admin.getId())).thenReturn(Optional.of(admin));

        RuntimeException ex = assertThrows(RuntimeException.class, () -> userService.ban(admin.getId()));

        assertEquals("Cannot ban an admin account", ex.getMessage());
        assertTrue(admin.isActive());
        verify(repo, never()).save(any());
    }

    @Test
    void ban_throwsWhenUserNotFound() {
        UUID missingId = UUID.randomUUID();
        when(repo.findById(missingId)).thenReturn(Optional.empty());

        assertThrows(RuntimeException.class, () -> userService.ban(missingId));
        verify(repo, never()).save(any());
    }

    @Test
    void unban_reactivatesUser() {
        User u = buildUser(Role.SELLER);
        u.setActive(false);
        when(repo.findById(u.getId())).thenReturn(Optional.of(u));

        userService.unban(u.getId());

        assertTrue(u.isActive());
        verify(repo).save(u);
    }

    @Test
    void unban_throwsWhenUserNotFound() {
        UUID missingId = UUID.randomUUID();
        when(repo.findById(missingId)).thenReturn(Optional.empty());

        assertThrows(RuntimeException.class, () -> userService.unban(missingId));
        verify(repo, never()).save(any());
    }
}
