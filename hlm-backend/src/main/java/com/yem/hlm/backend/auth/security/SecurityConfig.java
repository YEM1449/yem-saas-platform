package com.yem.hlm.backend.auth.security;

import com.yem.hlm.backend.auth.service.JwtProvider;
import com.yem.hlm.backend.auth.service.SecurityAuditLogger;
import com.yem.hlm.backend.auth.service.UserSecurityCacheService;
import org.springframework.beans.factory.annotation.Value;
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

    @Value("${server.ssl.enabled:false}")
    private boolean sslEnabled;

    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            JwtProvider jwtProvider,
            UserSecurityCacheService userSecurityCacheService,
            SecurityAuditLogger securityAuditLogger,
            CustomAuthenticationEntryPoint authenticationEntryPoint,
            CustomAccessDeniedHandler accessDeniedHandler
    ) throws Exception {
        var jwtFilter = new JwtAuthenticationFilter(jwtProvider, userSecurityCacheService, securityAuditLogger);

        http
                .cors(Customizer.withDefaults())
                .csrf(csrf -> csrf.disable())
                .headers(h -> {
                    // Content-Security-Policy: restrict resource origins; block framing;
                    // allow inline styles (Angular requirement); restrict form submissions.
                    h.contentSecurityPolicy(csp -> csp.policyDirectives(
                            "default-src 'self'; " +
                            "script-src 'self'; " +
                            "style-src 'self' 'unsafe-inline'; " +
                            "img-src 'self' data:; " +
                            "font-src 'self'; " +
                            "connect-src 'self'; " +
                            "frame-ancestors 'none'; " +
                            "form-action 'self'; " +
                            "base-uri 'self'"));
                    // Clickjacking protection (belt-and-suspenders alongside CSP frame-ancestors)
                    h.frameOptions(fo -> fo.deny());
                    // Prevent MIME-type sniffing
                    h.contentTypeOptions(ct -> {});
                    // Referrer: send origin only on same-site; omit on cross-origin downgrade
                    h.referrerPolicy(rp -> rp.policy(
                            org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter
                                    .ReferrerPolicy.STRICT_ORIGIN_WHEN_CROSS_ORIGIN));
                    // Permissions-Policy: disable sensors/media not used by this app
                    // (using StaticHeadersWriter — permissionsPolicy() deprecated in Spring Security 6.4)
                    h.addHeaderWriter(new org.springframework.security.web.header.writers.StaticHeadersWriter(
                            "Permissions-Policy",
                            "geolocation=(), microphone=(), camera=(), " +
                            "payment=(), usb=(), magnetometer=(), gyroscope=()"));
                    // X-XSS-Protection: legacy browser safeguard (ignored by modern browsers,
                    // harmless to keep for defense-in-depth)
                    h.xssProtection(xss -> xss.headerValue(
                            org.springframework.security.web.header.writers.XXssProtectionHeaderWriter
                                    .HeaderValue.ENABLED_MODE_BLOCK));
                    // HSTS is only meaningful (and safe) when the connection is already over TLS.
                    // Emitting it over plain HTTP would cause browsers to block future plain-HTTP
                    // access, which is wrong if TLS is terminated externally.
                    if (sslEnabled) {
                        h.httpStrictTransportSecurity(hsts -> hsts
                                .includeSubDomains(true)
                                .maxAgeInSeconds(31536000)
                                .preload(true));
                    } else {
                        h.httpStrictTransportSecurity(hsts -> hsts.disable());
                    }
                })
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()

                        // PUBLIC CRM endpoints
                        .requestMatchers("/auth/login").permitAll()
                        .requestMatchers("/actuator/health", "/actuator/info").permitAll()

                        // Société switch — token validated internally by AuthService
                        // (no prior Spring Security auth required; partial tokens are accepted here)
                        .requestMatchers(HttpMethod.POST, "/auth/switch-societe").permitAll()

                        // Invitation flow — public (no JWT required)
                        .requestMatchers("/auth/invitation/**").permitAll()

                        // Super-admin platform management (new canonical path)
                        .requestMatchers("/api/admin/**").hasRole("SUPER_ADMIN")

                        // Super-admin société management (legacy path — kept for backward compat)
                        .requestMatchers("/api/societes/**").hasRole("SUPER_ADMIN")

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
                .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}
