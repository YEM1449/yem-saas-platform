package com.yem.hlm.backend.vente.api.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

/** Request body for {@code POST /api/ventes/option} — places a temporary hold (VEFA OPTION). */
public record CreateOptionRequest(
        @NotNull UUID propertyId,
        @NotNull UUID contactId,
        /** Hold duration in hours (1-72, default 48 applied when null/0). */
        @Min(0) @Max(72) int dureeHeures
) {}
