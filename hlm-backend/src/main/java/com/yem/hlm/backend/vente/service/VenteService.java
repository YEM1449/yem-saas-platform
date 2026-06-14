package com.yem.hlm.backend.vente.service;

import com.yem.hlm.backend.contact.domain.Contact;
import com.yem.hlm.backend.contact.domain.ContactStatus;
import com.yem.hlm.backend.contact.repo.ContactRepository;
import com.yem.hlm.backend.contact.service.ContactNotFoundException;
import com.yem.hlm.backend.property.domain.Property;
import com.yem.hlm.backend.property.domain.PropertyStatus;
import com.yem.hlm.backend.property.repo.PropertyRepository;
import com.yem.hlm.backend.property.service.InvalidPropertyStatusTransitionException;
import com.yem.hlm.backend.property.service.PropertyCommercialWorkflowService;
import com.yem.hlm.backend.property.service.PropertyNotFoundException;
import com.yem.hlm.backend.reservation.domain.Reservation;
import com.yem.hlm.backend.reservation.domain.ReservationStatus;
import com.yem.hlm.backend.reservation.repo.ReservationRepository;
import com.yem.hlm.backend.reservation.service.ReservationNotFoundException;
import com.yem.hlm.backend.common.event.EcheanceChangedEvent;
import com.yem.hlm.backend.common.event.SaleFinalizedEvent;
import com.yem.hlm.backend.societe.SocieteContextHelper;
import com.yem.hlm.backend.user.domain.User;
import com.yem.hlm.backend.user.repo.UserRepository;
import com.yem.hlm.backend.user.service.UserNotFoundException;
import com.yem.hlm.backend.vente.api.dto.*;
import com.yem.hlm.backend.vente.domain.*;
import com.yem.hlm.backend.vente.repo.VenteDocumentRepository;
import com.yem.hlm.backend.vente.repo.VenteEcheanceRepository;
import com.yem.hlm.backend.vente.repo.VenteRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Set;
import java.util.UUID;

// Pipeline progression order for status advancement (never downgrade, never touch LOST/REFERRAL)
// PROSPECT=0, QUALIFIED_PROSPECT=1, CLIENT=2, ACTIVE_CLIENT=3, COMPLETED_CLIENT=4

/**
 * Core business logic for the Vente (sale) pipeline.
 *
 * <p>A Vente tracks the full commercial lifecycle from compromis to livraison.
 * It can be created by converting a Reservation or directly from contact + property.
 *
 * <p>State machine (VEFA Loi 44-00, Wave 12): PROSPECT → OPTION → RESERVE → EN_RETRACTATION →
 * ACOMPTE → COMPROMIS → FINANCEMENT → ACTE → LIVRE_AVEC_RESERVES → RESERVES_LEVEES →
 * LIVRE_DEFINITIF. ANNULE is the terminal failure state (see {@link VenteStatut}).
 */
@Service
@Transactional
public class VenteService {

    @Value("${app.vente.default-reflection-period-days:10}")
    private int defaultReflectionPeriodDays;

    @Value("${app.vente.default-financing-period-days:45}")
    private int defaultFinancingPeriodDays;

    private final VenteRepository venteRepository;
    private final VenteEcheanceRepository echeanceRepository;
    private final VenteDocumentRepository documentRepository;
    private final ContactRepository contactRepository;
    private final PropertyRepository propertyRepository;
    private final UserRepository userRepository;
    private final ReservationRepository reservationRepository;
    private final PropertyCommercialWorkflowService propertyWorkflow;
    private final SocieteContextHelper societeCtx;
    private final DateCoherenceValidator dateCoherence;
    private final ApplicationEventPublisher eventPublisher;
    private final VenteRefGenerator refGenerator;
    private final com.yem.hlm.backend.legal.MarketConfig marketConfig;
    private final com.yem.hlm.backend.vente.repo.ReserveLivraisonRepository reserveRepository;
    private final com.yem.hlm.backend.notification.service.NotificationService notificationService;
    /** Market-aware clock — all legal date/time math runs in the jurisdiction zone (EX-009). */
    private final Clock clock;

    public VenteService(
            VenteRepository venteRepository,
            VenteEcheanceRepository echeanceRepository,
            VenteDocumentRepository documentRepository,
            ContactRepository contactRepository,
            PropertyRepository propertyRepository,
            UserRepository userRepository,
            ReservationRepository reservationRepository,
            PropertyCommercialWorkflowService propertyWorkflow,
            SocieteContextHelper societeCtx,
            DateCoherenceValidator dateCoherence,
            ApplicationEventPublisher eventPublisher,
            VenteRefGenerator refGenerator,
            com.yem.hlm.backend.legal.MarketConfig marketConfig,
            com.yem.hlm.backend.vente.repo.ReserveLivraisonRepository reserveRepository,
            com.yem.hlm.backend.notification.service.NotificationService notificationService,
            Clock clock) {
        this.venteRepository     = venteRepository;
        this.echeanceRepository  = echeanceRepository;
        this.documentRepository  = documentRepository;
        this.contactRepository   = contactRepository;
        this.propertyRepository  = propertyRepository;
        this.userRepository      = userRepository;
        this.reservationRepository = reservationRepository;
        this.propertyWorkflow    = propertyWorkflow;
        this.societeCtx          = societeCtx;
        this.dateCoherence       = dateCoherence;
        this.eventPublisher      = eventPublisher;
        this.refGenerator        = refGenerator;
        this.marketConfig        = marketConfig;
        this.reserveRepository   = reserveRepository;
        this.notificationService = notificationService;
        this.clock               = clock;
    }

    // =========================================================================
    // CRUD
    // =========================================================================

