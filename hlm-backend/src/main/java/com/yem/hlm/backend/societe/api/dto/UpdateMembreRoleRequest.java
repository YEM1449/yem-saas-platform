package com.yem.hlm.backend.societe.api.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record UpdateMembreRoleRequest(
        @NotNull
        @Pattern(regexp = "^(ADMIN|MANAGER|AGENT)$",
                 message = "nouveauRole must be ADMIN, MANAGER, or AGENT")
        String nouveauRole,

        @Size(max = 500) String raison
) {}
