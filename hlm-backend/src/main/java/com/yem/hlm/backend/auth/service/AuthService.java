package com.yem.hlm.backend.auth.service;

import com.yem.hlm.backend.auth.api.dto.LoginRequest;
import com.yem.hlm.backend.auth.api.dto.LoginResponse;
import com.yem.hlm.backend.tenant.repo.TenantRepository;
import com.yem.hlm.backend.user.repo.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class AuthService {
    private final TenantRepository tenantRepository;
    private final UserRepository userRepository;
    private final PasswordEncoder   passwordEncoder;

    public AuthService(TenantRepository tenantRepository
            , UserRepository userRepository
            , PasswordEncoder passwordEncoder){
        this.tenantRepository = tenantRepository;
        this.userRepository   = userRepository;
        this.passwordEncoder  = passwordEncoder;
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

        // 6) Token (stub tant que AUTH-04 n’est pas branché)
        String tokenStub = "TODO_JWT_IN_AUTH_04";
        long expiresInSeconds = 3600;

        return LoginResponse.bearer(tokenStub, expiresInSeconds);
    }
}
