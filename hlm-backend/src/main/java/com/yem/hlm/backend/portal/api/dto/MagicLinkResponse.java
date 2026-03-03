package com.yem.hlm.backend.portal.api.dto;

/**
 * Response for POST /api/portal/auth/request-link.
 *
 * <p>{@code magicLinkUrl} is included so that dev/test environments can
 * verify the flow without an actual email provider. In production the link
 * is delivered only via email; the response value should be ignored by
 * production frontends.
 *
 * @param message      user-facing status message
 * @param magicLinkUrl the full magic link URL (token embedded as query param)
 */
public record MagicLinkResponse(
        String message,
        String magicLinkUrl
) {}
