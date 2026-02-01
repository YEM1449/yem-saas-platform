package com.yem.hlm.backend.support;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.jwk.OctetSequenceKey;
import com.nimbusds.jose.jwk.KeyUse;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.jwk.source.JWKSelector;
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
import java.util.UUID;

@TestConfiguration
public class TestJwtConfig {

    private static final byte[] TEST_SECRET = generateSecret();

    @Bean
    @Primary
    JwtEncoder testJwtEncoder() {
        OctetSequenceKey jwk = new OctetSequenceKey.Builder(TEST_SECRET)
                .keyID(UUID.randomUUID().toString())
                .keyUse(KeyUse.SIGNATURE)
                .algorithm(JWSAlgorithm.HS256)
                .build();

        JWKSource<SecurityContext> jwkSource = (JWKSelector selector, SecurityContext context) ->
                selector.select(new com.nimbusds.jose.jwk.JWKSet(jwk));

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
