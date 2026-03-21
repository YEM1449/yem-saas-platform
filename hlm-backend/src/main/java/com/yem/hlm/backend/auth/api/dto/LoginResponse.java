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

    /** Factory for a partial response when the user belongs to multiple sociétés. */
    public static LoginResponse selectSociete(List<SocieteDto> societes) {
        return new LoginResponse(null, null, 0, true, societes);
    }
}
