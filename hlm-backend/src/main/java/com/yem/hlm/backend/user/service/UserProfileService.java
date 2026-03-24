package com.yem.hlm.backend.user.service;

import com.yem.hlm.backend.auth.service.UserSecurityCacheService;
import com.yem.hlm.backend.societe.AppUserSocieteRepository;
import com.yem.hlm.backend.societe.SocieteContext;
import com.yem.hlm.backend.user.api.dto.UpdateProfileRequest;
import com.yem.hlm.backend.user.api.dto.UserProfileResponse;
import com.yem.hlm.backend.user.domain.User;
import com.yem.hlm.backend.user.repo.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Self-service profile operations for the currently authenticated user.
 * <p>
 * These methods scope exclusively to {@code SocieteContext.getUserId()} — users
 * can only read and update their own profile.
 */
@Service
public class UserProfileService {

    private final UserRepository userRepository;
    private final AppUserSocieteRepository appUserSocieteRepository;
    private final UserSecurityCacheService userSecurityCacheService;

    public UserProfileService(UserRepository userRepository,
                               AppUserSocieteRepository appUserSocieteRepository,
                               UserSecurityCacheService userSecurityCacheService) {
        this.userRepository = userRepository;
        this.appUserSocieteRepository = appUserSocieteRepository;
        this.userSecurityCacheService = userSecurityCacheService;
    }

    /**
     * Returns the profile of the currently authenticated user.
     */
    @Transactional(readOnly = true)
    public UserProfileResponse getProfile() {
        UUID userId    = SocieteContext.getUserId();
        UUID societeId = SocieteContext.getSocieteId();
        if (userId == null) throw new UserNotFoundException(null);
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException(userId));
        String role = resolveRole(userId, societeId);
        return UserProfileResponse.from(user, role);
    }

    /**
     * Applies the supplied (non-null) profile fields to the authenticated user.
     * Fields left {@code null} in the request are not touched.
     */
    @Transactional
    public UserProfileResponse updateProfile(UpdateProfileRequest request) {
        UUID userId    = SocieteContext.getUserId();
        UUID societeId = SocieteContext.getSocieteId();
        if (userId == null) throw new UserNotFoundException(null);
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException(userId));

        if (request.prenom()          != null) user.setPrenom(request.prenom());
        if (request.nomFamille()       != null) user.setNomFamille(request.nomFamille());
        if (request.telephone()        != null) user.setTelephone(request.telephone());
        if (request.poste()            != null) user.setPoste(request.poste());
        if (request.langueInterface()  != null) user.setLangueInterface(request.langueInterface());

        userRepository.save(user);
        userSecurityCacheService.evict(userId);

        String role = resolveRole(userId, societeId);
        return UserProfileResponse.from(user, role);
    }

    // ── private ───────────────────────────────────────────────────────────────

    private String resolveRole(UUID userId, UUID societeId) {
        if (societeId == null) {
            return SocieteContext.getRole();
        }
        return appUserSocieteRepository
                .findByUserIdAndSocieteIdAndActifTrue(userId, societeId)
                .map(aus -> "ROLE_" + aus.getRole())
                .orElse(SocieteContext.getRole());
    }
}
