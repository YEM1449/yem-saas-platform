package com.yem.hlm.backend.support;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import com.nimbusds.jose.proc.SecurityContext;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.security.SecureRandom;

@TestConfiguration
public class TestJwtConfig {

    private static final byte[] TEST_SECRET = generateSecret();

    @Bean
    @Primary
    JwtEncoder testJwtEncoder() {
        ImmutableSecret<SecurityContext> jwkSource = new ImmutableSecret<>(TEST_SECRET);
        return new NimbusJwtEncoder(jwkSource);
    }

    @Bean
    @Primary
    JwtDecoder testJwtDecoder() {
        SecretKey secretKey = new SecretKeySpec(TEST_SECRET, "HmacSHA256");
        return NimbusJwtDecoder.withSecretKey(secretKey)
                .macAlgorithm(MacAlgorithm.HS256)
                .build();
    }

    private static byte[] generateSecret() {
        byte[] secret = new byte[32];
        new SecureRandom().nextBytes(secret);
        return secret;
    }
}
