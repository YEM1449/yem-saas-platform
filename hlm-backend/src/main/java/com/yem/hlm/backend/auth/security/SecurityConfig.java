package com.yem.hlm.backend.auth.security;

import com.yem.hlm.backend.auth.service.JwtProvider;
import com.yem.hlm.backend.auth.service.UserSecurityCacheService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            JwtProvider jwtProvider,
            UserSecurityCacheService userSecurityCacheService,
            CustomAuthenticationEntryPoint authenticationEntryPoint,
            CustomAccessDeniedHandler accessDeniedHandler
    ) throws Exception {
        var jwtFilter = new JwtAuthenticationFilter(jwtProvider, userSecurityCacheService);

        return http
                .cors(Customizer.withDefaults())
                .csrf(csrf -> csrf.disable())
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()

                        // PUBLIC CRM endpoints
                        .requestMatchers("/auth/login").permitAll()
                        .requestMatchers("/actuator/health", "/actuator/info").permitAll()

                        // OpenAPI / Swagger UI
                        .requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll()

                        // Tenant bootstrap
                        .requestMatchers(HttpMethod.POST, "/tenants").permitAll()

                        // Portal auth — public (no JWT required)
                        .requestMatchers(HttpMethod.POST, "/api/portal/auth/request-link").permitAll()
                        .requestMatchers(HttpMethod.GET,  "/api/portal/auth/verify").permitAll()

                        // Portal data endpoints — ROLE_PORTAL only
                        .requestMatchers("/api/portal/**").hasRole("PORTAL")

                        // All other /api/** — blocked to ROLE_PORTAL; CRM roles only
                        .requestMatchers("/api/**").hasAnyRole("ADMIN", "MANAGER", "AGENT")

                        .anyRequest().authenticated()
                )
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint(authenticationEntryPoint)
                        .accessDeniedHandler(accessDeniedHandler)
                )
                .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class)
                .build();
    }
}
