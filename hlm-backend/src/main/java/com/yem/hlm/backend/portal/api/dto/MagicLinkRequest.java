package com.yem.hlm.backend.portal.api.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Request body for POST /api/portal/auth/request-link.
 *
 * @param email      buyer's email address
 * @param societeKey the société's key (e.g. "acme")
 */
public record MagicLinkRequest(
        @NotBlank @Email @Size(max = 160) String email,
        @NotBlank @Size(max = 80) String societeKey
) {}
