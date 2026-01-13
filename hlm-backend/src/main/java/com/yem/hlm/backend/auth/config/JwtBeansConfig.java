package com.yem.hlm.backend.auth.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.security.oauth2.jwt.*;

import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;

/**
 * Classe de configuration Spring pour JWT.
 *
 * Elle déclare :
 * - JwtEncoder → pour CRÉER des tokens
 * - JwtDecoder → pour LIRE / VALIDER des tokens
 */
@Configuration
@EnableConfigurationProperties(JwtProperties.class)
public class JwtBeansConfig {

    /**
     * Bean responsable de la génération (signature) des JWT.
     */
    @Bean
    public JwtEncoder jwtEncoder(JwtProperties props) {

        // Conversion du secret en tableau de bytes UTF-8
        byte[] keyBytes = props.secret().getBytes(StandardCharsets.UTF_8);

        // Création de la clé HMAC SHA-256 à partir du secret
        var secretKey = new SecretKeySpec(keyBytes, "HmacSHA256");

        // NimbusJwtEncoder utilise cette clé pour signer les tokens
        return new NimbusJwtEncoder(new ImmutableSecret<>(secretKey));
    }

    /**
     * Bean responsable de la validation et du décodage des JWT.
     */
    @Bean
    public JwtDecoder jwtDecoder(JwtProperties props) {

        // Même clé secrète que pour l’encoder
        byte[] keyBytes = props.secret().getBytes(StandardCharsets.UTF_8);

        // Clé HMAC SHA-256
        var secretKey = new SecretKeySpec(keyBytes, "HmacSHA256");

        // Le decoder vérifiera automatiquement :
        // - la signature
        // - l’expiration
        // - la structure du token
        return NimbusJwtDecoder.withSecretKey(secretKey).build();
    }
}
