package com.yem.hlm.backend.support;

import com.yem.hlm.backend.auth.config.JwtProperties;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * TestJwtConfig
 *
 * Objectif:
 * - Fournir des beans JwtEncoder/JwtDecoder *pour les tests* (profile test),
 *   afin de générer et valider des JWT comme en prod, mais avec une clé de test.
 *
 * Pourquoi c'est utile:
 * - Tes IT (MockMvc) ont besoin de produire des tokens valides.
 * - Tes composants (JwtProvider, JwtAuthenticationFilter) ont besoin d'un JwtDecoder
 *   (sinon: "No qualifying bean of type JwtDecoder").
 *
 * NOTE:
 * - On garde le même algorithme que la prod: HS256 (HMAC).
 * - La clé vient de JwtProperties.secret (donc application-test.yml doit la fournir).
 */
@TestConfiguration
public class TestJwtConfig {

    /**
     * Bean principal de JwtEncoder pour tests.
     *
     * On le met @Primary pour éviter les collisions si un autre encoder est présent.
     */
    @Bean
    @Primary
    JwtEncoder jwtEncoder(JwtProperties props) {
        // La clé HMAC doit être non-null et suffisamment longue.
        // En pratique: >= 32 chars pour HS256 (32 bytes recommandé).
        byte[] secretBytes = requireSecretBytes(props);

        // SecretKeySpec => clé HMAC exploitable par Nimbus
        var key = new SecretKeySpec(secretBytes, "HmacSHA256");

        // NimbusJwtEncoder utilise une source de clés (JWKSource).
        // Ici on crée un encoder HMAC via ImmutableSecret.
        return new NimbusJwtEncoder(new com.nimbusds.jose.jwk.source.ImmutableSecret<>(key));
    }

    /**
     * Bean principal de JwtDecoder pour tests.
     *
     * Il doit utiliser la même clé que l'encoder (sinon signature invalide).
     */
    @Bean
    @Primary
    JwtDecoder jwtDecoder(JwtProperties props) {
        byte[] secretBytes = requireSecretBytes(props);

        var key = new SecretKeySpec(secretBytes, "HmacSHA256");

        // NimbusJwtDecoder permet de valider HS256 en HMAC avec la même clé.
        return NimbusJwtDecoder.withSecretKey(key).macAlgorithm(MacAlgorithm.HS256).build();
    }

    /**
     * Utilitaire: génère un token "comme la prod" mais contrôlable pour les tests.
     *
     * Pourquoi:
     * - Dans certaines IT, tu veux un token valide mais volontairement incomplet
     *   (ex: missing claim sid) pour tester le comportement de sécurité.
     *
     * Design:
     * - Méthode statique => usage simple dans les tests.
     * - Claims minimaux JWT: iat, exp, sub
     * - Claims custom en paramètre (sid, rôles, etc.)
     */
    public static String mint(
            JwtEncoder encoder,
            String subjectUserId,
            Instant issuedAt,
            Instant expiresAt,
            Map<String, Object> extraClaims
    ) {
        var claimsBuilder = JwtClaimsSet.builder()
                .issuedAt(issuedAt)
                .expiresAt(expiresAt)
                .subject(subjectUserId);

        if (extraClaims != null) {
            extraClaims.forEach(claimsBuilder::claim);
        }

        var claims = claimsBuilder.build();
        var headers = JwsHeader.with(MacAlgorithm.HS256).build();

        return encoder.encode(JwtEncoderParameters.from(headers, claims)).getTokenValue();
    }

    /**
     * Variante pratique: userId + societeId.
     * (Utile si tu veux générer un token standard rapidement.)
     */
    public static String mintAccessToken(
            JwtEncoder encoder,
            UUID userId,
            UUID societeId,
            Instant issuedAt,
            Instant expiresAt
    ) {
        return mint(
                encoder,
                userId.toString(),
                issuedAt,
                expiresAt,
                Map.of("sid", societeId.toString())
        );
    }

    /**
     * Check centralisé: on refuse une config test silencieusement invalide.
     * Sinon tu perds du temps avec des NPE au démarrage des tests.
     */
    private static byte[] requireSecretBytes(JwtProperties props) {
        String secret = props.secret();
        if (secret == null || secret.isBlank()) {
            throw new IllegalStateException(
                    "JWT secret is missing for tests. " +
                            "Please set 'app.security.jwt.secret' in application-test.yml"
            );
        }
        return secret.getBytes(StandardCharsets.UTF_8);
    }
}