    /**
     * Creates a new Vente.
     *
     * <p>When {@code request.reservationId()} is non-null, contact/property/agent are
     * derived from the reservation and the reservation is marked as CONVERTED_TO_DEPOSIT
     * (re-uses that terminal status — the reservation is simply closed out).
     * Otherwise {@code contactId} and {@code propertyId} must be supplied.
     */
    public VenteResponse create(CreateVenteRequest request) {
        UUID societeId = societeCtx.requireSocieteId();
        UUID actorId   = societeCtx.requireUserId();

        Contact contact;
        Property property;
        User agent;
        UUID reservationId = null;
        BigDecimal finalPrice;
        java.time.LocalDate dateReservation = null;

        if (request.reservationId() != null) {
            // ── Convert from reservation ──────────────────────────────────────
            Reservation reservation = reservationRepository
                    .findBySocieteIdAndId(societeId, request.reservationId())
                    .orElseThrow(() -> new ReservationNotFoundException(request.reservationId()));

            contact  = reservation.getContact();
            property = propertyRepository
                    .findBySocieteIdAndIdAndDeletedAtIsNull(societeId, reservation.getPropertyId())
                    .orElseThrow(() -> new PropertyNotFoundException(reservation.getPropertyId()));
            agent    = reservation.getReservedByUser();
            reservationId   = reservation.getId();
            dateReservation = reservation.getReservationDate();

            // Mark reservation as closed
            reservation.setStatus(ReservationStatus.CONVERTED_TO_DEPOSIT);
            reservationRepository.save(reservation);

            // Price calculation: property price − advance already paid − optional reduction.
            // If an explicit prixVente is provided it takes precedence (override mode).
            if (request.prixVente() != null && request.prixVente().compareTo(BigDecimal.ZERO) <= 0) {
                throw new PrixVenteInvalideException();
            }
            if (request.prixVente() != null) {
                finalPrice = request.prixVente();
            } else {
                BigDecimal basePrice  = property.getPrice() != null
                        ? property.getPrice() : BigDecimal.ZERO;
                BigDecimal advance    = reservation.getReservationPrice() != null
                        ? reservation.getReservationPrice() : BigDecimal.ZERO;
                BigDecimal reduction  = request.reduction() != null
                        ? request.reduction() : BigDecimal.ZERO;
                finalPrice = basePrice.subtract(advance).subtract(reduction);
                if (finalPrice.compareTo(BigDecimal.ZERO) < 0) {
                    throw new IllegalArgumentException(
                            "Prix de vente calculé invalide : prix du bien (" + basePrice
                            + ") − avance (" + advance + ") − réduction (" + reduction
                            + ") est négatif. Veuillez ajuster la réduction.");
                }
            }

        } else {
            // ── Direct creation — contact and property are mandatory ───────────
            if (request.contactId() == null || request.propertyId() == null) {
                throw new IllegalArgumentException(
                        "contactId and propertyId are required when not converting a reservation");
            }
            contact  = contactRepository.findBySocieteIdAndId(societeId, request.contactId())
                    .orElseThrow(() -> new ContactNotFoundException(request.contactId()));
            property = propertyRepository
                    .findBySocieteIdAndIdAndDeletedAtIsNull(societeId, request.propertyId())
                    .orElseThrow(() -> new PropertyNotFoundException(request.propertyId()));
            UUID agentId = request.agentId() != null ? request.agentId() : actorId;
            agent = userRepository.findById(agentId)
                    .orElseThrow(() -> new UserNotFoundException(agentId));
            // Use provided price; fall back to property catalogue price when omitted.
            // A non-null price that is zero or negative is an explicit error (A-004).
            if (request.prixVente() != null && request.prixVente().compareTo(BigDecimal.ZERO) <= 0) {
                throw new PrixVenteInvalideException();
            }
            if (request.prixVente() != null) {
                finalPrice = request.prixVente();
            } else if (property.getPrice() != null && property.getPrice().compareTo(BigDecimal.ZERO) > 0) {
                finalPrice = property.getPrice();
            } else {
                throw new IllegalArgumentException(
                        "prixVente est obligatoire : le bien n'a pas de prix catalogue défini");
            }
        }

        // Validate date coherence before persisting
        dateCoherence.validateVenteDates(
                dateReservation,
                request.dateCompromis(),
                null,
                request.dateLivraisonPrevue()
        );

        // RG-B03: a property may have at most one active (non-cancelled) vente at a time.
        // The DB partial unique index (changeset 075) is the concurrency-safe backstop;
        // this guard gives a clean 409 with an actionable message.
        if (venteRepository.existsBySocieteIdAndPropertyIdAndStatutNot(
                societeId, property.getId(), VenteStatut.ANNULE)) {
            throw new PropertyAlreadyEngagedException(property.getId());
        }

        // Property must be ACTIVE or RESERVED to start a vente.
        // If ACTIVE (direct creation path), reserve it now.
        // If already RESERVED (from deposit/reservation workflow), keep it RESERVED.
        // Property becomes SOLD only at ACTE stage (was ACTE_NOTARIE pre-Wave-12; see updateStatut).
        if (property.getStatus() == PropertyStatus.ACTIVE) {
            propertyWorkflow.reserve(property, LocalDateTime.now(clock));
            propertyRepository.save(property);
        } else if (property.getStatus() != PropertyStatus.RESERVED) {
            throw new InvalidPropertyStatusTransitionException(
                    "Property must be ACTIVE or RESERVED to start a vente; current status: "
                    + property.getStatus());
        }

        // Advance contact to ACTIVE_CLIENT (sale underway)
        advanceContactStatus(contact, ContactStatus.ACTIVE_CLIENT);
        contactRepository.save(contact);

        Vente vente = new Vente(societeId, property.getId(), contact, agent);
        vente.setVenteRef(refGenerator.generate(societeId));
        vente.setReservationId(reservationId);
        vente.setPrixVente(finalPrice);
        vente.setDateCompromis(request.dateCompromis());
        // Auto-populate legal milestone dates from dateCompromis using configurable periods
        if (request.dateCompromis() != null) {
            vente.setDateFinDelaiReflexion(request.dateCompromis().plusDays(defaultReflectionPeriodDays));
            vente.setDateLimiteFinancement(request.dateCompromis().plusDays(defaultFinancingPeriodDays));
        }
        vente.setDateLivraisonPrevue(request.dateLivraisonPrevue());
        vente.setNotes(request.notes());

        if (request.expectedClosingDate() != null) {
            vente.setExpectedClosingDate(request.expectedClosingDate());
        } else if (request.dateLivraisonPrevue() != null) {
            vente.setExpectedClosingDate(request.dateLivraisonPrevue());
        } else {
            vente.setExpectedClosingDate(LocalDate.now(clock).plusDays(estimatedDaysToClose(vente.getStatut())));
        }

        return toResponse(venteRepository.save(vente));
    }

    // =========================================================================
    // VEFA Loi 44-00 — OPTION + rétractation (Wave 12 P1-T3/T4)
    // =========================================================================

