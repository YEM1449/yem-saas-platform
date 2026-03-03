package com.yem.hlm.backend.portal.service;

/**
 * Thrown when a portal magic link token is invalid, expired, or already used.
 * Maps to HTTP 401 Unauthorized.
 */
public class PortalTokenInvalidException extends RuntimeException {
    public PortalTokenInvalidException(String message) {
        super(message);
    }
}
