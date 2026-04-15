package com.yem.hlm.backend.vente.service;

import com.yem.hlm.backend.contact.domain.Contact;
import com.yem.hlm.backend.contact.domain.ContactStatus;
import com.yem.hlm.backend.contact.repo.ContactRepository;
import com.yem.hlm.backend.contact.service.ContactNotFoundException;
import com.yem.hlm.backend.property.domain.Property;
import com.yem.hlm.backend.property.repo.PropertyRepository;
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
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
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
 * <p>State machine: COMPROMIS → FINANCEMENT → ACTE_NOTARIE → LIVRE (or ANNULE at any step).
 */
@Service
@Transactional
public class VenteService {

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
            VenteRefGenerator refGenerator) {
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
            if (request.prixVente() != null && request.prixVente().compareTo(BigDecimal.ZERO) > 0) {
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
            if (request.prixVente() != null && request.prixVente().compareTo(BigDecimal.ZERO) > 0) {
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

        // Mark property as SOLD
        propertyWorkflow.sell(property, LocalDateTime.now());
        propertyRepository.save(property);

        // Advance contact to ACTIVE_CLIENT (sale underway)
        advanceContactStatus(contact, ContactStatus.ACTIVE_CLIENT);
        contactRepository.save(contact);

        Vente vente = new Vente(societeId, property.getId(), contact, agent);
        vente.setVenteRef(refGenerator.generate(societeId));
        vente.setReservationId(reservationId);
        vente.setPrixVente(finalPrice);
        vente.setDateCompromis(request.dateCompromis());
        // Auto-populate French legal deadlines from dateCompromis
        if (request.dateCompromis() != null) {
            vente.setDateFinDelaiSru(request.dateCompromis().plusDays(10));
            vente.setDateLimiteConditionCredit(request.dateCompromis().plusDays(45));
        }
        vente.setDateLivraisonPrevue(request.dateLivraisonPrevue());
        vente.setNotes(request.notes());

        if (request.expectedClosingDate() != null) {
            vente.setExpectedClosingDate(request.expectedClosingDate());
        } else if (request.dateLivraisonPrevue() != null) {
            vente.setExpectedClosingDate(request.dateLivraisonPrevue());
        } else {
            vente.setExpectedClosingDate(LocalDate.now().plusDays(estimatedDaysToClose(vente.getStatut())));
        }

        return toResponse(venteRepository.save(vente));
    }

    @Transactional(readOnly = true)
    public List<VenteResponse> findAll() {
        UUID societeId = societeCtx.requireSocieteId();
        return venteRepository.findAllBySocieteIdOrderByCreatedAtDesc(societeId)
                .stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public List<VenteResponse> findByContactId(UUID contactId) {
        UUID societeId = societeCtx.requireSocieteId();
        return venteRepository.findAllBySocieteIdAndContact_IdOrderByCreatedAtDesc(societeId, contactId)
                .stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public VenteResponse findById(UUID id) {
        UUID societeId = societeCtx.requireSocieteId();
        return toResponse(requireVente(societeId, id));
    }

    // =========================================================================
    // Statut transition
    // =========================================================================

    public VenteResponse updateStatut(UUID id, UpdateVenteStatutRequest request) {
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
        vente.setStageEntryDate(LocalDateTime.now());
        vente.setProbability(defaultProbability(request.statut()));
        vente.setNotes(request.notes() != null ? request.notes() : vente.getNotes());

        if (request.expectedClosingDate() != null) {
            vente.setExpectedClosingDate(request.expectedClosingDate());
        } else if (vente.getExpectedClosingDate() == null) {
            vente.setExpectedClosingDate(LocalDate.now().plusDays(estimatedDaysToClose(request.statut())));
        }

        // Stamp date fields based on the new statut
        if (request.dateTransition() != null) {
            switch (request.statut()) {
                case ACTE_NOTARIE -> vente.setDateActeNotarie(request.dateTransition());
                case LIVRE        -> vente.setDateLivraisonReelle(request.dateTransition());
                default           -> { /* no specific date field for other statuts */ }
            }
        }

        // Advance contact to COMPLETED_CLIENT when sale is delivered
        if (request.statut() == VenteStatut.LIVRE) {
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

        return toResponse(venteRepository.save(vente));
    }

    // =========================================================================
    // Échéancier
    // =========================================================================

    public EcheanceResponse addEcheance(UUID venteId, CreateEcheanceRequest request) {
        UUID societeId = societeCtx.requireSocieteId();
        Vente vente = requireVente(societeId, venteId);

        dateCoherence.validateEcheanceDates(vente.getDateCompromis(), request.dateEcheance(), null);

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
     * Generates a contract document for the given vente and transitions
     * {@code contractStatus} to {@link com.yem.hlm.backend.vente.domain.ContractStatus#GENERATED}.
     *
     * <p>PDF generation is a placeholder — a real implementation would call a template engine
     * (e.g., Thymeleaf + openhtmltopdf) and store the result via {@code VenteDocumentService}.
     *
     * @return updated VenteResponse with contractStatus = GENERATED
     */
    public VenteResponse generateContract(UUID venteId) {
        UUID societeId = societeCtx.requireSocieteId();
        Vente vente    = requireVente(societeId, venteId);

        if (vente.getContractStatus() == com.yem.hlm.backend.vente.domain.ContractStatus.SIGNED) {
            throw new InvalidVenteTransitionException(vente.getStatut(), vente.getStatut());
        }

        // TODO: invoke PDF template engine and store document via addDocument()
        // For now we mark the status so the UI unlocks the Sign button.
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
        if (request.dateLimiteConditionCredit()   != null) vente.setDateLimiteConditionCredit(request.dateLimiteConditionCredit());
        if (request.notaireAcquereurNom()         != null) vente.setNotaireAcquereurNom(request.notaireAcquereurNom());
        if (request.notaireAcquereurEmail()       != null) vente.setNotaireAcquereurEmail(request.notaireAcquereurEmail());

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
            case COMPROMIS    -> 25;
            case FINANCEMENT  -> 50;
            case ACTE_NOTARIE -> 75;
            case LIVRE        -> 100;
            case ANNULE       -> 0;
        };
    }

    private static long estimatedDaysToClose(VenteStatut statut) {
        return switch (statut) {
            case COMPROMIS    -> 90;
            case FINANCEMENT  -> 60;
            case ACTE_NOTARIE -> 30;
            case LIVRE, ANNULE -> 0;
        };
    }

    /**
     * Validates that the requested statut transition is permitted.
     *
     * <pre>
     * COMPROMIS  → FINANCEMENT, ANNULE
     * FINANCEMENT → ACTE_NOTARIE, ANNULE
     * ACTE_NOTARIE → LIVRE, ANNULE
     * LIVRE      → (terminal)
     * ANNULE     → (terminal)
     * </pre>
     */
    private void validateTransition(VenteStatut from, VenteStatut to) {
        if (from == to) return; // idempotent — no-op
        Set<VenteStatut> allowed = switch (from) {
            case COMPROMIS    -> Set.of(VenteStatut.FINANCEMENT,  VenteStatut.ANNULE);
            case FINANCEMENT  -> Set.of(VenteStatut.ACTE_NOTARIE, VenteStatut.ANNULE);
            case ACTE_NOTARIE -> Set.of(VenteStatut.LIVRE,        VenteStatut.ANNULE);
            case LIVRE, ANNULE -> Set.of(); // terminal states
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
        return new VenteResponse(
                v.getId(), v.getVenteRef(), v.getSocieteId(), v.getPropertyId(),
                v.getContact().getId(), v.getContact().getFullName(), v.getAgent().getId(),
                v.getReservationId(), v.getStatut(), v.getContractStatus(),
                // Legal deadlines
                v.getDateFinDelaiSru(), v.getDateLimiteConditionCredit(),
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
                v.getCreatedAt(), v.getUpdatedAt());
    }

    private EcheanceResponse toEcheanceResponse(VenteEcheance e) {
        return new EcheanceResponse(
                e.getId(), e.getVente().getId(),
                e.getLibelle(), e.getMontant(), e.getDateEcheance(),
                e.getStatut(), e.getDatePaiement(), e.getNotes(), e.getCreatedAt());
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
