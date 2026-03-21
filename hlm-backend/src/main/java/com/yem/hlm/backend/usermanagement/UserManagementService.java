package com.yem.hlm.backend.usermanagement;

import com.yem.hlm.backend.common.dto.PageResponse;
import com.yem.hlm.backend.societe.AppUserSocieteRepository;
import com.yem.hlm.backend.societe.domain.AppUserSociete;
import com.yem.hlm.backend.usermanagement.dto.*;
import com.yem.hlm.backend.usermanagement.event.*;
import com.yem.hlm.backend.usermanagement.exception.BusinessRuleException;
import com.yem.hlm.backend.user.domain.User;
import com.yem.hlm.backend.user.repo.UserRepository;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.persistence.criteria.Join;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static com.yem.hlm.backend.common.error.ErrorCode.*;

@Service
public class UserManagementService {

    private final UserRepository userRepository;
    private final AppUserSocieteRepository appUserSocieteRepository;
    private final ApplicationEventPublisher eventPublisher;

    public UserManagementService(UserRepository userRepository,
                                  AppUserSocieteRepository appUserSocieteRepository,
                                  ApplicationEventPublisher eventPublisher) {
        this.userRepository = userRepository;
        this.appUserSocieteRepository = appUserSocieteRepository;
        this.eventPublisher = eventPublisher;
    }

    // ── listerMembres ──────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public PageResponse<MembreDto> listerMembres(UUID societeId, MembreFilter filter, Pageable pageable) {
        Specification<User> spec = bySociete(societeId);
        if (filter.search() != null) spec = spec.and(searchTerm(filter.search()));
        if (filter.role()   != null) spec = spec.and(hasRole(filter.role(), societeId));
        if (filter.actif()  != null) spec = spec.and(isActif(filter.actif(), societeId));