    /** Creates an OPTION (temporary hold, 1-72h, default capped) on a property for a contact. */
    @Transactional
    public VenteResponse createOption(UUID propertyId, UUID contactId, int dureeHeures) {
        UUID societeId = societeCtx.requireSocieteId();
        UUID actorId   = societeCtx.requireUserId();

        Contact contact = contactRepository.findBySocieteIdAndId(societeId, contactId)
                .orElseThrow(() -> new ContactNotFoundException(contactId));
        Property property = propertyRepository
                .findBySocieteIdAndIdAndDeletedAtIsNull(societeId, propertyId)
                .orElseThrow(() -> new PropertyNotFoundException(propertyId));

        // RG-B03: at most one active (non-cancelled) vente per property (fail fast before agent lookup).
        if (venteRepository.existsBySocieteIdAndPropertyIdAndStatutNot(societeId, propertyId, VenteStatut.ANNULE)) {
            throw new PropertyAlreadyEngagedException(propertyId);
        }
        if (property.getStatus() != PropertyStatus.ACTIVE) {
            throw new InvalidPropertyStatusTransitionException(
                    "Le bien doit être ACTIVE pour poser une option; statut actuel : " + property.getStatus());
        }

        User agent = userRepository.findById(actorId)
                .orElseThrow(() -> new UserNotFoundException(actorId));
        int duree = Math.min(Math.max(dureeHeures, 1), 72);
        propertyWorkflow.reserve(property, LocalDateTime.now(clock));
        propertyRepository.save(property);

        Vente vente = new Vente(societeId, property.getId(), contact, agent);
        vente.setVenteRef(refGenerator.generate(societeId));
        vente.setStatut(VenteStatut.OPTION);
        vente.setOptionExpireAt(Instant.now(clock).plus(duree, ChronoUnit.HOURS));
        if (property.getPrice() != null) vente.setPrixVente(property.getPrice());
        vente.setProbability(defaultProbability(VenteStatut.OPTION));
        vente.setExpectedClosingDate(LocalDate.now(clock).plusDays(estimatedDaysToClose(VenteStatut.OPTION)));

        advanceContactStatus(contact, ContactStatus.QUALIFIED_PROSPECT);
        contactRepository.save(contact);

        return toResponse(venteRepository.save(vente));
    }

    /** Confirms a reservation (from PROSPECT/OPTION): enforces the legal deposit cap and opens the cooling-off period. */
    @Transactional
    public VenteResponse confirmReservation(UUID venteId, BigDecimal montantDepot) {
        UUID societeId = societeCtx.requireSocieteId();
        Vente vente = requireVente(societeId, venteId);

        validateTransition(vente.getStatut(), VenteStatut.RESERVE);

        // Art. 618-4 Loi 44-00 — deposit ≤ 5% of the agreed price.
        BigDecimal price = vente.getPrixVente();
        if (montantDepot != null && price != null && price.compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal maxDepot = price.multiply(marketConfig.getDepotGarantieMaxPct());
            if (montantDepot.compareTo(maxDepot) > 0) {
                throw new ViolationLegaleException(
                        "Le dépôt de garantie ne peut excéder " + maxDepot
                        + " (5% du prix — Art. 618-4 Loi 44-00).");
            }
        }

        // Persist the deposit so the refund obligation has a known amount on annulation (#028).
        vente.setMontantDepot(montantDepot);

        vente.setStatut(VenteStatut.RESERVE);
        vente.setOptionExpireAt(null);

        // Open the legal cooling-off window and advance to EN_RETRACTATION.
        vente.setDateFinDelaiReflexion(LocalDate.now(clock).plusDays(marketConfig.getDelaiRetractationJours()));
        vente.setStatut(VenteStatut.EN_RETRACTATION);
        vente.setStageEntryDate(LocalDateTime.now(clock));
        vente.setProbability(defaultProbability(VenteStatut.EN_RETRACTATION));

        advanceContactStatus(vente.getContact(), ContactStatus.CLIENT);
        contactRepository.save(vente.getContact());

        return toResponse(venteRepository.save(vente));
    }

    /** Buyer exercises the legal cooling-off right within the window → vente cancelled, property freed. */
    @Transactional
    public VenteResponse exerciseRetractation(UUID venteId) {
        UUID societeId = societeCtx.requireSocieteId();
        Vente vente = requireVente(societeId, venteId);

        if (vente.getStatut() != VenteStatut.EN_RETRACTATION) {
            throw new RetractationImpossibleException("La vente n'est pas en période de rétractation.");
        }
        LocalDate deadline = vente.getDateFinDelaiReflexion();
        if (deadline != null && LocalDate.now(clock).isAfter(deadline)) {
            throw new RetractationImpossibleException(
                    "Le délai légal de rétractation de " + marketConfig.getDelaiRetractationJours()
                    + " jours est expiré.");
        }

        vente.setStatut(VenteStatut.ANNULE);
        vente.setRetractationExerceeAt(Instant.now(clock));
        vente.setMotifAnnulation(MotifAnnulation.DESISTEMENT_ACHETEUR);
        releasePropertyForCancelledVente(societeId, vente);
        echeanceRepository.cancelAllPendingByVente(vente.getId());

        Vente saved = venteRepository.save(vente);
        publishVenteAnnulee(societeId, saved);
        return toResponse(saved);
    }

    /** Fires the cancellation event so a refund obligation is recorded (#028). */
    private void publishVenteAnnulee(UUID societeId, Vente vente) {
        eventPublisher.publishEvent(new com.yem.hlm.backend.common.event.VenteAnnuleeEvent(
                societeId, societeCtx.requireUserId(), vente.getId(), vente.getMontantDepot()));
    }

    // ── scheduler-facing sweeps (run in system context) ──────────────────────

    /** Cancels OPTIONs whose hold has expired. Returns the number expired. */
    @Transactional
    public int expireOverdueOptions() {
        List<Vente> overdue = venteRepository.findByStatutAndOptionExpireAtBefore(VenteStatut.OPTION, Instant.now(clock));
        for (Vente v : overdue) {
            v.setStatut(VenteStatut.ANNULE);
            v.setMotifAnnulation(MotifAnnulation.AUTRE);
            releasePropertyForCancelledVente(v.getSocieteId(), v);
            venteRepository.save(v);
            notifyAgent(v, com.yem.hlm.backend.notification.domain.NotificationType.OPTION_EXPIRED,
                    "{\"venteRef\":\"" + safeJson(v.getVenteRef()) + "\"}");
        }
        return overdue.size();
    }

    /** Closes the cooling-off window for ventes whose retraction deadline has passed (→ RESERVE). Returns count. */
    @Transactional
    public int closeExpiredRetractations() {
        List<Vente> done = venteRepository.findByStatutAndDateFinDelaiReflexionBefore(
                VenteStatut.EN_RETRACTATION, LocalDate.now(clock));
        for (Vente v : done) {
            v.setStatut(VenteStatut.RESERVE);
            venteRepository.save(v);
            notifyAgent(v, com.yem.hlm.backend.notification.domain.NotificationType.RETRACTATION_DELAI_CLOS,
                    "{\"venteRef\":\"" + safeJson(v.getVenteRef()) + "\"}");
        }
        return done.size();
    }

