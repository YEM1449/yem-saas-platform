package com.yem.hlm.backend.portal.api.dto;

/**
 * Lightweight tenant branding info for the portal shell.
 *
 * @param tenantName tenant display name
 * @param logoUrl    optional logo URL (null if not configured)
 */
public record PortalTenantInfoResponse(String tenantName, String logoUrl) {}