        Page<User> page = userRepository.findAll(spec, pageable);
        return PageResponse.of(page.map(u -> toMembreDto(u, societeId)));
    }

    // ── getDetail ──────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public MembreDto getDetail(UUID userId, UUID societeId) {
        User user = findMembre(userId, societeId);
        return toMembreDto(user, societeId);
    }

    // ── modifierProfil ─────────────────────────────────────────────────────────

    @Transactional
    public MembreDto modifierProfil(UUID userId, UUID societeId, ModifierUtilisateurRequest req, UUID actorId) {
        User user = findMembre(userId, societeId);

        if (!user.getVersion().equals(req.version()))
            throw new BusinessRuleException(CONCURRENT_UPDATE,
                    "Ce profil a été modifié entre-temps. Rechargez et réessayez.");

        List<String> changedFields = new ArrayList<>();
        if (req.prenom()          != null) { user.setPrenom(req.prenom());                changedFields.add("prenom"); }
        if (req.nomFamille()      != null) { user.setNomFamille(req.nomFamille());        changedFields.add("nomFamille"); }
        if (req.telephone()       != null) { user.setTelephone(req.telephone());          changedFields.add("telephone"); }
        if (req.poste()           != null) { user.setPoste(req.poste());                  changedFields.add("poste"); }
        if (req.langueInterface() != null) { user.setLangueInterface(req.langueInterface()); changedFields.add("langueInterface"); }
        if (req.notesAdmin()      != null) { user.setNotesAdmin(req.notesAdmin());        changedFields.add("notesAdmin"); }
        if (req.notificationsActives() != null) {
            appUserSocieteRepository.findByUserIdAndSocieteId(userId, societeId)
                    .ifPresent(aus -> aus.setNotificationsActives(req.notificationsActives()));
            changedFields.add("notificationsActives");
        }

        User saved = userRepository.save(user);
        eventPublisher.publishEvent(new UserUpdatedEvent(userId, societeId, actorId, changedFields));
        return toMembreDto(saved, societeId);
    }

    // ── changerRole ────────────────────────────────────────────────────────────

    @Transactional
    public MembreDto changerRole(UUID userId, UUID societeId, ChangerRoleRequest req, UUID actorId) {
        User user = findMembre(userId, societeId);

        if (!user.getVersion().equals(req.version()))
            throw new BusinessRuleException(CONCURRENT_UPDATE, "Version obsolète — rechargez.");

        AppUserSociete aus = appUserSocieteRepository
                .findByUserIdAndSocieteId(userId, societeId).orElseThrow();
        String ancienRole = aus.getRole();

        // C9: protect last ADMIN
        if ("ADMIN".equals(ancienRole) && !"ADMIN".equals(req.nouveauRole())) {
            long nbAdmins = appUserSocieteRepository.countBySocieteIdAndRoleAndActifTrue(societeId, "ADMIN");
            if (nbAdmins <= 1)
                throw new BusinessRuleException(DERNIER_ADMIN,
                        "Impossible de modifier le rôle du dernier administrateur de la société.");
        }

        aus.setRole(req.nouveauRole());
        appUserSocieteRepository.save(aus);

        // C5: increment token version to revoke all JWTs
        user.incrementTokenVersion();
        User saved = userRepository.save(user);

        eventPublisher.publishEvent(new UserRoleChangedEvent(userId, societeId, actorId,
                ancienRole, req.nouveauRole(), req.raison()));
        return toMembreDto(saved, societeId);
    }

    // ── retirerMembre ──────────────────────────────────────────────────────────

    @Transactional
    public void retirerMembre(UUID userId, UUID societeId, RetirerUtilisateurRequest req, UUID actorId) {
        User user = findMembre(userId, societeId);

        if (!user.getVersion().equals(req.version()))
            throw new BusinessRuleException(CONCURRENT_UPDATE, "Version obsolète — rechargez.");

        AppUserSociete aus = appUserSocieteRepository
                .findByUserIdAndSocieteId(userId, societeId).orElseThrow();

        // C9: protect last ADMIN
        if ("ADMIN".equals(aus.getRole())) {
            long nbAdmins = appUserSocieteRepository.countBySocieteIdAndRoleAndActifTrue(societeId, "ADMIN");
            if (nbAdmins <= 1)
                throw new BusinessRuleException(DERNIER_ADMIN, "Impossible de retirer le dernier administrateur.");
        }

        aus.setActif(false);
        aus.setDateRetrait(Instant.now());
        aus.setRaisonRetrait(req.raison());
        aus.setRetirePar(userRepository.getReferenceById(actorId));
        appUserSocieteRepository.save(aus);

        // C5: revoke JWTs
        user.incrementTokenVersion();
        userRepository.save(user);

        eventPublisher.publishEvent(new UserRemovedEvent(userId, societeId, actorId, req.raison()));
    }

    // ── debloquerCompte ────────────────────────────────────────────────────────

    @Transactional
    public MembreDto debloquerCompte(UUID userId, UUID societeId, UUID actorId) {
        User user = findMembre(userId, societeId);

        if (!user.isCompteBloque())
            throw new BusinessRuleException(COMPTE_DEJA_DEBLOQUE, "Ce compte n'est pas bloqué.");

        user.setCompteBloque(false);
        user.setCompteBlockeAt(null);
        userRepository.save(user);
        eventPublisher.publishEvent(new UserUnblockedEvent(userId, societeId, actorId));
        return toMembreDto(user, societeId);
    }

    // ── helpers ────────────────────────────────────────────────────────────────

    User findMembre(UUID userId, UUID societeId) {
        appUserSocieteRepository.findByUserIdAndSocieteIdAndActifTrue(userId, societeId)
                .orElseThrow(() -> new BusinessRuleException(MEMBRE_NON_TROUVE,
                        "Utilisateur non membre de cette société : " + userId));
        return userRepository.findById(userId)
                .orElseThrow(() -> new BusinessRuleException(MEMBRE_NON_TROUVE, userId.toString()));
    }

    MembreDto toMembreDto(User user, UUID societeId) {
        AppUserSociete aus = appUserSocieteRepository
                .findByUserIdAndSocieteId(user.getId(), societeId).orElse(null);
        String role   = aus != null ? aus.getRole()     : null;
        boolean actif = aus != null && aus.isActif();
        Instant dateAjout = aus != null ? aus.getDateAjout() : null;
        String statut = aus != null
                ? MembreStatut.compute(user, aus).name()
                : MembreStatut.RETIRE.name();

        return new MembreDto(
                user.getId(),
                user.getEmail(),
                user.getPrenom(),
                user.getNomFamille(),
                user.getNomComplet(),
                user.getTelephone(),
                user.getPoste(),
                role,
                actif,
                user.isEnabled(),
                user.isCompteBloque(),
                user.getDerniereConnexion(),
                dateAjout,
                user.getInvitationEnvoyeeAt(),
                user.getInvitationExpireAt(),
                statut,
                user.getVersion()
        );
    }

    // ── specifications ─────────────────────────────────────────────────────────

    private static Specification<User> bySociete(UUID societeId) {
        return (root, q, cb) -> {
            Join<Object, Object> aus = root.join("societes");
            // "societes" refers to the AppUserSociete collection — but User doesn't have this nav.
            // Use a subquery approach via exists instead.
            return cb.and(
                    cb.equal(aus.get("id").get("societeId"), societeId),
                    cb.isTrue(aus.get("actif"))
            );
        };
    }

    private static Specification<User> searchTerm(String term) {
        String like = "%" + term.toLowerCase() + "%";
        return (root, q, cb) -> cb.or(
                cb.like(cb.lower(root.get("email")),     like),
                cb.like(cb.lower(root.get("prenom")),    like),
                cb.like(cb.lower(root.get("nomFamille")),like),
                cb.like(cb.lower(root.get("poste")),     like)
        );
    }

    private static Specification<User> hasRole(String role, UUID societeId) {
        return (root, q, cb) -> {
            Join<Object, Object> aus = root.join("societes");
            return cb.and(
                    cb.equal(aus.get("id").get("societeId"), societeId),
                    cb.equal(aus.get("role"), role)
            );
        };
    }

    private static Specification<User> isActif(boolean actif, UUID societeId) {
        return (root, q, cb) -> {
            Join<Object, Object> aus = root.join("societes");
            return cb.and(
                    cb.equal(aus.get("id").get("societeId"), societeId),
                    cb.equal(aus.get("actif"), actif)
            );
        };
    }
}
