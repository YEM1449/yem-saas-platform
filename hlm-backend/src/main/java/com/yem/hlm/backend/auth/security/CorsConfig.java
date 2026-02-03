package com.yem.hlm.backend.auth.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

@Configuration
public class CorsConfig {

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();

        // ✅ Mets ici les origins de ton frontend (dev)
        config.setAllowedOrigins(List.of(
                "http://localhost:5173", // Vite
                "http://localhost:3000"  // CRA / Next dev
        ));

        // ✅ Inclure OPTIONS pour preflight
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));

        // ✅ Autorise les headers nécessaires (Authorization, Content-Type, etc.)
        config.setAllowedHeaders(List.of("*"));

        // ✅ Expose Location si tu l’utilises (ex: POST /tenants => Location header)
        config.setExposedHeaders(List.of("Location"));

        // Si tu n'utilises pas cookies, tu peux laisser false.
        // Si true: allowedOrigins ne peut pas être "*".
        config.setAllowCredentials(false);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}
