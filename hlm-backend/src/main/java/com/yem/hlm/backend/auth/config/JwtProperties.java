package com.yem.hlm.backend.auth.config;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Typed configuration for JWT (security.jwt.*).
 *
 * Fail-fast: app refuses to start if secret is missing, blank, or &lt; 32 chars (HS256 minimum).
 */
@Validated
@ConfigurationProperties(prefix = "security.jwt")
public record JwtProperties(

        @NotBlank
        @Size(min = 32, message = "security.jwt.secret must be at least 32 characters (256 bits for HS256)")
        String secret,

        long ttlSeconds
) {}
