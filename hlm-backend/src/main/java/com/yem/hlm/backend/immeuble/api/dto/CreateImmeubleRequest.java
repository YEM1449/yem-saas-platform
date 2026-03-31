package com.yem.hlm.backend.immeuble.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record CreateImmeubleRequest(
        @NotNull UUID projectId,
        @NotBlank @Size(max = 255) String nom,
        @Size(max = 2000) String adresse,
        Integer nbEtages,
        @Size(max = 2000) String description
) {}
