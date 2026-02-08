package com.yem.hlm.backend.auth.config;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import com.nimbusds.jose.proc.SecurityContext;

/**
 * JWT beans (Encoder/Decoder) based on a shared HMAC secret.
 *
 * <p>Architecture notes:
 * <ul>
 *   <li>Stateless auth: no server-side sessions.</li>
 *   <li>Multi-tenant isolation: tenantId must be carried inside the token as claim "tid".</li>
 *   <li>HMAC secret must exist in ALL envs (including tests).</li>
 * </ul>
 */
@Configuration
@EnableConfigurationProperties(JwtProperties.class)
public class JwtBeansConfig {

    /**
     * Creates a {@link JwtEncoder} that SIGNS JWTs with the configured HMAC secret.
     *
     * <p>We use NimbusJwtEncoder backed by Nimbus' ImmutableSecret JWK source.</p>
     */
    @Bean
    JwtEncoder jwtEncoder(JwtProperties props) {
        // Fail fast: missing secret should be a configuration error, not a runtime NPE later.
        String secret = props.secret();
        if (secret == null || secret.isBlank()) {
            throw new IllegalStateException("app.security.jwt.secret must be configured (non-empty)");
        }

        // Deterministic byte conversion (avoid platform-default encoding surprises).
        byte[] secretBytes = secret.getBytes(java.nio.charset.StandardCharsets.UTF_8);

        // ImmutableSecret is a Nimbus JWKSource<SecurityContext> for HMAC keys.
        ImmutableSecret<SecurityContext> jwkSource = new ImmutableSecret<>(secretBytes);

        return new NimbusJwtEncoder(jwkSource);
    }

    /**
     * Creates a {@link JwtDecoder} that VERIFIES JWTs with the same HMAC secret.
     *
     * <p>Decoder MUST use the same algorithm as the encoder (HS256 here).</p>
     */
    @Bean
    JwtDecoder jwtDecoder(JwtProperties props) {
        String secret = props.secret();
        if (secret == null || secret.isBlank()) {
            throw new IllegalStateException("security.jwt.secret must be configured (non-empty)");
        }

        byte[] secretBytes = secret.getBytes(java.nio.charset.StandardCharsets.UTF_8);

        // NimbusJwtDecoder expects a SecretKey for HMAC verification.
        SecretKey secretKey = new SecretKeySpec(secretBytes, "HmacSHA256");

        return NimbusJwtDecoder
                .withSecretKey(secretKey)
                .macAlgorithm(MacAlgorithm.HS256)
                .build();
    }
}
