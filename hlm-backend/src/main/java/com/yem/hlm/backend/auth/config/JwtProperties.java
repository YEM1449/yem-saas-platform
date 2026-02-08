package com.yem.hlm.backend.auth.config;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Classe de configuration typée pour JWT.
 *
 * Elle mappe automatiquement les propriétés :
 *
 * security.jwt.secret
 * security.jwt.ttl-seconds
 *
 * depuis application.yml / variables d'environnement.
 *
 * Fail-fast: si JWT_SECRET n'est pas défini, l'application refuse de démarrer.
 */
@Validated
@ConfigurationProperties(prefix = "security.jwt")
public record JwtProperties(

        // Clé secrète utilisée pour signer le JWT (HMAC SHA-256)
        // ⚠️ Doit faire au moins 32 caractères
        @NotBlank String secret,

        // Durée de vie du token en secondes (ex: 3600 = 1 heure)
        long ttlSeconds
) {}
