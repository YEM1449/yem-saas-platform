package com.yem.hlm.backend.societe;

import com.yem.hlm.backend.auth.config.CacheConfig;
import com.yem.hlm.backend.auth.service.JwtProvider;
import com.yem.hlm.backend.common.error.ErrorCode;
import com.yem.hlm.backend.contact.repo.ContactRepository;
import com.yem.hlm.backend.contract.repo.SaleContractRepository;
import com.yem.hlm.backend.project.repo.ProjectRepository;
import com.yem.hlm.backend.property.repo.PropertyRepository;
import com.yem.hlm.backend.societe.api.dto.*;
import com.yem.hlm.backend.societe.event.ImpersonationStartedEvent;
import com.yem.hlm.backend.societe.domain.AppUserSociete;
import com.yem.hlm.backend.societe.domain.AppUserSocieteId;
import com.yem.hlm.backend.societe.domain.Societe;
import com.yem.hlm.backend.societe.event.*;
import com.yem.hlm.backend.user.repo.UserRepository;
import com.yem.hlm.backend.usermanagement.exception.BusinessRuleException;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class SocieteService {

    /** Impersonation token TTL in seconds (1 hour). */
    static final int IMPERSONATION_TTL_SECONDS = 3600;

    private final SocieteRepository societeRepository;
    private final AppUserSocieteRepository appUserSocieteRepository;
    private final UserRepository userRepository;
    private final ContactRepository contactRepository;
    private final PropertyRepository propertyRepository;
    private final ProjectRepository projectRepository;
    private final SaleContractRepository saleContractRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final JwtProvider jwtProvider;

    public SocieteService(SocieteRepository societeRepository,
                          AppUserSocieteRepository appUserSocieteRepository,
                          UserRepository userRepository,
                          ContactRepository contactRepository,
                          PropertyRepository propertyRepository,
                          ProjectRepository projectRepository,
                          SaleContractRepository saleContractRepository,
                          ApplicationEventPublisher eventPublisher,
                          JwtProvider jwtProvider) {
        this.societeRepository = societeRepository;
        this.appUserSocieteRepository = appUserSocieteRepository;
        this.userRepository = userRepository;
        this.contactRepository = contactRepository;
        this.propertyRepository = propertyRepository;
        this.projectRepository = projectRepository;
        this.saleContractRepository = saleContractRepository;
        this.eventPublisher = eventPublisher;
        this.jwtProvider = jwtProvider;
    }

    // ── List / Read ───────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    @Cacheable(CacheConfig.SOCIETES_CACHE)
    public List<SocieteDto> listSocietes() {
        return societeRepository.findAll().stream().map(SocieteDto::from).toList();
    }

    @Transactional(readOnly = true)
    public Page<SocieteDto> listSocietes(SocieteFilter filter, Pageable pageable) {
        return societeRepository
                .findAll(SocieteSpecification.from(filter), pageable)
                .map(SocieteDto::from);
    }

    @Transactional(readOnly = true)
    @Cacheable(value = CacheConfig.SOCIETES_CACHE, key = "#id")
    public SocieteDto getSociete(UUID id) {
        return SocieteDto.from(require(id));
    }

    /** Full view including {@code notesInternes} — SUPER_ADMIN only (R9). */
    @Transactional(readOnly = true)
    public SocieteDetailDto getDetail(UUID id) {
        return SocieteDetailDto.from(require(id));
    }

    @Transactional(readOnly = true)
    public SocieteStatsDto getStats(UUID id) {
        require(id);  // 404 guard
        long totalMembres  = appUserSocieteRepository.countByIdSocieteId(id);
        long membresActifs = appUserSocieteRepository.countBySocieteIdAndActifTrue(id);
        long totalContacts = contactRepository.countBySocieteIdAndDeletedFalse(id);
        long totalBiens    = propertyRepository.countBySocieteIdAndDeletedAtIsNull(id);
        long totalProjets  = projectRepository.countBySocieteId(id);
        long totalContrats = saleContractRepository.countBySocieteId(id);

        Societe s = require(id);
        return new SocieteStatsDto(
                totalMembres, membresActifs,
                totalContacts, totalBiens, totalProjets, totalContrats,
                s.getMaxUtilisateurs(), s.getMaxBiens(), s.getMaxContacts(), s.getMaxProjets()
        );
    }

    @Transactional(readOnly = true)
    public SocieteComplianceDto getCompliance(UUID id) {
        Societe s = require(id);
        return SocieteComplianceDto.from(
                s.getNom() != null && !s.getNom().isBlank(),
                s.getEmailDpo() != null && !s.getEmailDpo().isBlank(),
                s.getAdresseSiege() != null || s.getAdresse() != null,
                s.getNumeroCndp() != null || s.getNumeroCnil() != null,
                s.getDpoNom() != null && !s.getDpoNom().isBlank(),
                s.getBaseJuridiqueDefaut() != null
        );
    }

    // ── Create / Update ───────────────────────────────────────────────────────

    @Transactional
    @CacheEvict(value = CacheConfig.SOCIETES_CACHE, allEntries = true)
    public SocieteDetailDto createSociete(CreateSocieteRequest req, UUID createdByUserId) {
        if (societeRepository.existsByNomIgnoreCase(req.nom())) {
            throw new BusinessRuleException(ErrorCode.SOCIETE_ALREADY_EXISTS,
                    "Une société avec le nom '" + req.nom() + "' existe déjà.");
        }
        Societe s = new Societe(req.nom(), req.pays() != null ? req.pays() : "MA");

        // Legal identity
        if (req.nomCommercial() != null)  s.setNomCommercial(req.nomCommercial());
        if (req.formeJuridique() != null) s.setFormeJuridique(req.formeJuridique());
        if (req.siretIce() != null)       s.setSiretIce(req.siretIce());
        if (req.rc() != null)             s.setRc(req.rc());
        if (req.ifNumber() != null)       s.setIfNumber(req.ifNumber());
        if (req.patente() != null)        s.setPatente(req.patente());
        if (req.tvaNumber() != null)      s.setTvaNumber(req.tvaNumber());
        if (req.cnssNumber() != null)     s.setCnssNumber(req.cnssNumber());

        // Location
        if (req.adresseSiege() != null)   s.setAdresseSiege(req.adresseSiege());
        if (req.ville() != null)          s.setVille(req.ville());
        if (req.codePostal() != null)     s.setCodePostal(req.codePostal());
        if (req.region() != null)         s.setRegion(req.region());
        if (req.telephone() != null)      s.setTelephone(req.telephone());
        if (req.telephone2() != null)     s.setTelephone2(req.telephone2());
        if (req.emailContact() != null)   s.setEmailContact(req.emailContact());
        if (req.siteWeb() != null)        s.setSiteWeb(req.siteWeb());

        // RGPD
        if (req.emailDpo() != null)             s.setEmailDpo(req.emailDpo());
        if (req.dpoNom() != null)               s.setDpoNom(req.dpoNom());
        if (req.telephoneDpo() != null)         s.setTelephoneDpo(req.telephoneDpo());
        if (req.baseJuridiqueDefaut() != null)  s.setBaseJuridiqueDefaut(req.baseJuridiqueDefaut());

        // Licensing
        if (req.numeroAgrement() != null) s.setNumeroAgrement(req.numeroAgrement());
        if (req.typeActivite() != null)   s.setTypeActivite(req.typeActivite());

        // Branding
        if (req.logoUrl() != null)        s.setLogoUrl(req.logoUrl());
        if (req.couleurPrimaire() != null) s.setCouleurPrimaire(req.couleurPrimaire());
        if (req.langueDefaut() != null)   s.setLangueDefaut(req.langueDefaut());
        if (req.devise() != null)         s.setDevise(req.devise());

        // Subscription
        if (req.planAbonnement() != null) s.setPlanAbonnement(req.planAbonnement());

        // Admin (SUPER_ADMIN only)
        if (req.notesInternes() != null)  s.setNotesInternes(req.notesInternes());

        if (createdByUserId != null) {
            userRepository.findById(createdByUserId).ifPresent(s::setCreatedBy);
        }

        Societe saved = societeRepository.save(s);
        eventPublisher.publishEvent(new SocieteCreatedEvent(saved.getId(), createdByUserId, saved.getNom()));
        return SocieteDetailDto.from(saved);
    }

    @Transactional
    @CacheEvict(value = CacheConfig.SOCIETES_CACHE, allEntries = true)
    public SocieteDetailDto updateSociete(UUID id, UpdateSocieteRequest req, UUID actorId) {
        Societe s = require(id);

        // Core
        if (req.nom() != null)    s.setNom(req.nom());
        if (req.pays() != null)   s.setPays(req.pays());

        // Legal identity
        if (req.nomCommercial() != null)  s.setNomCommercial(req.nomCommercial());
        if (req.formeJuridique() != null) s.setFormeJuridique(req.formeJuridique());
        if (req.capitalSocial() != null)  s.setCapitalSocial(req.capitalSocial());
        if (req.siretIce() != null)       s.setSiretIce(req.siretIce());
        if (req.rc() != null)             s.setRc(req.rc());
        if (req.ifNumber() != null)       s.setIfNumber(req.ifNumber());
        if (req.patente() != null)        s.setPatente(req.patente());
        if (req.tvaNumber() != null)      s.setTvaNumber(req.tvaNumber());
        if (req.cnssNumber() != null)     s.setCnssNumber(req.cnssNumber());

        // Location
        if (req.adresse() != null)        s.setAdresse(req.adresse());
        if (req.adresseSiege() != null)   s.setAdresseSiege(req.adresseSiege());
        if (req.ville() != null)          s.setVille(req.ville());
        if (req.codePostal() != null)     s.setCodePostal(req.codePostal());
        if (req.region() != null)         s.setRegion(req.region());
        if (req.telephone() != null)      s.setTelephone(req.telephone());
        if (req.telephone2() != null)     s.setTelephone2(req.telephone2());
        if (req.emailContact() != null)   s.setEmailContact(req.emailContact());
        if (req.siteWeb() != null)        s.setSiteWeb(req.siteWeb());
        if (req.linkedinUrl() != null)    s.setLinkedinUrl(req.linkedinUrl());

        // RGPD
        if (req.emailDpo() != null)             s.setEmailDpo(req.emailDpo());
        if (req.dpoNom() != null)               s.setDpoNom(req.dpoNom());
        if (req.telephoneDpo() != null)         s.setTelephoneDpo(req.telephoneDpo());
        if (req.numeroCndp() != null)           s.setNumeroCndp(req.numeroCndp());
        if (req.numeroCnil() != null)           s.setNumeroCnil(req.numeroCnil());
        if (req.dateDeclarationCndp() != null)  s.setDateDeclarationCndp(req.dateDeclarationCndp());
        if (req.dateDeclarationCnil() != null)  s.setDateDeclarationCnil(req.dateDeclarationCnil());
        if (req.baseJuridiqueDefaut() != null)  s.setBaseJuridiqueDefaut(req.baseJuridiqueDefaut());
        if (req.dureeRetentionJours() != null)  s.setDureeRetentionJours(req.dureeRetentionJours());

        // Licensing
        if (req.numeroAgrement() != null)         s.setNumeroAgrement(req.numeroAgrement());
        if (req.carteProfessionnelle() != null)   s.setCarteProfessionnelle(req.carteProfessionnelle());
        if (req.caisseGarantie() != null)         s.setCaisseGarantie(req.caisseGarantie());
        if (req.assuranceRc() != null)            s.setAssuranceRc(req.assuranceRc());
        if (req.dateAgrement() != null)           s.setDateAgrement(req.dateAgrement());
        if (req.dateExpirationAgrement() != null) s.setDateExpirationAgrement(req.dateExpirationAgrement());
        if (req.typeActivite() != null)           s.setTypeActivite(req.typeActivite());
        if (req.zonesIntervention() != null)      s.setZonesIntervention(req.zonesIntervention());

        // Branding
        if (req.logoUrl() != null)          s.setLogoUrl(req.logoUrl());
        if (req.couleurPrimaire() != null)  s.setCouleurPrimaire(req.couleurPrimaire());
        if (req.couleurSecondaire() != null) s.setCouleurSecondaire(req.couleurSecondaire());
        if (req.langueDefaut() != null)     s.setLangueDefaut(req.langueDefaut());
        if (req.devise() != null)           s.setDevise(req.devise());
        if (req.fuseauHoraire() != null)    s.setFuseauHoraire(req.fuseauHoraire());
        if (req.formatDate() != null)       s.setFormatDate(req.formatDate());
        if (req.mentionsLegales() != null)  s.setMentionsLegales(req.mentionsLegales());

        // Subscription (SUPER_ADMIN only)
        if (req.planAbonnement() != null)       s.setPlanAbonnement(req.planAbonnement());
        if (req.maxUtilisateurs() != null)      s.setMaxUtilisateurs(req.maxUtilisateurs());
        if (req.maxBiens() != null)             s.setMaxBiens(req.maxBiens());
        if (req.maxContacts() != null)          s.setMaxContacts(req.maxContacts());
        if (req.maxProjets() != null)           s.setMaxProjets(req.maxProjets());
        if (req.dateDebutAbonnement() != null)  s.setDateDebutAbonnement(req.dateDebutAbonnement());
        if (req.dateFinAbonnement() != null)    s.setDateFinAbonnement(req.dateFinAbonnement());
        if (req.periodeEssai() != null)         s.setPeriodeEssai(req.periodeEssai());

        // Admin notes (SUPER_ADMIN only)
        if (req.notesInternes() != null) s.setNotesInternes(req.notesInternes());

        Societe saved = societeRepository.save(s);
        eventPublisher.publishEvent(new SocieteUpdatedEvent(saved.getId(), actorId));
        return SocieteDetailDto.from(saved);
    }

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    /**
     * Deactivate a company. R6: atomically revokes all member JWTs by
     * incrementing tokenVersion for every active member.
     */
    @Transactional
    @CacheEvict(value = CacheConfig.SOCIETES_CACHE, allEntries = true)
    public void desactiver(UUID id, DesactiverRequest req, UUID actorId) {
        Societe s = require(id);
        if (!s.isActif()) {
            throw new BusinessRuleException(ErrorCode.SOCIETE_INACTIVE,
                    "La société est déjà inactive.");
        }
        s.setActif(false);
        s.setDateSuspension(Instant.now());
        s.setRaisonSuspension(req.raison());
        societeRepository.save(s);

        // R6 — revoke all member JWTs atomically
        appUserSocieteRepository.findByIdSocieteId(id).forEach(aus -> {
            if (aus.isActif()) {
                aus.setActif(false);
                aus.setDateRetrait(s.getDateSuspension());
                appUserSocieteRepository.save(aus);
                userRepository.findById(aus.getUserId()).ifPresent(user -> {
                    user.incrementTokenVersion();
                    userRepository.save(user);
                });
            }
        });

        eventPublisher.publishEvent(new SocieteDesactiveeEvent(id, actorId, req.raison()));
    }

    @Transactional
    @CacheEvict(value = CacheConfig.SOCIETES_CACHE, allEntries = true)
    public void reactiver(UUID id, UUID actorId) {
        Societe s = require(id);
        if (s.isActif()) {
            throw new BusinessRuleException(ErrorCode.SOCIETE_INACTIVE,
                    "La société est déjà active.");
        }
        s.setActif(true);
        Instant suspensionDate = s.getDateSuspension();
        if (suspensionDate != null) {
            appUserSocieteRepository.findByIdSocieteId(id).forEach(aus -> {
                if (!aus.isActif() && aus.getDateRetrait() != null
                        && !aus.getDateRetrait().isBefore(suspensionDate.minusSeconds(5))) {
                    aus.setActif(true);
                    aus.setDateRetrait(null);
                    appUserSocieteRepository.save(aus);
                }
            });
        }
        s.setDateSuspension(null);
        s.setRaisonSuspension(null);
        societeRepository.save(s);
        eventPublisher.publishEvent(new SocieteReactiveeEvent(id, actorId));
    }

    // ── Membres ───────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<MembreSocieteDto> listMembres(UUID societeId) {
        require(societeId);
        return appUserSocieteRepository.findByIdSocieteId(societeId).stream()
                .map(aus -> {
                    var user = userRepository.findById(aus.getUserId()).orElse(null);
                    if (user == null) return MembreSocieteDto.from(aus);
                    return MembreSocieteDto.from(aus, user.getPrenom(), user.getNomFamille(), user.getEmail());
                })
                .toList();
    }

    @Transactional
    public MembreSocieteDto addMembre(UUID societeId, AddMembreRequest req, UUID actorId) {
        Societe s = require(societeId);

        // Quota check
        if (s.getMaxUtilisateurs() != null) {
            long current = appUserSocieteRepository.countBySocieteIdAndActifTrue(societeId);
            if (current >= s.getMaxUtilisateurs()) {
                throw new BusinessRuleException(ErrorCode.QUOTA_UTILISATEURS_ATTEINT,
                        "Le quota d'utilisateurs (" + s.getMaxUtilisateurs() + ") est atteint.");
            }
        }

        // Duplicate check
        AppUserSocieteId pk = new AppUserSocieteId(req.userId(), societeId);
        appUserSocieteRepository.findById(pk).ifPresent(existing -> {
            if (existing.isActif()) {
                throw new BusinessRuleException(ErrorCode.MEMBRE_DEJA_EXISTANT,
                        "L'utilisateur est déjà membre actif de cette société.");
            }
        });

        // Verify user exists
        var user = userRepository.findById(req.userId())
                .orElseThrow(() -> new BusinessRuleException(ErrorCode.USER_NOT_FOUND,
                        "Utilisateur introuvable: " + req.userId()));

        AppUserSociete aus = new AppUserSociete(pk, req.role());
        AppUserSociete saved = appUserSocieteRepository.save(aus);

        // R4 — bump token version
        user.incrementTokenVersion();
        userRepository.save(user);

        eventPublisher.publishEvent(new MembreAjouteEvent(societeId, actorId, req.userId(), req.role()));
        return MembreSocieteDto.from(saved, user.getPrenom(), user.getNomFamille(), user.getEmail());
    }

    @Transactional
    public MembreSocieteDto updateMembreRole(UUID societeId, UUID userId,
                                             UpdateMembreRoleRequest req, UUID actorId) {
        require(societeId);
        AppUserSocieteId pk = new AppUserSocieteId(userId, societeId);
        AppUserSociete aus = appUserSocieteRepository.findById(pk)
                .orElseThrow(() -> new BusinessRuleException(ErrorCode.MEMBRE_NON_TROUVE,
                        "Membre introuvable dans cette société."));

        // R10 — cannot demote the last ADMIN
        if ("ADMIN".equals(aus.getRole()) && !"ADMIN".equals(req.nouveauRole())) {
            long adminCount = appUserSocieteRepository.countBySocieteIdAndRoleAndActifTrue(societeId, "ADMIN");
            if (adminCount <= 1) {
                throw new BusinessRuleException(ErrorCode.DERNIER_ADMIN,
                        "Impossible de rétrograder le dernier administrateur de la société.");
            }
        }

        String ancienRole = aus.getRole();
        aus.setRole(req.nouveauRole());
        AppUserSociete saved = appUserSocieteRepository.save(aus);

        // R4 — bump token version so new role takes effect immediately
        userRepository.findById(userId).ifPresent(user -> {
            user.incrementTokenVersion();
            userRepository.save(user);
        });

        eventPublisher.publishEvent(
                new MembreRoleModifieEvent(societeId, actorId, userId, ancienRole, req.nouveauRole()));

        var user = userRepository.findById(userId).orElse(null);
        if (user == null) return MembreSocieteDto.from(saved);
        return MembreSocieteDto.from(saved, user.getPrenom(), user.getNomFamille(), user.getEmail());
    }

    @Transactional
    public void removeMembre(UUID societeId, UUID userId, UUID actorId) {
        require(societeId);
        AppUserSocieteId pk = new AppUserSocieteId(userId, societeId);
        AppUserSociete aus = appUserSocieteRepository.findById(pk)
                .orElseThrow(() -> new BusinessRuleException(ErrorCode.MEMBRE_NON_TROUVE,
                        "Membre introuvable dans cette société."));

        // R10 — cannot remove the last ADMIN
        if ("ADMIN".equals(aus.getRole())) {
            long adminCount = appUserSocieteRepository.countBySocieteIdAndRoleAndActifTrue(societeId, "ADMIN");
            if (adminCount <= 1) {
                throw new BusinessRuleException(ErrorCode.DERNIER_ADMIN,
                        "Impossible de retirer le dernier administrateur de la société.");
            }
        }

        aus.setActif(false);
        aus.setDateRetrait(Instant.now());
        appUserSocieteRepository.save(aus);

        // R4 — revoke JWT
        userRepository.findById(userId).ifPresent(user -> {
            user.incrementTokenVersion();
            userRepository.save(user);
        });

        eventPublisher.publishEvent(new MembreRetireEvent(societeId, actorId, userId));
    }

    // ── Impersonation (SA-7) ─────────────────────────────────────────────────

    /**
     * Generates a short-lived impersonation JWT for the target user in the given société.
     * The SUPER_ADMIN (actorId) is recorded in the {@code imp} claim (R7).
     * The token inherits the target user's actual role in that société.
     */
    @Transactional(readOnly = true)
    public ImpersonateResponse impersonate(UUID societeId, UUID targetUserId, UUID superAdminId) {
        require(societeId);

        AppUserSocieteId pk = new AppUserSocieteId(targetUserId, societeId);
        AppUserSociete membership = appUserSocieteRepository.findById(pk)
                .filter(AppUserSociete::isActif)
                .orElseThrow(() -> new BusinessRuleException(ErrorCode.MEMBRE_NON_TROUVE,
                        "Utilisateur non membre actif de cette société."));

        var user = userRepository.findById(targetUserId)
                .orElseThrow(() -> new BusinessRuleException(ErrorCode.USER_NOT_FOUND,
                        "Utilisateur introuvable: " + targetUserId));

        String role = "ROLE_" + membership.getRole();
        String token = jwtProvider.generateImpersonation(
                targetUserId, societeId, role,
                user.getTokenVersion(), superAdminId, IMPERSONATION_TTL_SECONDS);

        eventPublisher.publishEvent(
                new ImpersonationStartedEvent(societeId, superAdminId, targetUserId, role));

        return new ImpersonateResponse(token, targetUserId, societeId,
                user.getEmail(), role, IMPERSONATION_TTL_SECONDS);
    }

    // ── Compatibility helpers (used by old SocieteController until SA-6 rewrites it) ──

    /** @deprecated Use {@link #listMembres(UUID)} instead. */
    @Transactional
    public AppUserSocieteDto addUserToSociete(UUID societeId, AddUserRequest req) {
        require(societeId);
        AppUserSocieteId pk = new AppUserSocieteId(req.userId(), societeId);
        if (appUserSocieteRepository.existsById(pk)) {
            throw new BusinessRuleException(ErrorCode.MEMBRE_DEJA_EXISTANT, "USER_ALREADY_IN_SOCIETE");
        }
        AppUserSociete aus = new AppUserSociete(pk, req.role());
        return AppUserSocieteDto.from(appUserSocieteRepository.save(aus));
    }

    /** @deprecated Use {@link #removeMembre(UUID, UUID, UUID)} instead. */
    @Transactional
    public void removeUserFromSociete(UUID societeId, UUID userId) {
        AppUserSocieteId pk = new AppUserSocieteId(userId, societeId);
        AppUserSociete aus = appUserSocieteRepository.findById(pk)
                .orElseThrow(() -> new BusinessRuleException(ErrorCode.MEMBRE_NON_TROUVE, "MEMBERSHIP_NOT_FOUND"));
        aus.setActif(false);
        appUserSocieteRepository.save(aus);

        userRepository.findById(userId).ifPresent(user -> {
            user.incrementTokenVersion();
            userRepository.save(user);
        });
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private Societe require(UUID id) {
        return societeRepository.findById(id)
                .orElseThrow(() -> new BusinessRuleException(ErrorCode.SOCIETE_NOT_FOUND,
                        "Société introuvable: " + id));
    }
}
