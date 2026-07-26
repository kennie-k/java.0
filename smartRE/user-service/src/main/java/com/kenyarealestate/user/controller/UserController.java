package com.kenyarealestate.user.controller;

import com.kenyarealestate.user.dto.*;
import com.kenyarealestate.user.service.UserService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.data.domain.*;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@Validated
@RestController
@RequestMapping("/api/users")
public class UserController {

    private final UserService svc;

    public UserController(UserService svc) { this.svc = svc; }

    @GetMapping("/me")
    public ResponseEntity<UserResponse> getProfile(Authentication auth) {
        return ResponseEntity.ok(svc.getProfile(auth.getName()));
    }

    @PutMapping("/me")
    public ResponseEntity<UserResponse> updateProfile(
            Authentication auth, @Valid @RequestBody UpdateProfileRequest req) {
        return ResponseEntity.ok(svc.updateProfile(auth.getName(), req));
    }

    @PutMapping("/me/password")
    public ResponseEntity<Void> changePassword(
            Authentication auth, @Valid @RequestBody ChangePasswordRequest req) {
        svc.changePassword(auth.getName(), req);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/{id}")
    public ResponseEntity<UserResponse> getById(@PathVariable UUID id, Authentication auth) {
        return ResponseEntity.ok(svc.getById(id, auth != null ? auth.getName() : null));
    }

    @GetMapping("/admin/all")
    public ResponseEntity<Page<UserResponse>> getAll(
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(1000) int size,
            @RequestParam(defaultValue = "createdAt") String sort,
            @RequestParam(defaultValue = "DESC") String direction) {
        Sort.Direction dir = direction.equalsIgnoreCase("ASC") ? Sort.Direction.ASC : Sort.Direction.DESC;
        return ResponseEntity.ok(svc.getAll(PageRequest.of(page, size, Sort.by(dir, sort))));
    }

    @PutMapping("/admin/{id}/promote")
    public ResponseEntity<UserResponse> promoteToAdmin(@PathVariable UUID id) {
        return ResponseEntity.ok(svc.promoteToAdmin(id));
    }

    @PutMapping("/admin/{id}/ban")
    public ResponseEntity<UserResponse> ban(@PathVariable UUID id) {
        return ResponseEntity.ok(svc.ban(id));
    }

    @PutMapping("/admin/{id}/unban")
    public ResponseEntity<UserResponse> unban(@PathVariable UUID id) {
        return ResponseEntity.ok(svc.unban(id));
    }
}
