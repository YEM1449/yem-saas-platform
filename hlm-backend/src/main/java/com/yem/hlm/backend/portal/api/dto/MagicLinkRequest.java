package com.yem.hlm.backend.portal.api.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

/**
 * Request body for POST /api/portal/auth/request-link.
 *
 * @param email     buyer's email address
 * @param tenantKey the tenant's key (e.g. "acme")
 */
public record MagicLinkRequest(
        @NotBlank @Email String email,
        @NotBlank String tenantKey
) {}
