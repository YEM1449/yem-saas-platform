package com.yem.hlm.backend.visite.api.dto;

import com.yem.hlm.backend.visite.domain.ResultatVisite;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * Request body for {@code POST /api/visites/{id}/compte-rendu} (RG-V06).
 * Recording a compte-rendu transitions the visite to REALISEE.
 */
public record CompteRenduRequest(
        @NotBlank String compteRendu,
        @NotNull ResultatVisite resultat
) {}
