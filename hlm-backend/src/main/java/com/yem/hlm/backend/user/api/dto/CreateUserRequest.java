package com.yem.hlm.backend.user.api.dto;

import com.yem.hlm.backend.common.validation.StrongPassword;
import com.yem.hlm.backend.user.domain.UserRole;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateUserRequest(
        @NotBlank @Email @Size(max = 160)
        String email,
        @NotBlank @StrongPassword @Size(max = 128)
        String password,
        @NotNull
        UserRole role
) {
    public CreateUserRequest {
        if (email != null) email = email.trim().toLowerCase();
    }
}
