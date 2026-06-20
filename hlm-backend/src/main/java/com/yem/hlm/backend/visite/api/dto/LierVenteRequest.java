package com.yem.hlm.backend.visite.api.dto;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

/** Request body for {@code POST /api/visites/{id}/lier-vente} (RG-V06 / P5-T2). */
public record LierVenteRequest(
        @NotNull UUID venteId
) {}