    /** Pushes a VEFA notification to the vente's agent (best-effort — never breaks the sweep). */
    private void notifyAgent(Vente vente, com.yem.hlm.backend.notification.domain.NotificationType type, String payload) {
        if (vente.getAgent() == null) return;
        try {
            notificationService.notify(vente.getSocieteId(), vente.getAgent(), type, vente.getId(), payload);
        } catch (Exception ignored) {
            // a notification failure must not abort the scheduled sweep
        }
    }

    private static String safeJson(String s) {
        return s == null ? "" : s.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private void releasePropertyForCancelledVente(UUID societeId, Vente vente) {
        Property property = propertyRepository.findBySocieteIdAndId(societeId, vente.getPropertyId()).orElse(null);
        if (property != null) {
            if (property.getStatus() == PropertyStatus.RESERVED) {
                propertyWorkflow.releaseReservation(property);
            } else if (property.getStatus() == PropertyStatus.SOLD) {
                propertyWorkflow.cancelSaleToAvailable(property);
            }
            propertyRepository.save(property);
        }
    }

    // =========================================================================
    // VEFA Loi 44-00 — Livraison avec réserves (Wave 12 P1-T5)
    // =========================================================================

    /**
     * Records delivery from ACTE. With no reserves → LIVRE_DEFINITIF; otherwise →
     * LIVRE_AVEC_RESERVES and the reserves are created with a configurable lift deadline.
     * Reuses {@link #updateStatut} so the standard side-effects (date stamping, contact
     * advancement on final delivery) stay in one place.
     */
    @Transactional
    public VenteResponse recordDelivery(UUID venteId, RecordDeliveryRequest request) {
        UUID societeId = societeCtx.requireSocieteId();
        Vente vente = requireVente(societeId, venteId);

        boolean withReserves = request.reserves() != null && !request.reserves().isEmpty();
        VenteStatut target = withReserves ? VenteStatut.LIVRE_AVEC_RESERVES : VenteStatut.LIVRE_DEFINITIF;

        if (vente.getStatut() != VenteStatut.ACTE) {
            throw new InvalidVenteTransitionException(vente.getStatut(), target);
        }

        // Trusted internal call: the source-state check above is the guard; applyStatutChange bypasses
        // the public guard so this dedicated handler can legitimately enter LIVRE_AVEC_RESERVES.
        VenteResponse response = applyStatutChange(venteId, new UpdateVenteStatutRequest(
                target, null, request.dateLivraison(), null, null, null));

        if (withReserves) {
            LocalDate echeance = LocalDate.now(clock).plusDays(marketConfig.getDelaiLeveeReservesJours());
            for (String description : request.reserves()) {
                if (description != null && !description.isBlank()) {
                    reserveRepository.save(new ReserveLivraison(societeId, venteId, description.trim(), echeance));
                }
            }
        }
        return response;
    }

    @Transactional(readOnly = true)
    public List<ReserveLivraisonResponse> listReserves(UUID venteId) {
        UUID societeId = societeCtx.requireSocieteId();
        requireVente(societeId, venteId); // scope check
        return reserveRepository.findBySocieteIdAndVenteIdOrderByDateConstatAsc(societeId, venteId)
                .stream().map(ReserveLivraisonResponse::from).toList();
    }

    /** Lifts a single reserve; when the last one is lifted the vente advances to RESERVES_LEVEES. */
    @Transactional
    public VenteResponse liftReserve(UUID venteId, UUID reserveId) {
        UUID societeId = societeCtx.requireSocieteId();
        Vente vente = requireVente(societeId, venteId);
        ReserveLivraison reserve = reserveRepository.findBySocieteIdAndId(societeId, reserveId)
                .orElseThrow(() -> new ReserveNotFoundException(reserveId));
        if (!reserve.getVenteId().equals(venteId)) {
            throw new ReserveNotFoundException(reserveId);
        }

        reserve.setStatut(StatutReserve.LEVEE);
        reserve.setDateLeveeReelle(LocalDate.now(clock));
        reserveRepository.save(reserve);

        long remaining = reserveRepository.countBySocieteIdAndVenteIdAndStatutNot(
                societeId, venteId, StatutReserve.LEVEE);
        if (remaining == 0 && vente.getStatut() == VenteStatut.LIVRE_AVEC_RESERVES) {
            validateTransition(vente.getStatut(), VenteStatut.RESERVES_LEVEES);
            vente.setStatut(VenteStatut.RESERVES_LEVEES);
            vente.setProbability(defaultProbability(VenteStatut.RESERVES_LEVEES));
            vente.setStageEntryDate(LocalDateTime.now(clock));
            venteRepository.save(vente);
        }
        return toResponse(vente);
    }

    @Transactional(readOnly = true)
    public List<VenteResponse> findAll() {
        UUID societeId = societeCtx.requireSocieteId();
        return venteRepository.findAllBySocieteIdOrderByCreatedAtDesc(societeId)
                .stream().map(this::toResponse).toList();
    }

    /** Paginated société-scoped list (#023). */
    @Transactional(readOnly = true)
    public org.springframework.data.domain.Page<VenteResponse> findAll(
            org.springframework.data.domain.Pageable pageable) {
        UUID societeId = societeCtx.requireSocieteId();
        return venteRepository.findAllBySocieteId(societeId, pageable).map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public List<VenteResponse> findByContactId(UUID contactId) {
        UUID societeId = societeCtx.requireSocieteId();
        return venteRepository.findAllBySocieteIdAndContact_IdOrderByCreatedAtDesc(societeId, contactId)
                .stream().map(this::toResponse).toList();
    }

    /** Paginated société-scoped list filtered by buyer (#023). */
    @Transactional(readOnly = true)
    public org.springframework.data.domain.Page<VenteResponse> findByContactId(
            UUID contactId, org.springframework.data.domain.Pageable pageable) {
        UUID societeId = societeCtx.requireSocieteId();
        return venteRepository.findAllBySocieteIdAndContact_Id(societeId, contactId, pageable)
                .map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public VenteResponse findById(UUID id) {
        UUID societeId = societeCtx.requireSocieteId();
        return toResponse(requireVente(societeId, id));
    }

    // =========================================================================
    // Statut transition
    // =========================================================================

    /**
     * Stages that MUST be entered through their dedicated, guarded endpoint — never via the generic
     * {@code PATCH /statut} — because each enforces a legal/operational precondition the generic path
     * does not (deposit cap, cooling-off window, recorded reserves). See {@link GuardedStageEntryException}.
     */
    private static final java.util.Map<VenteStatut, String> GUARDED_ENTRY_ENDPOINTS = java.util.Map.of(
            VenteStatut.OPTION,              "POST /api/ventes/option",
            VenteStatut.RESERVE,             "POST /api/ventes/{id}/confirm-reservation",
            VenteStatut.EN_RETRACTATION,     "POST /api/ventes/{id}/confirm-reservation",
            VenteStatut.LIVRE_AVEC_RESERVES, "POST /api/ventes/{id}/livraison",
            VenteStatut.RESERVES_LEVEES,     "PUT /api/ventes/{id}/reserves/{reserveId}/lever"
    );

    /**
     * Public statut transition (the controller's {@code PATCH /statut}). Refuses transitions into a
     * guarded-entry stage so the legal guards of the dedicated handlers cannot be bypassed (EX-001).
     * All other transitions are applied via {@link #applyStatutChange}.
     */
    public VenteResponse updateStatut(UUID id, UpdateVenteStatutRequest request) {
        String dedicatedEndpoint = GUARDED_ENTRY_ENDPOINTS.get(request.statut());
        if (dedicatedEndpoint != null) {
            throw new GuardedStageEntryException(request.statut(), dedicatedEndpoint);
        }
        return applyStatutChange(id, request);
    }

    /**
     * Trusted internal statut applier. Callers must have already enforced any stage-specific
     * preconditions (e.g. {@link #recordDelivery} validates the source state and records reserves).
     * Not exposed via the public API — do not call from a controller.
     */
    private VenteResponse applyStatutChange(UUID id, UpdateVenteStatutRequest request) {
        UUID societeId = societeCtx.requireSocieteId();
        Vente vente = requireVente(societeId, id);

        validateTransition(vente.getStatut(), request.statut());

        // Cancellation reason is mandatory when annulling a sale
        if (request.statut() == VenteStatut.ANNULE) {
            if (request.motifAnnulation() == null) {
                throw new IllegalArgumentException(
                        "motifAnnulation est obligatoire lors de l'annulation d'une vente.");
            }
            vente.setMotifAnnulation(request.motifAnnulation());
        }

        vente.setStatut(request.statut());
        vente.setStageEntryDate(LocalDateTime.now(clock));
        vente.setProbability(defaultProbability(request.statut()));
        vente.setNotes(request.notes() != null ? request.notes() : vente.getNotes());

        if (request.expectedClosingDate() != null) {
            vente.setExpectedClosingDate(request.expectedClosingDate());
        } else if (vente.getExpectedClosingDate() == null) {
            vente.setExpectedClosingDate(LocalDate.now(clock).plusDays(estimatedDaysToClose(request.statut())));
        }

        // Stamp date fields based on the new statut
        if (request.dateTransition() != null) {
            switch (request.statut()) {
                case ACTE            -> vente.setDateActeNotarie(request.dateTransition());
                case LIVRE_DEFINITIF -> vente.setDateLivraisonReelle(request.dateTransition());
                default              -> { /* no specific date field for other statuts */ }
            }
        }

        // Capture PV de réception date when advancing to final delivery (optional convenience field)
        if (request.statut() == VenteStatut.LIVRE_DEFINITIF && request.datePvReception() != null) {
            vente.setDatePvReception(request.datePvReception());
        }

        // Advance contact to COMPLETED_CLIENT when sale is delivered
        if (request.statut() == VenteStatut.LIVRE_DEFINITIF) {
            Contact contact = vente.getContact();
            advanceContactStatus(contact, ContactStatus.COMPLETED_CLIENT);
            contactRepository.save(contact);

            // Publish KPI recomputation event after commit
            UUID trancheId = propertyRepository
                    .findBySocieteIdAndId(societeId, vente.getPropertyId())
                    .map(p -> p.getTrancheId()).orElse(null);
            eventPublisher.publishEvent(
                    new SaleFinalizedEvent(societeId, societeCtx.requireUserId(), vente.getId(), trancheId));
        }

        // Drive property status from vente stage:
        // ACTE → SOLD (legal ownership transfer); ANNULE → release back to ACTIVE
        Property property = propertyRepository
                .findBySocieteIdAndId(societeId, vente.getPropertyId()).orElse(null);
        if (property != null) {
            if (request.statut() == VenteStatut.ACTE) {
                propertyWorkflow.sell(property, LocalDateTime.now(clock));
                propertyRepository.save(property);
            } else if (request.statut() == VenteStatut.ANNULE) {
                if (property.getStatus() == PropertyStatus.RESERVED) {
                    propertyWorkflow.releaseReservation(property);
                } else if (property.getStatus() == PropertyStatus.SOLD) {
                    propertyWorkflow.cancelSaleToAvailable(property);
                }
                propertyRepository.save(property);
            }
        }

        // Cancel all pending échéances when a vente is annulled (A-001).
        // PAYEE échéances are already collected and are left unchanged.
        if (request.statut() == VenteStatut.ANNULE) {
            echeanceRepository.cancelAllPendingByVente(vente.getId());
        }

        Vente saved = venteRepository.save(vente);
        if (request.statut() == VenteStatut.ANNULE) {
            publishVenteAnnulee(societeId, saved);
        }
        return toResponse(saved);
    }

    // =========================================================================
    // Échéancier
    // =========================================================================

    public EcheanceResponse addEcheance(UUID venteId, CreateEcheanceRequest request) {
        UUID societeId = societeCtx.requireSocieteId();
        // Lock the vente: the cumulative-cap check below must be atomic w.r.t. concurrent additions (DA-003).
        Vente vente = requireVenteForUpdate(societeId, venteId);

        dateCoherence.validateEcheanceDates(vente.getDateCompromis(), request.dateEcheance(), null);
        assertCumulWithinPrice(societeId, venteId, vente.getPrixVente(), request.montant());

        var echeance = new VenteEcheance(
                societeId, vente,
                request.libelle(),
                request.montant(),
                request.dateEcheance());
        echeance.setNotes(request.notes());

        EcheanceResponse saved = toEcheanceResponse(echeanceRepository.save(echeance));

        UUID trancheId = propertyRepository.findBySocieteIdAndId(societeId, vente.getPropertyId())
                .map(p -> p.getTrancheId()).orElse(null);
        eventPublisher.publishEvent(
                new EcheanceChangedEvent(societeId, societeCtx.requireUserId(), venteId, trancheId));

        return saved;
    }

    /**
     * Generates the legal VEFA call-for-funds schedule (Art. 618-17 Loi 44-00): 7 staged
     * échéances whose percentages sum to 100% of the agreed price. Idempotent-guarded —
     * fails if a legal échéancier already exists.
     */
    @Transactional
    public List<EcheanceResponse> generateEcheancierLegal(UUID venteId) {
        UUID societeId = societeCtx.requireSocieteId();
        // Lock the vente: the "already generated?" idempotency guard below must be atomic so two
        // concurrent calls cannot each create a full 100% schedule (→ 200% total) (DA-003).
        Vente vente = requireVenteForUpdate(societeId, venteId);

        BigDecimal prix = vente.getPrixVente();
        if (prix == null || prix.compareTo(BigDecimal.ZERO) <= 0) {
            throw new ViolationLegaleException(
                    "Le prix de vente doit être défini pour générer l'échéancier légal.");
        }
        if (echeanceRepository.existsByVente_IdAndEtapeIsNotNull(venteId)) {
            throw new ViolationLegaleException("L'échéancier légal a déjà été généré pour cette vente.");
        }

        LocalDate base = (vente.getDateCompromis() != null && !vente.getDateCompromis().isBefore(LocalDate.now(clock)))
                ? vente.getDateCompromis() : LocalDate.now(clock);

        List<VenteEcheance> created = new java.util.ArrayList<>();
        for (com.yem.hlm.backend.legal.EcheancierLegal.EtapeLegale etape
                : com.yem.hlm.backend.legal.EcheancierLegal.MA) {
            BigDecimal montant = prix.multiply(etape.pct())
                    .divide(BigDecimal.valueOf(100), 2, java.math.RoundingMode.HALF_UP);
            LocalDate echeance = base.plusMonths(2L * (etape.ordre() - 1));
            VenteEcheance e = new VenteEcheance(societeId, vente,
                    etape.label() + " (" + etape.pct() + "%)", montant, echeance);
            e.setEtape(etape.code());
            e.setPctPrevu(etape.pct());
            e.setBaseLegale(com.yem.hlm.backend.legal.EcheancierLegal.BASE_LEGALE_MA);
            created.add(echeanceRepository.save(e));
        }

        UUID trancheId = propertyRepository.findBySocieteIdAndId(societeId, vente.getPropertyId())
                .map(p -> p.getTrancheId()).orElse(null);
        eventPublisher.publishEvent(
                new EcheanceChangedEvent(societeId, societeCtx.requireUserId(), venteId, trancheId));

        return created.stream().map(this::toEcheanceResponse).toList();
    }

    /** Art. 618-17 — the cumulative échéances may never exceed the agreed price. */
    private void assertCumulWithinPrice(UUID societeId, UUID venteId, BigDecimal prix, BigDecimal addedMontant) {
        if (prix == null || prix.compareTo(BigDecimal.ZERO) <= 0 || addedMontant == null) return;
        BigDecimal cumul = echeanceRepository.sumMontantByVente(societeId, venteId).add(addedMontant);
        if (cumul.compareTo(prix) > 0) {
            throw new ViolationLegaleException(
                    "Le cumul des échéances (" + cumul + ") dépasse le prix de vente (" + prix
                    + ") — Art. 618-17 Loi 44-00.");
        }
    }

    @Transactional(readOnly = true)
    public List<EcheanceResponse> findEcheances(UUID venteId) {
        UUID societeId = societeCtx.requireSocieteId();
        requireVente(societeId, venteId); // access check
        return echeanceRepository.findAllByVente_IdOrderByDateEcheanceAsc(venteId)
                .stream().map(this::toEcheanceResponse).toList();
    }

    public EcheanceResponse updateEcheanceStatut(UUID venteId, UUID echeanceId,
                                                 UpdateEcheanceStatutRequest request) {
        UUID societeId = societeCtx.requireSocieteId();
        requireVente(societeId, venteId); // access check

        VenteEcheance echeance = echeanceRepository.findBySocieteIdAndId(societeId, echeanceId)
                .orElseThrow(() -> new VenteEcheanceNotFoundException(echeanceId));

        echeance.setStatut(request.statut());
        if (request.datePaiement() != null) {
            dateCoherence.validateEcheanceDates(null, echeance.getDateEcheance(), request.datePaiement());
            echeance.setDatePaiement(request.datePaiement());
        }

        EcheanceResponse saved = toEcheanceResponse(echeanceRepository.save(echeance));

        Vente vente = requireVente(societeId, venteId);
        UUID trancheId = propertyRepository.findBySocieteIdAndId(societeId, vente.getPropertyId())
                .map(p -> p.getTrancheId()).orElse(null);
        eventPublisher.publishEvent(
                new EcheanceChangedEvent(societeId, societeCtx.requireUserId(), venteId, trancheId));

        return saved;
    }

    // =========================================================================
    // Contract generation / signing
    // =========================================================================

    /**
     * Called by the controller after PDF bytes have been generated and stored.
     * Creates a {@link VenteDocument} record for the PDF and transitions
     * {@code contractStatus} to {@link com.yem.hlm.backend.vente.domain.ContractStatus#GENERATED}.
     */
    public VenteResponse generateContract(UUID venteId, String storageKey,
                                          String fileName, long fileSizeBytes) {
        UUID societeId = societeCtx.requireSocieteId();
        UUID actorId   = societeCtx.requireUserId();
        Vente vente    = requireVente(societeId, venteId);

        if (vente.getContractStatus() == com.yem.hlm.backend.vente.domain.ContractStatus.SIGNED) {
            throw new InvalidVenteTransitionException(vente.getStatut(), vente.getStatut());
        }

        User uploader = userRepository.findById(actorId)
                .orElseThrow(() -> new UserNotFoundException(actorId));

        var doc = new VenteDocument(societeId, vente, fileName, storageKey,
                "application/pdf", fileSizeBytes, uploader);
        doc.setDocumentType(com.yem.hlm.backend.vente.domain.VenteDocumentType.CONTRAT_GENERE);
        documentRepository.save(doc);

        vente.setContractStatus(com.yem.hlm.backend.vente.domain.ContractStatus.GENERATED);
        return toResponse(venteRepository.save(vente));
    }

    /**
     * Marks the vente contract as SIGNED.
     * Requires {@code contractStatus == GENERATED}; throws {@link ContractNotGeneratedException} otherwise.
     */
    public VenteResponse signContract(UUID venteId) {
        UUID societeId = societeCtx.requireSocieteId();
        Vente vente    = requireVente(societeId, venteId);

        if (vente.getContractStatus() != com.yem.hlm.backend.vente.domain.ContractStatus.GENERATED) {
            throw new ContractNotGeneratedException(venteId);
        }

        vente.setContractStatus(com.yem.hlm.backend.vente.domain.ContractStatus.SIGNED);
        return toResponse(venteRepository.save(vente));
    }

    /**
     * Updates financing information for a vente (patch semantics — null fields are ignored).
     * Also allows overriding the condition suspensive crédit deadline and notary details.
     */
    public VenteResponse updateFinancement(UUID venteId, UpdateFinancingRequest request) {
        UUID societeId = societeCtx.requireSocieteId();
        Vente vente    = requireVente(societeId, venteId);

        if (request.typeFinancement()             != null) vente.setTypeFinancement(request.typeFinancement());
        if (request.montantCredit()               != null) vente.setMontantCredit(request.montantCredit());
        if (request.banqueCredit()                != null) vente.setBanqueCredit(request.banqueCredit());
        if (request.creditObtenu()                != null) vente.setCreditObtenu(request.creditObtenu());
        if (request.dateLimiteFinancement()        != null) vente.setDateLimiteFinancement(request.dateLimiteFinancement());
        if (request.notaireAcquereurNom()         != null) vente.setNotaireAcquereurNom(request.notaireAcquereurNom());
        if (request.notaireAcquereurEmail()       != null) vente.setNotaireAcquereurEmail(request.notaireAcquereurEmail());
        if (request.datePvReception()             != null) vente.setDatePvReception(request.datePvReception());
        if (request.dateTitreFoncier()            != null) vente.setDateTitreFoncier(request.dateTitreFoncier());

        return toResponse(venteRepository.save(vente));
    }

    // =========================================================================
    // Documents
    // =========================================================================

    public VenteDocumentResponse addDocument(UUID venteId, String nomFichier,
                                             String storageKey, String contentType,
                                             Long tailleOctets) {
        UUID societeId = societeCtx.requireSocieteId();
        UUID actorId   = societeCtx.requireUserId();
        Vente vente    = requireVente(societeId, venteId);
        User uploader  = userRepository.findById(actorId)
                .orElseThrow(() -> new UserNotFoundException(actorId));

        var doc = new VenteDocument(societeId, vente, nomFichier,
                storageKey, contentType, tailleOctets, uploader);
        return toDocumentResponse(documentRepository.save(doc));
    }

    /** Portal-upload variant — no CRM user; sets {@code uploadedByPortal = true}. */
    public VenteDocumentResponse addDocumentFromPortal(UUID venteId, String nomFichier,
                                                       String storageKey, String contentType,
                                                       Long tailleOctets,
                                                       VenteDocumentType documentType) {
        UUID societeId = societeCtx.requireSocieteId();
        Vente vente    = requireVente(societeId, venteId);
        var doc = new VenteDocument(societeId, vente, nomFichier,
                storageKey, contentType, tailleOctets, documentType);
        return toDocumentResponse(documentRepository.save(doc));
    }

    /**
     * Returns the storage key for a document so the caller (controller) can stream it.
     * Enforces société isolation and vente access checks.
     */
    @Transactional(readOnly = true)
    public String downloadDocumentKey(UUID venteId, UUID docId) {
        UUID societeId = societeCtx.requireSocieteId();
        requireVente(societeId, venteId); // access-check
        return documentRepository.findBySocieteIdAndId(societeId, docId)
                .map(VenteDocument::getStorageKey)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Document not found"));
    }

    @Transactional(readOnly = true)
    public List<VenteDocumentResponse> findDocuments(UUID venteId) {
        UUID societeId = societeCtx.requireSocieteId();
        requireVente(societeId, venteId); // access check
        return documentRepository.findAllByVente_IdOrderByCreatedAtDesc(venteId)
                .stream().map(this::toDocumentResponse).toList();
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    private Vente requireVente(UUID societeId, UUID venteId) {
        return venteRepository.findBySocieteIdAndId(societeId, venteId)
                .orElseThrow(() -> new VenteNotFoundException(venteId));
    }

    /**
     * Like {@link #requireVente} but takes a pessimistic write lock (SELECT ... FOR UPDATE) on the
     * vente row. Use it when a method must atomically read-then-write a vente's financial children
     * (échéances / appels de fonds): concurrent callers serialize on this row so the cumulative-cap
     * and idempotency checks cannot be bypassed by a race (DA-003). Must run inside a transaction.
     */
    private Vente requireVenteForUpdate(UUID societeId, UUID venteId) {
        return venteRepository.findBySocieteIdAndIdForUpdate(societeId, venteId)
                .orElseThrow(() -> new VenteNotFoundException(venteId));
    }

    /**
     * Advances a contact's status in the pipeline progression without downgrading.
     * LOST and REFERRAL contacts are not modified (their status is deliberate).
     *
     * <p>Progression order: PROSPECT(0) → QUALIFIED_PROSPECT(1) → CLIENT(2)
     *                       → ACTIVE_CLIENT(3) → COMPLETED_CLIENT(4)
     */
    private static final List<ContactStatus> PIPELINE_PROGRESSION = List.of(
            ContactStatus.PROSPECT, ContactStatus.QUALIFIED_PROSPECT,
            ContactStatus.CLIENT, ContactStatus.ACTIVE_CLIENT,
            ContactStatus.COMPLETED_CLIENT);

    private void advanceContactStatus(Contact contact, ContactStatus target) {
        ContactStatus current = contact.getStatus();
        // Never touch LOST or REFERRAL contacts — their statuses are deliberate
        if (current == ContactStatus.LOST || current == ContactStatus.REFERRAL) return;

        int currentIdx = PIPELINE_PROGRESSION.indexOf(current);
        int targetIdx  = PIPELINE_PROGRESSION.indexOf(target);
        // Only advance — never downgrade
        if (targetIdx > currentIdx) {
            contact.setStatus(target);
        }
    }

    private static int defaultProbability(VenteStatut statut) {
        return switch (statut) {
            case PROSPECT            -> 5;
            case OPTION              -> 10;
            case RESERVE             -> 20;
            case EN_RETRACTATION     -> 20;
            case ACOMPTE             -> 30;
            case COMPROMIS           -> 40;
            case FINANCEMENT         -> 50;
            case ACTE                -> 75;
            case LIVRE_AVEC_RESERVES -> 90;
            case RESERVES_LEVEES     -> 95;
            case LIVRE_DEFINITIF     -> 100;
            case ANNULE              -> 0;
        };
    }

    private static long estimatedDaysToClose(VenteStatut statut) {
        return switch (statut) {
            case PROSPECT            -> 150;
            case OPTION              -> 120;
            case RESERVE             -> 110;
            case EN_RETRACTATION     -> 100;
            case ACOMPTE             -> 95;
            case COMPROMIS           -> 90;
            case FINANCEMENT         -> 60;
            case ACTE                -> 30;
            case LIVRE_AVEC_RESERVES -> 15;
            case RESERVES_LEVEES     -> 5;
            case LIVRE_DEFINITIF, ANNULE -> 0;
        };
    }

    /**
     * Validates that the requested statut transition is permitted.
     *
     * <pre>
     * PROSPECT        → OPTION, RESERVE, ANNULE
     * OPTION          → RESERVE, PROSPECT, ANNULE
     * RESERVE         → EN_RETRACTATION, ACOMPTE, ANNULE
     * EN_RETRACTATION → ACOMPTE, ANNULE        (expiry → continue ; rétractation → ANNULE)
     * ACOMPTE         → COMPROMIS, ANNULE
     * COMPROMIS       → FINANCEMENT, ANNULE
     * FINANCEMENT     → ACTE, ANNULE
     * ACTE            → LIVRE_AVEC_RESERVES, LIVRE_DEFINITIF, ANNULE
     * LIVRE_AVEC_RESERVES → RESERVES_LEVEES, ANNULE
     * RESERVES_LEVEES → LIVRE_DEFINITIF
     * LIVRE_DEFINITIF, ANNULE → (terminal)
     * </pre>
     */
    private void validateTransition(VenteStatut from, VenteStatut to) {
        if (from == to) return; // idempotent — no-op
        Set<VenteStatut> allowed = switch (from) {
            case PROSPECT        -> Set.of(VenteStatut.OPTION, VenteStatut.RESERVE, VenteStatut.ANNULE);
            case OPTION          -> Set.of(VenteStatut.RESERVE, VenteStatut.PROSPECT, VenteStatut.ANNULE);
            case RESERVE         -> Set.of(VenteStatut.EN_RETRACTATION, VenteStatut.ACOMPTE, VenteStatut.ANNULE);
            case EN_RETRACTATION -> Set.of(VenteStatut.ACOMPTE, VenteStatut.ANNULE);
            case ACOMPTE         -> Set.of(VenteStatut.COMPROMIS, VenteStatut.ANNULE);
            case COMPROMIS       -> Set.of(VenteStatut.FINANCEMENT, VenteStatut.ANNULE);
            case FINANCEMENT     -> Set.of(VenteStatut.ACTE, VenteStatut.ANNULE);
            case ACTE            -> Set.of(VenteStatut.LIVRE_AVEC_RESERVES, VenteStatut.LIVRE_DEFINITIF, VenteStatut.ANNULE);
            case LIVRE_AVEC_RESERVES -> Set.of(VenteStatut.RESERVES_LEVEES, VenteStatut.ANNULE);
            case RESERVES_LEVEES -> Set.of(VenteStatut.LIVRE_DEFINITIF);
            case LIVRE_DEFINITIF, ANNULE -> Set.of(); // terminal states
        };
        if (!allowed.contains(to)) {
            throw new InvalidVenteTransitionException(from, to);
        }
    }

    public VenteResponse toResponse(Vente v) {
        List<EcheanceResponse> echeances = v.getEcheances().stream()
                .map(this::toEcheanceResponse).toList();
        List<VenteDocumentResponse> docs = v.getDocuments().stream()
                .map(this::toDocumentResponse).toList();

        // B-001: late-delivery penalty — only for active ventes past their expected delivery date
        Long joursRetard = null;
        BigDecimal penaliteAccumulee = null;
        boolean terminal = v.getStatut() == VenteStatut.ANNULE || v.getStatut() == VenteStatut.LIVRE_DEFINITIF;
        if (!terminal && v.getDateLivraisonReelle() == null && v.getDateLivraisonPrevue() != null) {
            LocalDate today = LocalDate.now(clock);
            if (today.isAfter(v.getDateLivraisonPrevue())) {
                joursRetard = ChronoUnit.DAYS.between(v.getDateLivraisonPrevue(), today);
                penaliteAccumulee = marketConfig.getPenaliteRetardJournalierMad()
                        .multiply(BigDecimal.valueOf(joursRetard));
            }
        }

        return new VenteResponse(
                v.getId(), v.getVenteRef(), v.getSocieteId(), v.getPropertyId(),
                v.getContact().getId(), v.getContact().getFullName(), v.getAgent().getId(),
                v.getReservationId(), v.getStatut(), v.getContractStatus(),
                // Legal milestone dates
                v.getDateFinDelaiReflexion(), v.getDateLimiteFinancement(),
                // Financing
                v.getTypeFinancement(), v.getMontantCredit(), v.getBanqueCredit(), v.isCreditObtenu(),
                // Cancellation
                v.getMotifAnnulation(),
                // Notary
                v.getNotaireAcquereurNom(), v.getNotaireAcquereurEmail(),
                // Post-livraison
                v.getDatePvReception(), v.getDateTitreFoncier(),
                v.getPrixVente(),
                v.getDateCompromis(), v.getDateActeNotarie(),
                v.getDateLivraisonPrevue(), v.getDateLivraisonReelle(),
                v.getNotes(), v.getProbability(), v.getStageEntryDate(),
                v.getExpectedClosingDate(), echeances, docs,
                v.getCreatedAt(), v.getUpdatedAt(),
                joursRetard, penaliteAccumulee);
    }

    private EcheanceResponse toEcheanceResponse(VenteEcheance e) {
        return new EcheanceResponse(
                e.getId(), e.getVente().getId(),
                e.getLibelle(), e.getMontant(), e.getDateEcheance(),
                e.getStatut(), e.getDatePaiement(), e.getNotes(), e.getCreatedAt(),
                e.getEtape(), e.getPctPrevu(), e.getBaseLegale());
    }

    private VenteDocumentResponse toDocumentResponse(VenteDocument d) {
        return new VenteDocumentResponse(
                d.getId(), d.getVente().getId(),
                d.getNomFichier(), d.getContentType(), d.getTailleOctets(),
                d.getUploadedBy() != null ? d.getUploadedBy().getId() : null,
                d.isUploadedByPortal(), d.getDocumentType(),
                d.getCreatedAt());
    }
}
