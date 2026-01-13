package com.yem.hlm.backend.auth.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Classe de configuration typée pour JWT.
 *
 * Elle mappe automatiquement les propriétés :
 *
 * security.jwt.secret
 * security.jwt.ttl-seconds
 *
 * depuis application.yml / variables d'environnement.
 */
@ConfigurationProperties(prefix = "security.jwt")
public record JwtProperties(

        // Clé secrète utilisée pour signer le JWT (HMAC SHA-256)
        // ⚠️ Doit faire au moins 32 caractères
        String secret,

        // Durée de vie du token en secondes (ex: 3600 = 1 heure)
        long ttlSeconds
) {}
