package com.yem.hlm.backend.user.api.dto;

import jakarta.validation.constraints.Size;

/**
 * Request body for {@code PATCH /api/me}.
 * All fields are optional (null = no change).
 */
public record UpdateProfileRequest(

        @Size(max = 100)
        String prenom,

        @Size(max = 100)
        String nomFamille,

        @Size(max = 30)
        String telephone,

        @Size(max = 150)
        String poste,

        @Size(max = 10)
        String langueInterface
) {}
