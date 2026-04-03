package com.yem.hlm.backend.user.service;

import com.yem.hlm.backend.auth.service.SecurityAuditLogger;
import com.yem.hlm.backend.auth.service.UserSecurityCacheService;
import com.yem.hlm.backend.contact.service.CrossTenantAccessException;
import com.yem.hlm.backend.societe.domain.AppUserSociete;
import com.yem.hlm.backend.societe.domain.AppUserSocieteId;
import com.yem.hlm.backend.societe.AppUserSocieteRepository;
import com.yem.hlm.backend.societe.QuotaService;
import com.yem.hlm.backend.societe.SocieteContext;
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
    private final AppUserSocieteRepository appUserSocieteRepository;
    private final PasswordEncoder passwordEncoder;
    private final UserSecurityCacheService userSecurityCacheService;
    private final SecurityAuditLogger securityAuditLogger;
    private final QuotaService quotaService;
    private final SecureRandom secureRandom = new SecureRandom();

    public AdminUserService(UserRepository userRepository,
                            AppUserSocieteRepository appUserSocieteRepository,
                            PasswordEncoder passwordEncoder,
                            UserSecurityCacheService userSecurityCacheService,
                            SecurityAuditLogger securityAuditLogger,
                            QuotaService quotaService) {
        this.userRepository = userRepository;
        this.appUserSocieteRepository = appUserSocieteRepository;
        this.passwordEncoder = passwordEncoder;
        this.userSecurityCacheService = userSecurityCacheService;
        this.securityAuditLogger = securityAuditLogger;
        this.quotaService = quotaService;
    }

    @Transactional(readOnly = true)
    public List<UserResponse> list(String q) {
        UUID societeId = requireSocieteId();
        return userRepository.searchBySociete(societeId, q)
                .stream()
                .map(UserResponse::from)
                .toList();
    }

    /**
     * Lightweight suggest — accessible to ADMIN, MANAGER and AGENT.
     * Returns id + displayName + email so the frontend can show a name-based
     * typeahead without ever exposing UUIDs to the user.
     */
    @Transactional(readOnly = true)
    public List<UserSuggestResponse> suggest(String q) {
        UUID societeId = requireSocieteId();
        return userRepository.suggestBySociete(societeId, q)
                .stream()
                .map(u -> new UserSuggestResponse(u.getId(), buildDisplayName(u), u.getEmail()))
                .toList();
    }

    private static String buildDisplayName(com.yem.hlm.backend.user.domain.User u) {
        String prenom = u.getPrenom();
        String nom    = u.getNomFamille();
        if (prenom != null && !prenom.isBlank() && nom != null && !nom.isBlank()) return prenom + " " + nom;
        if (prenom != null && !prenom.isBlank()) return prenom;
        if (nom    != null && !nom.isBlank())    return nom;
        return u.getEmail();
    }

    @Transactional
    public UserResponse create(CreateUserRequest request) {
        UUID societeId = requireSocieteId();
        quotaService.enforceUserQuota(societeId);

        userRepository.findByEmail(request.email())
                .ifPresent(existing -> {
                    throw new UserEmailAlreadyExistsException(request.email());
                });

        User user = new User(request.email(), passwordEncoder.encode(request.password()));
        User saved = userRepository.save(user);

        // Register membership in this société with the requested role (stored without ROLE_ prefix)
        var membership = new AppUserSociete(new AppUserSocieteId(saved.getId(), societeId), toSocieteRole(request.role()));
        appUserSocieteRepository.save(membership);

        return UserResponse.from(saved);
    }

    @Transactional
    public UserResponse changeRole(UUID userId, ChangeRoleRequest request) {
        User user = findUserInSociete(userId);
        UUID societeId = requireSocieteId();

        // Update the AppUserSociete membership role
        String newRole = toSocieteRole(request.role());
        appUserSocieteRepository.findByIdUserIdAndIdSocieteId(userId, societeId)
                .ifPresent(membership -> {
                    membership.setRole(newRole);
                    appUserSocieteRepository.save(membership);
                });

        user.incrementTokenVersion();
        User saved = userRepository.save(user);
        userSecurityCacheService.evict(userId);

        UUID actorId = resolveActorId();
        securityAuditLogger.logTokenRevocation(societeId.toString(), userId, actorId, "ROLE_CHANGE");

        // Return role in JWT format (ROLE_ADMIN/ROLE_MANAGER/ROLE_AGENT) for API consistency
        return UserResponse.from(saved, "ROLE_" + newRole);
    }

    @Transactional
    public UserResponse setEnabled(UUID userId, SetEnabledRequest request) {
        User user = findUserInSociete(userId);
        user.setEnabled(request.enabled());
        user.incrementTokenVersion();
        User saved = userRepository.save(user);
        userSecurityCacheService.evict(userId);

        if (!request.enabled()) {
            UUID actorId = resolveActorId();
            UUID societeId = requireSocieteId();
            securityAuditLogger.logTokenRevocation(societeId.toString(), userId, actorId, "ACCOUNT_DISABLED");
        }

        return UserResponse.from(saved);
    }

    @Transactional
    public ResetPasswordResponse resetPassword(UUID userId) {
        User user = findUserInSociete(userId);
        String tempPassword = generateTempPassword();
        user.setPasswordHash(passwordEncoder.encode(tempPassword));
        user.incrementTokenVersion();
        userRepository.save(user);
        userSecurityCacheService.evict(userId);
        return new ResetPasswordResponse(tempPassword);
    }

    private User findUserInSociete(UUID userId) {
        UUID societeId = requireSocieteId();
        // Verify user belongs to this société
        appUserSocieteRepository.findByIdUserIdAndIdSocieteId(userId, societeId)
                .orElseThrow(() -> new UserNotFoundException(userId));
        return userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException(userId));
    }

    private UUID requireSocieteId() {
        UUID societeId = SocieteContext.getSocieteId();
        if (societeId == null) {
            throw new CrossTenantAccessException("Missing société context");
        }
        return societeId;
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

    /** Strips ROLE_ prefix so stored role matches chk_societe_role constraint (ADMIN/MANAGER/AGENT). */
    private static String toSocieteRole(com.yem.hlm.backend.user.domain.UserRole role) {
        String name = role.name();
        return name.startsWith("ROLE_") ? name.substring(5) : name;
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
