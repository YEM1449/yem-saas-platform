package com.yem.hlm.backend.user.api.dto;

import com.yem.hlm.backend.user.domain.User;

import java.util.UUID;

public record UserResponse(
        UUID id,
        String email,
        String role,
        boolean enabled
) {
    /** Use when role is known from AppUserSociete context. */
    public static UserResponse from(User u, String role) {
        return new UserResponse(u.getId(), u.getEmail(), role, u.isEnabled());
    }

    /** Fallback when role is not available (e.g. SUPER_ADMIN). */
    public static UserResponse from(User u) {
        return new UserResponse(u.getId(), u.getEmail(), u.getPlatformRole(), u.isEnabled());
    }
}
