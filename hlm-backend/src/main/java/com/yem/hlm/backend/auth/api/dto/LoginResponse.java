package com.yem.hlm.backend.auth.api.dto;

import java.util.List;

public record LoginResponse(
        String accessToken,
        String tokenType,
        long expiresIn,
        boolean requiresSocieteSelection,
        List<SocieteDto> societes
) {
    /** Convenience factory for a fully-authenticated (single-société) response. */
    public static LoginResponse bearer(String token, long expiresIn) {
        return new LoginResponse(token, "Bearer", expiresIn, false, null);
    }

    /**
     * Factory for a société-selection response.
     * Includes a short-lived partial token so the client can call
     * POST /auth/switch-societe without a full session token.
     */
    public static LoginResponse selectSociete(String partialToken, List<SocieteDto> societes) {
        return new LoginResponse(partialToken, "Partial", 300, true, societes);
    }
}
