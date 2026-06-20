package com.yem.hlm.backend.visite.api.dto;

import com.yem.hlm.backend.visite.domain.TypeVisite;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.time.Instant;
import java.util.UUID;

/**
 * Request body for {@code PUT /api/visites/{id}} — reschedule / edit a non-terminal visite.
 * Triggers conflict re-check (RG-V05). {@code override} forces over a conflict (MANAGER).
 */
public record UpdateVisiteRequest(
        UUID propertyId,
        UUID projectId,
        @NotNull Instant dateHeure,
        @Positive Integer dureeMinutes,
        @NotNull TypeVisite type,
        String lieu,
        boolean override
) {}
