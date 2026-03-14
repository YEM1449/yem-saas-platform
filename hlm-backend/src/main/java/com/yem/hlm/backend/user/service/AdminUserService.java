package com.yem.hlm.backend.user.service;

import com.yem.hlm.backend.auth.service.SecurityAuditLogger;
import com.yem.hlm.backend.auth.service.UserSecurityCacheService;
import com.yem.hlm.backend.contact.service.CrossTenantAccessException;
import com.yem.hlm.backend.tenant.domain.Tenant;
import com.yem.hlm.backend.tenant.repo.TenantRepository;
import com.yem.hlm.backend.tenant.context.TenantContext;
import com.yem.hlm.backend.user.api.dto.*;
import com.yem.hlm.backend.user.domain.User;
import com.yem.hlm.backend.user.repo.UserRepository;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.util.List;
import java.util.UUID;

@Service
public class AdminUserService {

    private final UserRepository userRepository;
    private final TenantRepository tenantRepository;
    private final PasswordEncoder passwordEncoder;
    private final UserSecurityCacheService userSecurityCacheService;
    private final SecurityAuditLogger securityAuditLogger;
    private final SecureRandom secureRandom = new SecureRandom();

    public AdminUserService(UserRepository userRepository,
                            TenantRepository tenantRepository,
                            PasswordEncoder passwordEncoder,
                            UserSecurityCacheService userSecurityCacheService,
                            SecurityAuditLogger securityAuditLogger) {
        this.userRepository = userRepository;
        this.tenantRepository = tenantRepository;
        this.passwordEncoder = passwordEncoder;
        this.userSecurityCacheService = userSecurityCacheService;
        this.securityAuditLogger = securityAuditLogger;
    }

    @Transactional(readOnly = true)
    public List<UserResponse> list(String q) {
        UUID tenantId = requireTenantId();
        return userRepository.searchByTenant(tenantId, q)
                .stream()
                .map(UserResponse::from)
                .toList();
    }

    @Transactional
    public UserResponse create(CreateUserRequest request) {
        UUID tenantId = requireTenantId();
        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new CrossTenantAccessException("Unknown tenant: " + tenantId));

        userRepository.findByTenant_IdAndEmail(tenantId, request.email())
                .ifPresent(existing -> {
                    throw new UserEmailAlreadyExistsException(request.email());
                });

        User user = new User(tenant, request.email(), passwordEncoder.encode(request.password()));
        user.setRole(request.role());
        User saved = userRepository.save(user);
        return UserResponse.from(saved);
    }

    @Transactional
    public UserResponse changeRole(UUID userId, ChangeRoleRequest request) {
        User user = findUserInTenant(userId);
        user.setRole(request.role());
        user.incrementTokenVersion();
        User saved = userRepository.save(user);
        userSecurityCacheService.evict(userId);

        // Audit: token revocation due to role change
        UUID actorId = resolveActorId();
        String tenantKey = resolveTenantKey(requireTenantId());
        securityAuditLogger.logTokenRevocation(tenantKey, userId, actorId, "ROLE_CHANGE");

        return UserResponse.from(saved);
    }

    @Transactional
    public UserResponse setEnabled(UUID userId, SetEnabledRequest request) {
        User user = findUserInTenant(userId);
        user.setEnabled(request.enabled());
        user.incrementTokenVersion();
        User saved = userRepository.save(user);
        userSecurityCacheService.evict(userId);

        // Audit: token revocation when account is disabled
        if (!request.enabled()) {
            UUID actorId = resolveActorId();
            String tenantKey = resolveTenantKey(requireTenantId());
            securityAuditLogger.logTokenRevocation(tenantKey, userId, actorId, "ACCOUNT_DISABLED");
        }

        return UserResponse.from(saved);
    }

    @Transactional
    public ResetPasswordResponse resetPassword(UUID userId) {
        User user = findUserInTenant(userId);
        String tempPassword = generateTempPassword();
        user.setPasswordHash(passwordEncoder.encode(tempPassword));
        user.incrementTokenVersion();
        userRepository.save(user);
        userSecurityCacheService.evict(userId);
        return new ResetPasswordResponse(tempPassword);
    }

    private User findUserInTenant(UUID userId) {
        UUID tenantId = requireTenantId();
        return userRepository.findByTenant_IdAndId(tenantId, userId)
                .orElseThrow(() -> new UserNotFoundException(userId));
    }

    private UUID requireTenantId() {
        UUID tenantId = TenantContext.getTenantId();
        if (tenantId == null) {
            throw new CrossTenantAccessException("Missing tenant context");
        }
        return tenantId;
    }

    private UUID resolveActorId() {
        try {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth != null && auth.getPrincipal() instanceof UUID uuid) {
                return uuid;
            }
        } catch (Exception ignored) {}
        return null;
    }

    private String resolveTenantKey(UUID tenantId) {
        return tenantRepository.findById(tenantId)
                .map(t -> t.getKey())
                .orElse(tenantId.toString());
    }

    private String generateTempPassword() {
        String chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789!@#$";
        StringBuilder sb = new StringBuilder(16);
        for (int i = 0; i < 16; i++) {
            sb.append(chars.charAt(secureRandom.nextInt(chars.length())));
        }
        return sb.toString();
    }
}
