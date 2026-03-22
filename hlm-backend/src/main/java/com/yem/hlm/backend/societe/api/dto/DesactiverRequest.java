package com.yem.hlm.backend.societe.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record DesactiverRequest(
        @NotBlank @Size(max = 500) String raison
) {}
