package com.yem.hlm.backend.auth.service;

import com.yem.hlm.backend.auth.api.dto.LoginRequest;
import com.yem.hlm.backend.auth.api.dto.LoginResponse;
import com.yem.hlm.backend.auth.config.JwtProperties;
import com.yem.hlm.backend.tenant.repo.TenantRepository;
import com.yem.hlm.backend.user.repo.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class AuthService {
    private final TenantRepository tenantRepository;
    private final UserRepository userRepository;
    private final PasswordEncoder   passwordEncoder;
    private final JwtProvider        jwtProvider;
    private final JwtProperties      jwtProperties;

    public AuthService(TenantRepository tenantRepository,
                       UserRepository userRepository,
                       PasswordEncoder passwordEncoder,
                       JwtProvider jwtProvider,
                       JwtProperties jwtProperties) {
        this.tenantRepository = tenantRepository;
        this.userRepository   = userRepository;
        this.passwordEncoder  = passwordEncoder;
        this.jwtProvider      = jwtProvider;
        this.jwtProperties    = jwtProperties;
    }
    public LoginResponse login (LoginRequest req){
        // 1) Normaliser l’input (important pour éviter admin@X.com vs admin@x.com)
        String tenantKey = req.tenantKey().trim().toLowerCase();
        String email     = req.email().trim().toLowerCase();
        String password  = req.password();

        // 2) Charger tenant
        var tenant = tenantRepository.findByKey(tenantKey)
                        .orElseThrow(UnauthorizedException::new); // ne révèle pas si tenant existe
        // 3) Charger user
        var user = userRepository.findByTenant_IdAndEmail(tenant.getId(), email)
                .orElseThrow(UnauthorizedException::new);

        // 4) Vérifier enabled
        if (!user.isEnabled()){
            throw new UnauthorizedException();
        }

        // 5) Vérifier password (BCrypt)
        if (!passwordEncoder.matches(password, user.getPasswordHash())){
            throw new UnauthorizedException();
        }

        // 6) Generate real JWT
        String token = jwtProvider.generate(user.getId(), tenant.getId(), user.getRole());
        long expiresInSeconds = jwtProperties.ttlSeconds();

        return LoginResponse.bearer(token, expiresInSeconds);
    }
}
