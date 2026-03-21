package com.yem.hlm.backend.usermanagement.dto;

import jakarta.validation.constraints.*;

public record ChangerRoleRequest(
    @NotNull @Pattern(regexp = "ADMIN|MANAGER|AGENT") String nouveauRole,
    @Size(max = 500) String raison,
    @NotNull Long version
) {}
