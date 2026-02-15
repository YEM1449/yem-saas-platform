package com.yem.hlm.backend.user.api;

import com.yem.hlm.backend.user.api.dto.*;
import com.yem.hlm.backend.user.service.AdminUserService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/admin/users")
@PreAuthorize("hasRole('ADMIN')")
public class AdminUserController {

    private final AdminUserService adminUserService;

    public AdminUserController(AdminUserService adminUserService) {
        this.adminUserService = adminUserService;
    }

    @GetMapping
    public List<UserResponse> list(@RequestParam(required = false) String q) {
        return adminUserService.list(q);
    }

    @PostMapping
    public ResponseEntity<UserResponse> create(@Valid @RequestBody CreateUserRequest request) {
        UserResponse response = adminUserService.create(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PatchMapping("/{id}/role")
    public UserResponse changeRole(@PathVariable UUID id,
                                   @Valid @RequestBody ChangeRoleRequest request) {
        return adminUserService.changeRole(id, request);
    }

    @PatchMapping("/{id}/enabled")
    public UserResponse setEnabled(@PathVariable UUID id,
                                   @Valid @RequestBody SetEnabledRequest request) {
        return adminUserService.setEnabled(id, request);
    }

    @PostMapping("/{id}/reset-password")
    public ResetPasswordResponse resetPassword(@PathVariable UUID id) {
        return adminUserService.resetPassword(id);
    }
}
