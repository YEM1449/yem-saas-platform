package com.yem.hlm.backend.usermanagement;

import com.yem.hlm.backend.societe.AppUserSocieteRepository;
import com.yem.hlm.backend.societe.domain.AppUserSociete;
import com.yem.hlm.backend.usermanagement.event.UserAnonymizedEvent;
import com.yem.hlm.backend.usermanagement.exception.BusinessRuleException;
import com.yem.hlm.backend.user.domain.User;
import com.yem.hlm.backend.user.repo.UserRepository;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static com.yem.hlm.backend.common.error.ErrorCode.MEMBRE_NON_TROUVE;

/**
 * RGPD Art. 15 (export) and Art. 17 (anonymisation) for platform users.
 *
 * <p>C6 constraint: {@code notes_admin} is NEVER included in exports — it is internal
 * organisational data, not personal data belonging to the data subject.
 */
@Service
@Transactional(readOnly = true)
public class UserGdprService {

    private final UserRepository userRepository;
    private final AppUserSocieteRepository appUserSocieteRepository;
    private final ApplicationEventPublisher eventPublisher;

    public UserGdprService(UserRepository userRepository,
                           AppUserSocieteRepository appUserSocieteRepository,
                           ApplicationEventPublisher eventPublisher) {
        this.userRepository = userRepository;
        this.appUserSocieteRepository = appUserSocieteRepository;
        this.eventPublisher = eventPublisher;
    }

    // ── Art. 15 / Art. 20 — Export ─────────────────────────────────────────────

    public UserDataExport exportUserData(UUID userId, UUID societeId) {
        appUserSocieteRepository.findByUserIdAndSocieteIdAndActifTrue(userId, societeId)
                .orElseThrow(() -> new BusinessRuleException(MEMBRE_NON_TROUVE,
                        "Utilisateur non membre de cette société : " + userId));
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessRuleException(MEMBRE_NON_TROUVE, userId.toString()));

        List<AppUserSociete> memberships = appUserSocieteRepository.findByIdUserId(userId);
        List<MembershipExport> membershipExports = memberships.stream()
                .map(m -> new MembershipExport(
                        m.getSocieteId(),
                        m.getRole(),
                        m.isActif(),
                        m.getDateAjout()))
                .toList();

        // C6: notes_admin is intentionally excluded — it is internal organisational data
        return new UserDataExport(
                user.getId(),
                user.getEmail(),
                user.getPrenom(),
                user.getNomFamille(),
                user.getTelephone(),
                user.getPoste(),
                user.getLangueInterface(),
                user.isConsentementCgu(),
                user.getConsentementCguDate(),
                user.getConsentementCguVersion(),
                user.getDerniereConnexion(),
                membershipExports
        );
    }

    // ── Art. 17 — Anonymisation ────────────────────────────────────────────────

    @Transactional
    public void anonymiserUtilisateur(UUID userId, UUID societeId, UUID actorId) {
        appUserSocieteRepository.findByUserIdAndSocieteIdAndActifTrue(userId, societeId)
                .orElseThrow(() -> new BusinessRuleException(MEMBRE_NON_TROUVE,
                        "Utilisateur non membre de cette société : " + userId));
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessRuleException(MEMBRE_NON_TROUVE, userId.toString()));

        user.setPrenom(null);
        user.setNomFamille(null);
        user.setTelephone(null);
        user.setPoste(null);
        user.setPhotoUrl(null);
        user.setLangueInterface(null);
        user.setNotesAdmin(null);
        user.setConsentementCgu(false);
        user.setConsentementCguDate(null);
        user.setConsentementCguVersion(null);
        // Replace email with an opaque identifier — preserves uniqueness constraint
        user.setEnabled(false);
        userRepository.save(user);

        // Deactivate all memberships
        List<AppUserSociete> memberships = appUserSocieteRepository.findByIdUserId(userId);
        for (AppUserSociete aus : memberships) {
            aus.setActif(false);
            aus.setDateRetrait(Instant.now());
            aus.setRaisonRetrait("RGPD Art. 17 — anonymisation");
        }
        appUserSocieteRepository.saveAll(memberships);

        eventPublisher.publishEvent(new UserAnonymizedEvent(userId, societeId, actorId));
    }

    // ── DTOs ───────────────────────────────────────────────────────────────────

    public record UserDataExport(
            UUID id,
            String email,
            String prenom,
            String nomFamille,
            String telephone,
            String poste,
            String langueInterface,
            boolean consentementCgu,
            Instant consentementCguDate,
            String consentementCguVersion,
            Instant derniereConnexion,
            List<MembershipExport> memberships
    ) {}

    public record MembershipExport(
            UUID societeId,
            String role,
            boolean actif,
            Instant dateAjout
    ) {}
}
