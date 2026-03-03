package com.yem.hlm.backend.portal.api.dto;

/**
 * Response for GET /api/portal/auth/verify.
 *
 * @param accessToken portal-scoped JWT (ROLE_PORTAL, 2 h TTL)
 */
public record PortalTokenVerifyResponse(String accessToken) {}
