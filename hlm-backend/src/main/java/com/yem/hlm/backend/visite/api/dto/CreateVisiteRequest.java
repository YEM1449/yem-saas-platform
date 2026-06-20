package com.yem.hlm.backend.visite.api.dto;

import com.yem.hlm.backend.visite.domain.TypeVisite;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.time.Instant;
import java.util.UUID;

/**
 * Request body for {@code POST /api/visites} (RG-V01).
 *
 * <p>{@code dateHeure} is an instant (ISO-8601, UTC on the wire); the agent enters it in
 * Africa/Casablanca and the frontend converts. {@code property} and {@code project} are
 * optional (a visite can be a simple discovery meeting). {@code agentId} defaults to the
 * current user; only MANAGER/ADMIN may book on behalf of another agent. {@code override}
 * lets a MANAGER force-book over a conflicting slot (RG-V05).
 */
public record CreateVisiteRequest(
        @NotNull UUID contactId,
        UUID propertyId,
        UUID projectId,
        UUID agentId,
        @NotNull Instant dateHeure,
        @Positive Integer dureeMinutes,
        @NotNull TypeVisite type,
        String lieu,
        boolean override
) {}
