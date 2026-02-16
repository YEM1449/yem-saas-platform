package com.yem.hlm.backend.auth.service;

/**
 * Lightweight projection of user security metadata for JWT validation.
 * Cached in Caffeine to avoid DB hits on every request.
 */
public record UserSecurityInfo(
        boolean enabled,
        int tokenVersion
) {}
