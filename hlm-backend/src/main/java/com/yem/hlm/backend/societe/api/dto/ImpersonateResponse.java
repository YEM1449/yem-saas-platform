package com.yem.hlm.backend.societe.api.dto;

import java.util.UUID;

/**
 * Returned by {@code POST /api/admin/societes/{id}/impersonate/{userId}}.
 * The caller uses {@code token} as a regular Bearer token; it carries the
 * target user's identity but has a short TTL and an {@code imp} claim.
 */
public record ImpersonateResponse(
        String token,
        UUID   targetUserId,
        UUID   targetSocieteId,
        String targetUserEmail,
        String targetRole,
        int    ttlSeconds
) {}
