package com.yem.hlm.backend.auth.service;

import com.yem.hlm.backend.auth.config.JwtProperties;
import com.yem.hlm.backend.user.domain.UserRole;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
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
     * Génère un JWT signé (backward compatible version without role).
     * Defaults to ROLE_AGENT if no role is provided.
     *
     * @param userId   identifiant de l'utilisateur (subject)
     * @param tenantId identifiant du tenant (claim custom)
     * @return JWT signé sous forme de String
     */
    public String generate(UUID userId, UUID tenantId) {
        return generate(userId, tenantId, UserRole.ROLE_AGENT, 0);
    }

    public String generate(UUID userId, UUID tenantId, UserRole role) {
        return generate(userId, tenantId, role, 0);
    }

    public String generate(UUID userId, UUID tenantId, UserRole role, int tokenVersion) {
        Instant now = Instant.now();
        Instant expiresAt = now.plusSeconds(ttlSeconds);

        var claims = JwtClaimsSet.builder()
                .issuedAt(now)
                .expiresAt(expiresAt)
                .subject(userId.toString())
                .claim("tid", tenantId.toString())
                .claim("roles", List.of(role.name()))
                .claim("tv", tokenVersion)
                .build();

        var headers = JwsHeader.with(MacAlgorithm.HS256).build();

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

    /**
     * Extrait les rôles depuis le token pour RBAC.
     * Retourne une liste vide si aucun rôle n'est présent (backward compatibility).
     *
     * @param token JWT
     * @return Liste des rôles (ex: ["ROLE_ADMIN"])
     */
    public List<String> extractRoles(String token) {

        Jwt jwt = decoder.decode(token);

        // Le claim "roles" est une liste de strings
        List<String> roles = jwt.getClaimAsStringList("roles");

        // Backward compatibility: si pas de claim roles, retourner ROLE_AGENT par défaut
        if (roles == null || roles.isEmpty()) {
            return List.of(UserRole.ROLE_AGENT.name());
        }

        return roles;
    }

    /**
     * Extract tokenVersion from JWT. Returns 0 if claim is missing (backward compat).
     */
    public int extractTokenVersion(String token) {
        Jwt jwt = decoder.decode(token);
        Object tv = jwt.getClaim("tv");
        if (tv instanceof Number n) {
            return n.intValue();
        }
        return 0;
    }
}
