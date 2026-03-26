package com.yem.hlm.backend.user.api;

import com.yem.hlm.backend.user.api.dto.*;
import com.yem.hlm.backend.user.service.AdminUserService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * REST API for société-scoped user administration.
 *
 * <p>Base path: {@code /api/users} — accessible only to {@code ROLE_ADMIN}.
 *
 * <p>Note: this controller is intentionally <em>not</em> under {@code /api/admin/}
 * because that prefix is reserved for SUPER_ADMIN in {@code SecurityConfig}.
 * All operations are automatically scoped to the caller's société via
 * {@code SocieteContext} (populated by {@code JwtAuthenticationFilter}).
 */
@Tag(name = "Users", description = "Société-scoped user administration")
@RestController
@RequestMapping("/api/users")
@PreAuthorize("hasRole('ADMIN')")
public class AdminUserController {

    private final AdminUserService adminUserService;

    public AdminUserController(AdminUserService adminUserService) {
        this.adminUserService = adminUserService;
    }

    /** Lists all users in the current société, optionally filtered by {@code q} (email/name search). */
    @GetMapping
    public List<UserResponse> list(@RequestParam(required = false) String q) {
        return adminUserService.list(q);
    }

    /** Creates a new user in the current société. Returns 201 with the created user. */
    @PostMapping
    public ResponseEntity<UserResponse> create(@Valid @RequestBody CreateUserRequest request) {
        UserResponse response = adminUserService.create(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /** Changes the société-level role of an existing user. */
    @PatchMapping("/{id}/role")
    public UserResponse changeRole(@PathVariable UUID id,
                                   @Valid @RequestBody ChangeRoleRequest request) {
        return adminUserService.changeRole(id, request);
    }

    /** Enables or disables a user account within the current société. */
    @PatchMapping("/{id}/enabled")
    public UserResponse setEnabled(@PathVariable UUID id,
                                   @Valid @RequestBody SetEnabledRequest request) {
        return adminUserService.setEnabled(id, request);
    }

    /** Generates a temporary password for the user and returns it in the response. */
    @PostMapping("/{id}/reset-password")
    public ResetPasswordResponse resetPassword(@PathVariable UUID id) {
        return adminUserService.resetPassword(id);
    }
}
