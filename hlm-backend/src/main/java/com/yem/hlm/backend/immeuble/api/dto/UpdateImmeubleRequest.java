package com.yem.hlm.backend.immeuble.api.dto;

import jakarta.validation.constraints.Size;

public record UpdateImmeubleRequest(
        @Size(max = 255) String nom,
        @Size(max = 2000) String adresse,
        Integer nbEtages,
        @Size(max = 2000) String description
) {}
