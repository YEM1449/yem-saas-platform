package com.yem.hlm.backend.societe.api.dto;

import java.util.UUID;

/**
 * Returned by {@code POST /api/admin/societes/{id}/impersonate/{userId}}.
 *
 * <p>The {@code token} field is used internally by {@link com.yem.hlm.backend.societe.api.SocieteController}
 * to set an httpOnly cookie; it is stripped from the JSON body sent to the client via
 * {@link #withoutToken()}. JavaScript never sees the impersonation JWT.
 */
public record ImpersonateResponse(
        String token,
        UUID   targetUserId,
        UUID   targetSocieteId,
        String targetUserEmail,
        String targetRole,
        int    ttlSeconds
) {
    /** Returns a copy with the {@code token} field cleared for the HTTP response body. */
    public ImpersonateResponse withoutToken() {
        return new ImpersonateResponse("", targetUserId, targetSocieteId,
                targetUserEmail, targetRole, ttlSeconds);
    }
}
