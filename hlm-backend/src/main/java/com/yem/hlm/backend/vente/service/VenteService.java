package com.yem.hlm.backend.vente.service;

import com.yem.hlm.backend.contact.domain.Contact;
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
import com.yem.hlm.backend.societe.SocieteContextHelper;
import com.yem.hlm.backend.user.domain.User;
import com.yem.hlm.backend.user.repo.UserRepository;
import com.yem.hlm.backend.user.service.UserNotFoundException;
import com.yem.hlm.backend.vente.api.dto.*;
import com.yem.hlm.backend.vente.domain.*;
import com.yem.hlm.backend.vente.repo.VenteDocumentRepository;
import com.yem.hlm.backend.vente.repo.VenteEcheanceRepository;
import com.yem.hlm.backend.vente.repo.VenteRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

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

    public VenteService(
            VenteRepository venteRepository,
            VenteEcheanceRepository echeanceRepository,
            VenteDocumentRepository documentRepository,
            ContactRepository contactRepository,
            PropertyRepository propertyRepository,
            UserRepository userRepository,
            ReservationRepository reservationRepository,
            PropertyCommercialWorkflowService propertyWorkflow,
            SocieteContextHelper societeCtx) {
        this.venteRepository     = venteRepository;
        this.echeanceRepository  = echeanceRepository;
        this.documentRepository  = documentRepository;
        this.contactRepository   = contactRepository;
        this.propertyRepository  = propertyRepository;
        this.userRepository      = userRepository;
        this.reservationRepository = reservationRepository;
        this.propertyWorkflow    = propertyWorkflow;
        this.societeCtx          = societeCtx;
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

        if (request.reservationId() != null) {
            // Convert from reservation
            Reservation reservation = reservationRepository
                    .findBySocieteIdAndId(societeId, request.reservationId())
                    .orElseThrow(() -> new ReservationNotFoundException(request.reservationId()));

            contact  = reservation.getContact();
            property = propertyRepository
                    .findBySocieteIdAndIdAndDeletedAtIsNull(societeId, reservation.getPropertyId())
                    .orElseThrow(() -> new PropertyNotFoundException(reservation.getPropertyId()));
            agent    = reservation.getReservedByUser();
            reservationId = reservation.getId();

            // Mark reservation as closed
            reservation.setStatus(ReservationStatus.CONVERTED_TO_DEPOSIT);
            reservationRepository.save(reservation);

        } else {
            // Direct creation — contact and property are mandatory
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
        }

        // Mark property as SOLD
        propertyWorkflow.sell(property, LocalDateTime.now());
        propertyRepository.save(property);

        Vente vente = new Vente(societeId, property.getId(), contact, agent);
        vente.setReservationId(reservationId);
        vente.setPrixVente(request.prixVente());
        vente.setDateCompromis(request.dateCompromis());
        vente.setDateLivraisonPrevue(request.dateLivraisonPrevue());
        vente.setNotes(request.notes());

        return toResponse(venteRepository.save(vente));
    }

    @Transactional(readOnly = true)
    public List<VenteResponse> findAll() {
        UUID societeId = societeCtx.requireSocieteId();
        return venteRepository.findAllBySocieteIdOrderByCreatedAtDesc(societeId)
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

        vente.setStatut(request.statut());
        vente.setNotes(request.notes() != null ? request.notes() : vente.getNotes());

        // Stamp date fields based on the new statut
        if (request.dateTransition() != null) {
            switch (request.statut()) {
                case ACTE_NOTARIE -> vente.setDateActeNotarie(request.dateTransition());
                case LIVRE        -> vente.setDateLivraisonReelle(request.dateTransition());
                default           -> { /* no specific date field for other statuts */ }
            }
        }

        return toResponse(venteRepository.save(vente));
    }

    // =========================================================================
    // Échéancier
    // =========================================================================

    public EcheanceResponse addEcheance(UUID venteId, CreateEcheanceRequest request) {
        UUID societeId = societeCtx.requireSocieteId();
        Vente vente = requireVente(societeId, venteId);

        var echeance = new VenteEcheance(
                societeId, vente,
                request.libelle(),
                request.montant(),
                request.dateEcheance());
        echeance.setNotes(request.notes());

        return toEcheanceResponse(echeanceRepository.save(echeance));
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
            echeance.setDatePaiement(request.datePaiement());
        }

        return toEcheanceResponse(echeanceRepository.save(echeance));
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

    public VenteResponse toResponse(Vente v) {
        List<EcheanceResponse> echeances = v.getEcheances().stream()
                .map(this::toEcheanceResponse).toList();
        List<VenteDocumentResponse> docs = v.getDocuments().stream()
                .map(this::toDocumentResponse).toList();
        return new VenteResponse(
                v.getId(), v.getSocieteId(), v.getPropertyId(),
                v.getContact().getId(), v.getAgent().getId(),
                v.getReservationId(), v.getStatut(), v.getPrixVente(),
                v.getDateCompromis(), v.getDateActeNotarie(),
                v.getDateLivraisonPrevue(), v.getDateLivraisonReelle(),
                v.getNotes(), echeances, docs,
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
                d.getUploadedBy().getId(), d.getCreatedAt());
    }
}
