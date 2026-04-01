package com.yem.hlm.backend.portal.service;

import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Generates and validates portal-scoped JWTs.
 *
 * <p>Portal JWTs differ from regular CRM JWTs:
 * <ul>
 *   <li>{@code sub} = contactId (not userId)</li>
 *   <li>{@code roles} = ["ROLE_PORTAL"]</li>
 *   <li>TTL = 2 h (short, re-auth via a new magic link)</li>
 *   <li>No {@code tv} (tokenVersion) claim — portal tokens are stateless</li>
 * </ul>
 *
 * <p>Uses the same HMAC-SHA256 encoder/decoder beans as the main {@code JwtProvider}
 * to share a single key infrastructure.
 */
@Component
public class PortalJwtProvider {

    static final String ROLE_PORTAL = "ROLE_PORTAL";
    private static final long TTL_SECONDS = 2L * 60 * 60; // 2 h

    private final JwtEncoder encoder;
    private final JwtDecoder decoder;

    public PortalJwtProvider(JwtEncoder encoder, JwtDecoder decoder) {
        this.encoder = encoder;
        this.decoder = decoder;
    }

    /**
     * Generates a signed portal JWT for the given contact + tenant.
     *
     * @param contactId the buyer's contact UUID (becomes JWT subject)
     * @param societeId the société UUID (stored in {@code sid} claim)
     * @return signed JWT string
     */
    public String generate(UUID contactId, UUID societeId) {
        Instant now = Instant.now();

        var claims = JwtClaimsSet.builder()
                .issuedAt(now)
                .expiresAt(now.plusSeconds(TTL_SECONDS))
                .subject(contactId.toString())
                .claim("sid", societeId.toString())
                .claim("roles", List.of(ROLE_PORTAL))
                .build();

        var headers = JwsHeader.with(MacAlgorithm.HS256).build();
        return encoder.encode(JwtEncoderParameters.from(headers, claims)).getTokenValue();
    }

    /** Returns {@code true} if the token signature is valid and not expired. */
    public boolean isValid(String token) {
        try {
            decoder.decode(token);
            return true;
        } catch (JwtException ex) {
            return false;
        }
    }

    public static long ttlSeconds() {
        return TTL_SECONDS;
    }
}
