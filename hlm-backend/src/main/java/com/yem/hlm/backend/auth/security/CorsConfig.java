package com.yem.hlm.backend.auth.security;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;
import java.util.List;

@Configuration
public class CorsConfig {

    private static final Logger log = LoggerFactory.getLogger(CorsConfig.class);

    @Value("${app.cors.allowed-origins:}")
    private String allowedOriginsRaw;

    @Value("${spring.profiles.active:default}")
    private String activeProfile;

    @PostConstruct
    void validateCorsOrigins() {
        if (isProduction() && containsLocalhost()) {
            log.error("CRITICAL: CORS_ALLOWED_ORIGINS contains localhost/127.0.0.1 in production profile! " +
                      "Set CORS_ALLOWED_ORIGINS to your exact frontend domain: https://app.yourdomain.com");
            throw new IllegalStateException(
                "CORS_ALLOWED_ORIGINS must not contain 'localhost' or '127.0.0.1' in production. " +
                "Current value includes development origins. " +
                "Set: CORS_ALLOWED_ORIGINS=https://app.yourdomain.com");
        }
        if (containsLocalhost()) {
            log.warn("CORS_ALLOWED_ORIGINS contains localhost — acceptable for development only");
        }
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();

        List<String> origins = (allowedOriginsRaw == null || allowedOriginsRaw.isBlank())
                ? List.of()
                : Arrays.stream(allowedOriginsRaw.split(","))
                        .map(String::trim)
                        .filter(s -> !s.isBlank())
                        .toList();
        config.setAllowedOrigins(origins);

        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        config.setExposedHeaders(List.of("Location"));
        config.setAllowCredentials(false);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }

    private boolean isProduction() {
        return activeProfile != null &&
               (activeProfile.contains("production") || activeProfile.contains("prod"));
    }

    private boolean containsLocalhost() {
        if (allowedOriginsRaw == null || allowedOriginsRaw.isBlank()) return false;
        String lower = allowedOriginsRaw.toLowerCase();
        return lower.contains("localhost") || lower.contains("127.0.0.1");
    }
}
