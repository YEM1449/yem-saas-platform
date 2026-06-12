package com.yem.hlm.backend.user.api.dto;

import jakarta.validation.constraints.Size;

/**
 * Body for {@code POST /api/users/{id}/deactivate-everywhere}.
 *
 * <p>{@code raison} is the off-boarding reason recorded on every membership
 * ({@code raison_retrait}) and in the security audit log — optional but recommended.
 */
public record DeactivateEverywhereRequest(
        @Size(max = 100)
        String raison
) {}
