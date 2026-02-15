package com.yem.hlm.backend.user.api.dto;

import jakarta.validation.constraints.NotNull;

public record SetEnabledRequest(
        @NotNull
        Boolean enabled
) {}
