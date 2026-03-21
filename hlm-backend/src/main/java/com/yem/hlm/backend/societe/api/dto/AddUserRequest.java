package com.yem.hlm.backend.societe.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record AddUserRequest(
        @NotNull UUID userId,
        @NotBlank String role
) {}
