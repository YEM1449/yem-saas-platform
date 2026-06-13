package com.yem.hlm.backend.user.api.dto;

import java.util.UUID;

/**
 * Result of a cross-société off-boarding ({@code POST /api/users/{id}/deactivate-everywhere}).
 *
 * @param userId              the off-boarded user
 * @param email               their email (for the confirmation toast)
 * @param societesDesactivees number of active memberships that were deactivated
 * @param compteDesactive     whether the global account was disabled (always true on success)
 */
public record DeactivateEverywhereResponse(
        UUID    userId,
        String  email,
        int     societesDesactivees,
        boolean compteDesactive
) {}
