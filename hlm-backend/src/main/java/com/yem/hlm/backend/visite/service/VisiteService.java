package com.yem.hlm.backend.visite.service;

import com.yem.hlm.backend.contact.domain.Contact;
import com.yem.hlm.backend.contact.repo.ContactRepository;
import com.yem.hlm.backend.project.repo.ProjectRepository;
import com.yem.hlm.backend.property.repo.PropertyRepository;
import com.yem.hlm.backend.societe.AppUserSocieteRepository;
import com.yem.hlm.backend.societe.SocieteContextHelper;
import com.yem.hlm.backend.user.domain.User;
import com.yem.hlm.backend.user.repo.UserRepository;
import com.yem.hlm.backend.visite.api.dto.CompteRenduRequest;
import com.yem.hlm.backend.visite.api.dto.CreateVisiteRequest;
import com.yem.hlm.backend.visite.api.dto.UpdateVisiteRequest;
import com.yem.hlm.backend.visite.api.dto.VisiteResponse;
import com.yem.hlm.backend.visite.domain.*;
import com.yem.hlm.backend.visite.repo.VisiteRappelRepository;
import com.yem.hlm.backend.visite.repo.VisiteRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Business logic for the Visites module (RG-V01..RG-V10).
 *
 * <p>Every operation is société-scoped via {@link SocieteContextHelper#requireSocieteId()}.
 * AGENT may only act on their own visites; MANAGER/ADMIN act on the whole société (RG-V04).
 * Conflict detection (RG-V05) runs on create and reschedule. Reaching {@code REALISEE}
 * requires a compte-rendu (RG-V06). Persistent reminders are created here (RG-V07) and sent
 * by the {@code @Scheduled} {@code RappelVisiteJob} — never by an in-memory scheduler.
 */
@Service
public class VisiteService {

    private static final Logger log = LoggerFactory.getLogger(VisiteService.class);

    /** Widest realistic visit duration — bounds the conflict-candidate window. */
    private static final Duration FENETRE_CONFLIT = Duration.ofHours(24);
    private static final Duration RAPPEL_H24 = Duration.ofHours(24);
    private static final Duration RAPPEL_H1 = Duration.ofHours(1);
    private static final int DUREE_DEFAUT = 30;

    private final VisiteRepository visiteRepo;
    private final VisiteRappelRepository rappelRepo;
    private final ContactRepository contactRepo;
    private final UserRepository userRepo;
    private final AppUserSocieteRepository membershipRepo;
    private final PropertyRepository propertyRepo;
    private final ProjectRepository projectRepo;
    private final SocieteContextHelper societeCtx;
    private final VisiteEmailService emailService;

    public VisiteService(VisiteRepository visiteRepo,
                         VisiteRappelRepository rappelRepo,
                         ContactRepository contactRepo,
                         UserRepository userRepo,
                         AppUserSocieteRepository membershipRepo,
                         PropertyRepository propertyRepo,
                         ProjectRepository projectRepo,
                         SocieteContextHelper societeCtx,
                         VisiteEmailService emailService) {
        this.visiteRepo = visiteRepo;
        this.rappelRepo = rappelRepo;
        this.contactRepo = contactRepo;
        this.userRepo = userRepo;
        this.membershipRepo = membershipRepo;
        this.propertyRepo = propertyRepo;
        this.projectRepo = projectRepo;
        this.societeCtx = societeCtx;
        this.emailService = emailService;
    }

    // =========================================================================
    // Commands
    // =========================================================================

    @Transactional
    public VisiteResponse create(CreateVisiteRequest req) {
        UUID societeId = societeCtx.requireSocieteId();
        UUID actorId = societeCtx.requireUserId();

        UUID agentId = resolveAgentId(req.agentId(), societeId, actorId);
        User agent = userRepo.findById(agentId)
                .orElseThrow(() -> new IllegalArgumentException("Agent introuvable : " + agentId));
        Contact contact = contactRepo.findBySocieteIdAndId(societeId, req.contactId())
                .orElseThrow(() -> new com.yem.hlm.backend.contact.service.ContactNotFoundException(req.contactId()));

        int duree = (req.dureeMinutes() == null || req.dureeMinutes() <= 0) ? DUREE_DEFAUT : req.dureeMinutes();
        Instant fin = req.dateHeure().plusSeconds(duree * 60L);
        verifierConflit(societeId, agentId, req.dateHeure(), fin, null, req.override());
        validerActifsLies(societeId, req.propertyId(), req.projectId());

        Visite visite = new Visite(societeId, agent, contact, req.dateHeure(), duree, req.type(), actorId);
        visite.setPropertyId(req.propertyId());
        visite.setProjectId(req.projectId());
        visite.setLieu(req.lieu());
        visite = visiteRepo.save(visite);

        planifierRappels(visite, contact);
        log.info("Visite {} créée pour agent {} le {}", visite.getId(), agentId, req.dateHeure());
        return VisiteResponse.from(visite);
    }

    @Transactional
    public VisiteResponse update(UUID id, UpdateVisiteRequest req) {
        Visite visite = chargerAccessible(id);
        if (visite.getStatut().isTerminal()) {
            throw new InvalidVisiteTransitionException(visite.getStatut(), visite.getStatut());
        }
        int duree = (req.dureeMinutes() == null || req.dureeMinutes() <= 0) ? DUREE_DEFAUT : req.dureeMinutes();
        Instant fin = req.dateHeure().plusSeconds(duree * 60L);
        verifierConflit(visite.getSocieteId(), visite.getAgent().getId(),
                req.dateHeure(), fin, visite.getId(), req.override());

        boolean dateChanged = !req.dateHeure().equals(visite.getDateHeure()) || duree != visite.getDureeMinutes();
        visite.setDateHeure(req.dateHeure());
        visite.setDureeMinutes(duree);
        visite.setType(req.type());
        visite.setLieu(req.lieu());
        validerActifsLies(visite.getSocieteId(), req.propertyId(), req.projectId());
        visite.setPropertyId(req.propertyId());
        visite.setProjectId(req.projectId());
        visite.setUpdatedAt(Instant.now());

        if (dateChanged) {
            annulerRappelsEnAttente(visite.getId());
            planifierRappels(visite, visite.getContact());
        }
        return VisiteResponse.from(visite);
    }

    @Transactional
    public VisiteResponse confirmer(UUID id) {
        Visite visite = chargerAccessible(id);
        transition(visite, StatutVisite.CONFIRMEE);
        visite.setUpdatedAt(Instant.now());
        return VisiteResponse.from(visite);
    }

    @Transactional
    public VisiteResponse marquerNoShow(UUID id) {
        Visite visite = chargerAccessible(id);
        transition(visite, StatutVisite.NO_SHOW);
        annulerRappelsEnAttente(visite.getId());
        visite.setUpdatedAt(Instant.now());
        return VisiteResponse.from(visite);
    }

    @Transactional
    public VisiteResponse annuler(UUID id, String raison) {
        Visite visite = chargerAccessible(id);
        boolean etaitConfirmee = visite.getStatut() == StatutVisite.CONFIRMEE;
        transition(visite, StatutVisite.ANNULEE);
        visite.setAnnulationRaison(raison);
        visite.setUpdatedAt(Instant.now());
        annulerRappelsEnAttente(visite.getId());
        // RG-V08 — notify the prospect only when the visite was already CONFIRMEE.
        if (etaitConfirmee) {
            try {
                emailService.envoyerAnnulation(visite, raison);
            } catch (RuntimeException e) {
                log.warn("Échec envoi email d'annulation pour visite {}", visite.getId(), e);
            }
        }
        return VisiteResponse.from(visite);
    }

    /** RG-V06 — recording a compte-rendu transitions the visite to REALISEE. */
    @Transactional
    public VisiteResponse enregistrerCompteRendu(UUID id, CompteRenduRequest req) {
        Visite visite = chargerAccessible(id);
        if (req.compteRendu() == null || req.compteRendu().isBlank() || req.resultat() == null) {
            throw new CompteRenduRequisException();
        }
        transition(visite, StatutVisite.REALISEE);
        visite.setCompteRendu(req.compteRendu());
        visite.setResultat(req.resultat());
        visite.setUpdatedAt(Instant.now());
        annulerRappelsEnAttente(visite.getId());
        return VisiteResponse.from(visite);
    }

    /** RG-V06 — link the visite to the Vente created from its OPPORTUNITE_CREEE outcome (P5-T2). */
    @Transactional
    public VisiteResponse lierVente(UUID id, UUID venteId) {
        Visite visite = chargerAccessible(id);
        visite.setVenteId(venteId);
        visite.setUpdatedAt(Instant.now());
        return VisiteResponse.from(visite);
    }

    // =========================================================================
    // Queries
    // =========================================================================

    /**
     * iCalendar (.ics) representation of a visite for "add to my calendar" (P5-T4).
     * Times are emitted in UTC (basic format with {@code Z}), which every calendar app renders
     * back into the viewer's local zone — unambiguous and avoids shipping a VTIMEZONE block.
     */
    @Transactional(readOnly = true)
    public String genererIcs(UUID id) {
        Visite v = chargerAccessible(id);
        Instant fin = v.getDateHeure().plus(Duration.ofMinutes(v.getDureeMinutes()));
        java.time.format.DateTimeFormatter fmt = java.time.format.DateTimeFormatter
                .ofPattern("yyyyMMdd'T'HHmmss'Z'").withZone(java.time.ZoneOffset.UTC);
        String contactNom = v.getContact() == null ? "Visite" : v.getContact().getFullName();
        String desc = "Type : " + v.getType()
                + (v.getLieu() == null ? "" : "\\nLieu : " + escapeIcs(v.getLieu()));
        return String.join("\r\n",
                "BEGIN:VCALENDAR",
                "VERSION:2.0",
                "PRODID:-//YEM HLM//Visites//FR",
                "CALSCALE:GREGORIAN",
                "METHOD:PUBLISH",
                "BEGIN:VEVENT",
                "UID:visite-" + v.getId() + "@yem-hlm",
                "DTSTAMP:" + fmt.format(Instant.now()),
                "DTSTART:" + fmt.format(v.getDateHeure()),
                "DTEND:" + fmt.format(fin),
                "SUMMARY:" + escapeIcs("Visite — " + contactNom),
                "DESCRIPTION:" + desc,
                (v.getLieu() == null ? "" : "LOCATION:" + escapeIcs(v.getLieu())),
                "STATUS:" + (v.getStatut() == StatutVisite.ANNULEE ? "CANCELLED" : "CONFIRMED"),
                "END:VEVENT",
                "END:VCALENDAR") + "\r\n";
    }

    /** RFC 5545 text escaping for SUMMARY/LOCATION/DESCRIPTION values. */
    private static String escapeIcs(String s) {
        return s.replace("\\", "\\\\").replace(";", "\\;").replace(",", "\\,").replace("\n", "\\n");
    }

    @Transactional(readOnly = true)
    public VisiteResponse getById(UUID id) {
        return VisiteResponse.from(chargerAccessible(id));
    }

    /**
     * Agenda over a window (RG-V04). AGENT is forced to their own visites regardless of the
     * {@code agentId} filter; MANAGER/ADMIN may filter by agent or see the whole société.
     */
    @Transactional(readOnly = true)
    public List<VisiteResponse> agenda(UUID agentId, StatutVisite statut, Instant from, Instant to) {
        UUID societeId = societeCtx.requireSocieteId();
        UUID effectiveAgent = restrictedToOwnAgent() ? societeCtx.requireUserId() : agentId;

        List<Visite> visites;
        if (statut != null && effectiveAgent == null) {
            visites = visiteRepo.findBySocieteIdAndStatutAndDateHeureBetweenOrderByDateHeureAsc(societeId, statut, from, to);
        } else if (effectiveAgent != null) {
            visites = visiteRepo.findBySocieteIdAndAgentIdAndDateHeureBetweenOrderByDateHeureAsc(societeId, effectiveAgent, from, to);
            if (statut != null) {
                visites = visites.stream().filter(v -> v.getStatut() == statut).toList();
            }
        } else {
            visites = visiteRepo.findBySocieteIdAndDateHeureBetweenOrderByDateHeureAsc(societeId, from, to);
        }
        return visites.stream().map(VisiteResponse::from).toList();
    }

    /** All visites of a contact (RG-V09 / P5-T1). AGENT sees only their own. */
    @Transactional(readOnly = true)
    public List<VisiteResponse> listByContact(UUID contactId) {
        UUID societeId = societeCtx.requireSocieteId();
        List<Visite> visites = visiteRepo.findBySocieteIdAndContactIdOrderByDateHeureDesc(societeId, contactId);
        if (restrictedToOwnAgent()) {
            UUID self = societeCtx.requireUserId();
            visites = visites.stream().filter(v -> v.getAgent().getId().equals(self)).toList();
        }
        return visites.stream().map(VisiteResponse::from).toList();
    }

    /** KPI "Visites réalisées" over a period (RG-V09 — single source of truth). */
    @Transactional(readOnly = true)
    public long countRealisees(Instant from, Instant to) {
        return visiteRepo.countBySocieteIdAndStatutAndDateHeureBetween(
                societeCtx.requireSocieteId(), StatutVisite.REALISEE, from, to);
    }

    /** KPI conversion numerator — réalisées having created an opportunité (RG-V09). */
    @Transactional(readOnly = true)
    public long countOpportunites(Instant from, Instant to) {
        return visiteRepo.countBySocieteIdAndResultatAndDateHeureBetween(
                societeCtx.requireSocieteId(), ResultatVisite.OPPORTUNITE_CREEE, from, to);
    }

    /** Agent-scoped "Visites réalisées" — an AGENT's home dashboard sees only its own activity (RG-V04). */
    @Transactional(readOnly = true)
    public long countRealiseesForAgent(Instant from, Instant to, UUID agentId) {
        return visiteRepo.countBySocieteIdAndAgentIdAndStatutAndDateHeureBetween(
                societeCtx.requireSocieteId(), agentId, StatutVisite.REALISEE, from, to);
    }

    /** Agent-scoped opportunité conversion numerator (RG-V04). */
    @Transactional(readOnly = true)
    public long countOpportunitesForAgent(Instant from, Instant to, UUID agentId) {
        return visiteRepo.countBySocieteIdAndAgentIdAndResultatAndDateHeureBetween(
                societeCtx.requireSocieteId(), agentId, ResultatVisite.OPPORTUNITE_CREEE, from, to);
    }

    // =========================================================================
    // Internals
    // =========================================================================

    /**
     * Tenant guard for the optional property/project FKs: a visite must only reference a bien or
     * programme of its own société. Without this a caller could supply another tenant's UUID and
     * the FK would persist, leaving a visite pointing at a foreign commercial asset.
     */
    private void validerActifsLies(UUID societeId, UUID propertyId, UUID projectId) {
        if (propertyId != null && !propertyRepo.existsBySocieteIdAndIdAndDeletedAtIsNull(societeId, propertyId)) {
            throw new IllegalArgumentException("Bien hors société : " + propertyId);
        }
        if (projectId != null && !projectRepo.existsBySocieteIdAndId(societeId, projectId)) {
            throw new IllegalArgumentException("Programme hors société : " + projectId);
        }
    }

    /** Loads a visite in the current société, enforcing AGENT ownership (RG-V04). */
    private Visite chargerAccessible(UUID id) {
        UUID societeId = societeCtx.requireSocieteId();
        Visite visite = visiteRepo.findBySocieteIdAndId(societeId, id)
                .orElseThrow(() -> new VisiteNotFoundException(id));
        if (restrictedToOwnAgent() && !visite.getAgent().getId().equals(societeCtx.requireUserId())) {
            throw new VisiteNotFoundException(id); // do not leak other agents' visites
        }
        return visite;
    }

    private void transition(Visite visite, StatutVisite target) {
        if (!visite.getStatut().canTransitionTo(target)) {
            throw new InvalidVisiteTransitionException(visite.getStatut(), target);
        }
        visite.setStatut(target);
    }

    /** RG-V05 — fail if a non-cancelled visite of the same agent overlaps the slot. */
    private void verifierConflit(UUID societeId, UUID agentId, Instant debut, Instant fin,
                                 UUID excludeId, boolean override) {
        Instant borneBasse = debut.minus(FENETRE_CONFLIT);
        List<Visite> candidats = visiteRepo.findConflitCandidats(societeId, agentId, borneBasse, fin, excludeId);
        boolean chevauchement = candidats.stream().anyMatch(v -> v.getFin().isAfter(debut));
        if (!chevauchement) {
            return;
        }
        if (override && !restrictedToOwnAgent()) {
            log.warn("Conflit de créneau forcé (override) par {} pour agent {} sur [{} - {}]",
                    societeCtx.requireUserId(), agentId, debut, fin);
            return; // MANAGER/ADMIN may force-book (RG-V05), traced above
        }
        throw new ConflitVisiteException();
    }

    /** RG-V07 — create the persistent H24/H1 reminders for future-dated reminders only. */
    private void planifierRappels(Visite visite, Contact contact) {
        Instant now = Instant.now();
        List<VisiteRappel> rappels = new ArrayList<>();

        Instant h24 = visite.getDateHeure().minus(RAPPEL_H24);
        if (h24.isAfter(now)) {
            rappels.add(new VisiteRappel(visite.getSocieteId(), visite.getId(),
                    TypeRappel.H24, DestinataireRappel.AGENT, h24));
            if (contact.getEmail() != null && !contact.getEmail().isBlank()) {
                rappels.add(new VisiteRappel(visite.getSocieteId(), visite.getId(),
                        TypeRappel.H24, DestinataireRappel.PROSPECT, h24));
            }
        }
        Instant h1 = visite.getDateHeure().minus(RAPPEL_H1);
        if (h1.isAfter(now)) {
            rappels.add(new VisiteRappel(visite.getSocieteId(), visite.getId(),
                    TypeRappel.H1, DestinataireRappel.AGENT, h1));
        }
        if (!rappels.isEmpty()) {
            rappelRepo.saveAll(rappels);
        }
    }

    private void annulerRappelsEnAttente(UUID visiteId) {
        List<VisiteRappel> enAttente = rappelRepo.findByVisiteIdAndStatut(visiteId, StatutRappel.EN_ATTENTE);
        enAttente.forEach(VisiteRappel::marquerAnnule);
        if (!enAttente.isEmpty()) {
            rappelRepo.saveAll(enAttente);
        }
    }

    /**
     * Resolves the agent for a new visite (RG-V04). AGENT is forced to themselves; MANAGER/ADMIN
     * may book for any active member of the société (defaults to the current user).
     */
    private UUID resolveAgentId(UUID requested, UUID societeId, UUID actorId) {
        if (restrictedToOwnAgent() || requested == null || requested.equals(actorId)) {
            return actorId;
        }
        // Must be an *active* member: a removed/deactivated user can no longer access the société,
        // so a visite (and its reminders) must not be assigned to them. The plain
        // findByIdUserIdAndIdSocieteId would also match inactive memberships.
        membershipRepo.findByUserIdAndSocieteIdAndActifTrue(requested, societeId)
                .orElseThrow(() -> new IllegalArgumentException("Agent hors société : " + requested));
        return requested;
    }

    /** True when the caller is a plain AGENT (restricted to their own visites). */
    private boolean restrictedToOwnAgent() {
        return "ROLE_AGENT".equals(societeCtx.getRole());
    }
}
