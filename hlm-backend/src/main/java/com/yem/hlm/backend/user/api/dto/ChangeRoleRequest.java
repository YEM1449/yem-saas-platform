package com.yem.hlm.backend.user.api.dto;

import com.yem.hlm.backend.user.domain.UserRole;
import jakarta.validation.constraints.NotNull;

public record ChangeRoleRequest(
        @NotNull
        UserRole role
) {}
