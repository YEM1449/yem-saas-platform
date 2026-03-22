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
 * - extraire les claims utiles (userId, societeId)
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
     * @param societeId identifiant de la société (claim custom)
     * @return JWT signé sous forme de String
     */
    public String generate(UUID userId, UUID societeId) {
        return generate(userId, societeId, UserRole.ROLE_AGENT, 0);
    }

    public String generate(UUID userId, UUID societeId, UserRole role) {
        return generate(userId, societeId, role, 0);
    }

    public String generate(UUID userId, UUID societeId, UserRole role, int tokenVersion) {
        Instant now = Instant.now();
        var builder = JwtClaimsSet.builder()
                .issuedAt(now)
                .expiresAt(now.plusSeconds(ttlSeconds))
                .subject(userId.toString())
                .claim("roles", List.of(role.name()))
                .claim("tv", tokenVersion);
        if (societeId != null) builder.claim("sid", societeId.toString());

        var headers = JwsHeader.with(MacAlgorithm.HS256).build();
        return encoder.encode(JwtEncoderParameters.from(headers, builder.build())).getTokenValue();
    }

    /**
     * Génère un JWT signé avec un rôle de type String (pour les rôles de société).
     *
     * @param userId       identifiant de l'utilisateur (subject)
     * @param societeId    identifiant de la société (claim "sid")
     * @param role         rôle sous forme de String (ex: "ROLE_ADMIN")
     * @param tokenVersion version du token (pour la révocation)
     * @return JWT signé sous forme de String
     */
    public String generate(UUID userId, UUID societeId, String role, int tokenVersion) {
        Instant now = Instant.now();
        var builder = JwtClaimsSet.builder()
                .issuedAt(now)
                .expiresAt(now.plusSeconds(ttlSeconds))
                .subject(userId.toString())
                .claim("roles", List.of(role))
                .claim("tv", tokenVersion);
        if (societeId != null) builder.claim("sid", societeId.toString());
        var headers = JwsHeader.with(MacAlgorithm.HS256).build();
        return encoder.encode(JwtEncoderParameters.from(headers, builder.build())).getTokenValue();
    }

    /**
     * Generates a short-lived partial token for multi-société selection.
     * The token has no "sid" claim and carries a "partial=true" marker so
     * JwtAuthenticationFilter can refuse it for any route except /auth/switch-societe.
     *
     * @param userId     the authenticated user
     * @param tokenVersion the user's current tokenVersion for revocation checks
     * @param ttlSeconds lifetime of the token in seconds (typically 300 = 5 min)
     * @return signed partial JWT
     */
    public String generatePartial(UUID userId, int tokenVersion, int ttlSeconds) {
        Instant now = Instant.now();
        var claims = JwtClaimsSet.builder()
                .issuedAt(now)
                .expiresAt(now.plusSeconds(ttlSeconds))
                .subject(userId.toString())
                .claim("partial", true)
                .claim("tv", tokenVersion)
                .build();
        var headers = JwsHeader.with(MacAlgorithm.HS256).build();
        return encoder.encode(JwtEncoderParameters.from(headers, claims)).getTokenValue();
    }

    public String generatePartial(UUID userId, int ttlSeconds) {
        return generatePartial(userId, 0, ttlSeconds);
    }

    /**
     * Generates a short-lived impersonation token.
     * The token has the target user's subject and société, but carries an {@code imp} claim
     * identifying the SUPER_ADMIN who initiated impersonation.
     *
     * @param targetUserId   the user being impersonated
     * @param targetSocieteId the société scope
     * @param targetRole     the role of the impersonated user in that société (e.g. "ROLE_ADMIN")
     * @param tokenVersion   the target user's current tokenVersion
     * @param superAdminId   the SUPER_ADMIN performing impersonation (stored in {@code imp} claim)
     * @param ttlSeconds     lifetime of the impersonation token (typically 3600 = 1 hour)
     */
    public String generateImpersonation(UUID targetUserId, UUID targetSocieteId,
                                        String targetRole, int tokenVersion,
                                        UUID superAdminId, int ttlSeconds) {
        Instant now = Instant.now();
        var builder = JwtClaimsSet.builder()
                .issuedAt(now)
                .expiresAt(now.plusSeconds(ttlSeconds))
                .subject(targetUserId.toString())
                .claim("roles", List.of(targetRole))
                .claim("tv",    tokenVersion)
                .claim("imp",   superAdminId.toString());
        if (targetSocieteId != null) builder.claim("sid", targetSocieteId.toString());
        var headers = JwsHeader.with(MacAlgorithm.HS256).build();
        return encoder.encode(JwtEncoderParameters.from(headers, builder.build())).getTokenValue();
    }

    /**
     * Extracts the {@code imp} claim (impersonating SUPER_ADMIN userId).
     * Returns {@code null} when the claim is absent (normal non-impersonation token).
     */
    public UUID extractImpersonatedBy(String token) {
        try {
            Jwt jwt = decoder.decode(token);
            String imp = jwt.getClaimAsString("imp");
            return imp != null ? UUID.fromString(imp) : null;
        } catch (RuntimeException ex) {
            return null;
        }
    }

    /**
     * Decodes and validates a token, returning the parsed {@link Jwt}.
     * Throws {@link JwtException} if the token is null, invalid, or expired.
     */
    public Jwt parse(String token) {
        if (token == null || token.isBlank()) {
            throw new JwtException("Token must not be null or blank");
        }
        return decoder.decode(token);
    }

    /**
     * Returns true when the token carries the "partial=true" claim.
     * Partial tokens are only valid for /auth/switch-societe.
     */
    public boolean isPartialToken(String token) {
        try {
            Jwt jwt = decoder.decode(token);
            return Boolean.TRUE.equals(jwt.getClaim("partial"));
        } catch (JwtException e) {
            return false;
        }
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
     * Extrait l'identifiant société depuis le token (claim "sid").
     *
     * @param token JWT
     * @return UUID de la société
     */
    public UUID extractSocieteId(String token) {

        Jwt jwt = decoder.decode(token);

        String sid = jwt.getClaimAsString("sid");
        if (sid == null || sid.isBlank()) {
            throw new JwtException("Missing required claim: sid");
        }
        return UUID.fromString(sid);
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
