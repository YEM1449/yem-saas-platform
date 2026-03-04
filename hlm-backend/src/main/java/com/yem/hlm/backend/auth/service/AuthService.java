package com.yem.hlm.backend.auth.service;

import com.yem.hlm.backend.auth.api.dto.LoginRequest;
import com.yem.hlm.backend.auth.api.dto.LoginResponse;
import com.yem.hlm.backend.auth.config.JwtProperties;
import com.yem.hlm.backend.common.ratelimit.RateLimiterService;
import com.yem.hlm.backend.tenant.repo.TenantRepository;
import com.yem.hlm.backend.user.repo.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    private final TenantRepository tenantRepository;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtProvider jwtProvider;
    private final JwtProperties jwtProperties;
    private final RateLimiterService rateLimiterService;

    public AuthService(TenantRepository tenantRepository,
                       UserRepository userRepository,
                       PasswordEncoder passwordEncoder,
                       JwtProvider jwtProvider,
                       JwtProperties jwtProperties,
                       RateLimiterService rateLimiterService) {
        this.tenantRepository  = tenantRepository;
        this.userRepository    = userRepository;
        this.passwordEncoder   = passwordEncoder;
        this.jwtProvider       = jwtProvider;
        this.jwtProperties     = jwtProperties;
        this.rateLimiterService = rateLimiterService;
    }

    @Transactional(readOnly = true)
    public LoginResponse login(LoginRequest req) {
        String tenantKey = req.tenantKey().trim().toLowerCase();
        String email     = req.email().trim().toLowerCase();
        String password  = req.password();

        rateLimiterService.checkLogin(email);

        var tenant = tenantRepository.findByKey(tenantKey)
                .orElseThrow(UnauthorizedException::new);

        var user = userRepository.findByTenant_IdAndEmail(tenant.getId(), email)
                .orElseThrow(UnauthorizedException::new);

        if (!user.isEnabled()) {
            throw new UnauthorizedException();
        }

        if (!passwordEncoder.matches(password, user.getPasswordHash())) {
            throw new UnauthorizedException();
        }

        log.debug("Login successful for tenant={} user={} role={}",
                tenantKey, email, user.getRole());

        String token = jwtProvider.generate(user.getId(), tenant.getId(), user.getRole(), user.getTokenVersion());
        long expiresInSeconds = jwtProperties.ttlSeconds();

        return LoginResponse.bearer(token, expiresInSeconds);
    }
}
