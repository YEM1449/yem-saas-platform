package com.yem.hlm.backend.auth.config;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.KeyUse;
import com.nimbusds.jose.jwk.OctetSequenceKey;
import com.nimbusds.jose.proc.SecurityContext;
import com.nimbusds.jose.jwk.source.JWKSource;
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

    @Bean
    @Primary
    SecretKey testJwtSecretKey() {
        byte[] secretBytes = new byte[32];
        new SecureRandom().nextBytes(secretBytes);
        return new SecretKeySpec(secretBytes, "HmacSHA256");
    }

    @Bean
    @Primary
    JWKSource<SecurityContext> testJwkSource(SecretKey testJwtSecretKey) {
        OctetSequenceKey jwk = new OctetSequenceKey.Builder(testJwtSecretKey.getEncoded())
                .keyUse(KeyUse.SIGNATURE)
                .algorithm(JWSAlgorithm.HS256)
                .keyID(UUID.randomUUID().toString())
                .build();
        JWKSet jwkSet = new JWKSet(jwk);
        return (jwkSelector, securityContext) -> jwkSelector.select(jwkSet);
    }

    @Bean
    @Primary
    JwtEncoder testJwtEncoder(JWKSource<SecurityContext> testJwkSource) {
        return new NimbusJwtEncoder(testJwkSource);
    }

    @Bean
    @Primary
    JwtDecoder testJwtDecoder(SecretKey testJwtSecretKey) {
        return NimbusJwtDecoder
                .withSecretKey(testJwtSecretKey)
                .macAlgorithm(MacAlgorithm.HS256)
                .build();
    }
}
