package com.yem.hlm.backend.auth.service;

import com.yem.hlm.backend.auth.config.JwtProperties;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.UUID;

/**
 * Composant métier responsable de :
 * - générer un JWT
 * - valider un JWT
 * - extraire les claims utiles (userId, tenantId)
 *
 * ⚠️ Il ne fait PAS :
 * - de HTTP
 * - de filtre
 * - de SecurityContext
 */
@Component
public class JwtProvider {

    // Composant Spring Security pour signer les tokens
    private final JwtEncoder encoder;

    // Composant Spring Security pour valider / lire les tokens
    private final JwtDecoder decoder;

    // Durée de validité du token (en secondes)
    private final long ttlSeconds;

    /**
     * Injection par constructeur.
     *
     * JwtProperties contient les valeurs venant du application.yml
     */
    public JwtProvider(
            JwtEncoder encoder,
            JwtDecoder decoder,
            JwtProperties props
    ) {
        this.encoder = encoder;
        this.decoder = decoder;
        this.ttlSeconds = props.ttlSeconds();
    }

    /**
     * Génère un JWT signé.
     *
     * @param userId   identifiant de l'utilisateur (subject)
     * @param tenantId identifiant du tenant (claim custom)
     * @return JWT signé sous forme de String
     */
    public String generate(UUID userId, UUID tenantId) {

        // Instant actuel (iat)
        Instant now = Instant.now();

        // Date d'expiration du token
        Instant expiresAt = now.plusSeconds(ttlSeconds);

        // Construction des claims JWT
        var claims = JwtClaimsSet.builder()

                // Date de création du token
                .issuedAt(now)

                // Date d'expiration
                .expiresAt(expiresAt)

                // Subject du token (standard JWT)
                // → ici : userId
                .subject(userId.toString())

                // Claim custom pour le tenant
                // → essentiel pour le multi-tenant
                .claim("tid", tenantId.toString())

                .build();

        var headers = JwsHeader.with(MacAlgorithm.HS256).build();

        // Signature + encodage du token
        return encoder
                .encode(JwtEncoderParameters.from(headers, claims))
                .getTokenValue();
    }

    /**
     * Vérifie si un token est valide.
     *
     * - signature correcte
     * - non expiré
     * - structure valide
     */
    public boolean isValid(String token) {
        try {
            decoder.decode(token); // lève une exception si invalide
            return true;
        } catch (JwtException ex) {
            return false;
        }
    }

    /**
     * Extrait l'identifiant utilisateur depuis le token.
     *
     * @param token JWT
     * @return UUID du user
     */
    public UUID extractUserId(String token) {

        // Décodage du token (validation incluse)
        Jwt jwt = decoder.decode(token);

        // Le subject contient le userId
        return UUID.fromString(jwt.getSubject());
    }

    /**
     * Extrait l'identifiant tenant depuis le token.
     *
     * @param token JWT
     * @return UUID du tenant
     */
    public UUID extractTenantId(String token) {

        Jwt jwt = decoder.decode(token);

        String tid = jwt.getClaimAsString("tid");
        if (tid == null || tid.isBlank()) {
            throw new JwtException("Missing required claim: tid");
        }
        return UUID.fromString(tid);
    }
}
