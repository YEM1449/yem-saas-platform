package com.yem.hlm.backend.visite.api.dto;

import jakarta.validation.constraints.NotBlank;

/** Request body for {@code POST /api/visites/{id}/annuler} (RG-V08). */
public record AnnulerVisiteRequest(
        @NotBlank String raison
) {}
