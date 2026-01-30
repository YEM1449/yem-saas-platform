package com.yem.hlm.backend.auth.config;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import com.nimbusds.jose.proc.SecurityContext;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;

@Configuration
@EnableConfigurationProperties(JwtProperties.class)
public class JwtBeansConfig {

    private static final MacAlgorithm ALG = MacAlgorithm.HS256;

    @Bean
    public JwtEncoder jwtEncoder(JwtProperties props) {
        String secret = props.secret();

        if (secret == null || secret.isBlank()) {
            throw new IllegalStateException(
                    "security.jwt.secret is missing (null/blank). " +
                            "Set it in application.yml / application-test.yml"
            );
        }

        byte[] secretBytes = secret.getBytes(StandardCharsets.UTF_8);

        // ✅ IMPORTANT: le generic est SecurityContext (Nimbus), pas byte[]
        ImmutableSecret<SecurityContext> jwkSource = new ImmutableSecret<>(secretBytes);

        return new NimbusJwtEncoder(jwkSource);
    }

    @Bean
    public JwtDecoder jwtDecoder(JwtProperties props) {
        String secret = props.secret();

        if (secret == null || secret.isBlank()) {
            throw new IllegalStateException(
                    "security.jwt.secret is missing (null/blank). " +
                            "Set it in application.yml / application-test.yml"
            );
        }

        SecretKey secretKey = new SecretKeySpec(
                secret.getBytes(StandardCharsets.UTF_8),
                "HmacSHA256"
        );

        return NimbusJwtDecoder
                .withSecretKey(secretKey)
                .macAlgorithm(ALG)
                .build();
    }
}
