package com.yem.hlm.backend.auth.api.dto;

import jakarta.validation.constraints.NotBlank;

public record LoginRequest(
        @NotBlank String tenantKey,
        @NotBlank String email,
        @NotBlank String password
) {}

