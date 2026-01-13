package com.yem.hlm.backend.auth.security;

import com.yem.hlm.backend.auth.service.JwtProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * Configuration Spring Security :
 * - stateless (JWT)
 * - routes publiques vs protégées
 * - ajout du filtre JWT
 */
@Configuration
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http, JwtProvider jwtProvider) throws Exception {

        // Création de notre filtre JWT (il dépend de JwtProvider)
        var jwtFilter = new JwtAuthenticationFilter(jwtProvider);

        return http
                // API stateless -> on désactive CSRF
                .csrf(csrf -> csrf.disable())

                // pas de session serveur
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))

                // règles d'accès
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/auth/login").permitAll() // public
                        .requestMatchers("/actuator/health", "/actuator/info").permitAll() // public
                        .anyRequest().authenticated() // le reste protégé
                )

                // exécuter notre filtre avant le filtre standard de login
                .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class)

                .build();
    }
}
