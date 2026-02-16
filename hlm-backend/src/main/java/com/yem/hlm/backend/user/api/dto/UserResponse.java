package com.yem.hlm.backend.user.api.dto;

import com.yem.hlm.backend.user.domain.User;
import com.yem.hlm.backend.user.domain.UserRole;

import java.util.UUID;

public record UserResponse(
        UUID id,
        String email,
        UserRole role,
        boolean enabled
) {
    public static UserResponse from(User u) {
        return new UserResponse(u.getId(), u.getEmail(), u.getRole(), u.isEnabled());
    }
}
