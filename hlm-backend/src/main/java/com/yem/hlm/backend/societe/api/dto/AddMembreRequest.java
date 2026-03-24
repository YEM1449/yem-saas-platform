package com.yem.hlm.backend.societe.api.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import java.util.UUID;

public record AddMembreRequest(
        @NotNull UUID userId,
        @NotNull
        @Pattern(regexp = "^(ADMIN|MANAGER|AGENT)$",
                 message = "role must be ADMIN, MANAGER, or AGENT")
        String role
) {}
