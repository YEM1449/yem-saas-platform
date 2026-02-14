package com.yem.hlm.backend.auth.config;

import java.nio.charset.StandardCharsets;

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
 */
@Configuration
@EnableConfigurationProperties(JwtProperties.class)
public class JwtBeansConfig {

    private static final int HS256_MIN_KEY_BYTES = 32;

    @Bean
    JwtEncoder jwtEncoder(JwtProperties props) {
        byte[] secretBytes = validatedSecretBytes(props);
        ImmutableSecret<SecurityContext> jwkSource = new ImmutableSecret<>(secretBytes);
        return new NimbusJwtEncoder(jwkSource);
    }

    @Bean
    JwtDecoder jwtDecoder(JwtProperties props) {
        byte[] secretBytes = validatedSecretBytes(props);
        SecretKey secretKey = new SecretKeySpec(secretBytes, "HmacSHA256");
        return NimbusJwtDecoder
                .withSecretKey(secretKey)
                .macAlgorithm(MacAlgorithm.HS256)
                .build();
    }

    private static byte[] validatedSecretBytes(JwtProperties props) {
        String secret = props.secret();
        if (secret == null || secret.isBlank()) {
            throw new IllegalStateException(
                    "security.jwt.secret must be configured (non-empty)");
        }
        byte[] bytes = secret.getBytes(StandardCharsets.UTF_8);
        if (bytes.length < HS256_MIN_KEY_BYTES) {
            throw new IllegalStateException(
                    "security.jwt.secret must be at least 32 bytes (256 bits) for HS256. "
                            + "Current length: " + bytes.length + " bytes");
        }
        return bytes;
    }
}
