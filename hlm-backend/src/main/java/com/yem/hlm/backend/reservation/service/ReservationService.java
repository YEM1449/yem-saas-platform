package com.yem.hlm.backend.reservation.service;

import com.yem.hlm.backend.audit.domain.AuditEventType;
import com.yem.hlm.backend.audit.service.CommercialAuditService;
import com.yem.hlm.backend.contact.domain.Contact;
import com.yem.hlm.backend.contact.repo.ContactRepository;
import com.yem.hlm.backend.contact.service.ContactNotFoundException;
import com.yem.hlm.backend.contact.service.CrossTenantAccessException;
import com.yem.hlm.backend.deposit.api.dto.CreateDepositRequest;
import com.yem.hlm.backend.deposit.api.dto.DepositResponse;
import com.yem.hlm.backend.deposit.service.DepositService;
import com.yem.hlm.backend.property.domain.Property;
import com.yem.hlm.backend.property.domain.PropertyStatus;
import com.yem.hlm.backend.property.repo.PropertyRepository;
import com.yem.hlm.backend.property.service.PropertyCommercialWorkflowService;
import com.yem.hlm.backend.property.service.PropertyNotFoundException;
import com.yem.hlm.backend.reservation.api.dto.ConvertReservationToDepositRequest;
import com.yem.hlm.backend.reservation.api.dto.CreateReservationRequest;
import com.yem.hlm.backend.reservation.api.dto.ReservationDetailResponse;
import com.yem.hlm.backend.reservation.api.dto.ReservationResponse;
import com.yem.hlm.backend.reservation.api.dto.VentePrefillResponse;
import com.yem.hlm.backend.reservation.domain.Reservation;
import com.yem.hlm.backend.reservation.domain.ReservationStatus;
import com.yem.hlm.backend.reservation.repo.ReservationRepository;
import com.yem.hlm.backend.common.event.PropertyInterestEvent;
import com.yem.hlm.backend.societe.SocieteContext;
import com.yem.hlm.backend.tranche.repo.TrancheRepository;
import com.yem.hlm.backend.user.domain.User;
import com.yem.hlm.backend.user.repo.UserRepository;
import com.yem.hlm.backend.vente.repo.VenteRepository;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
public class ReservationService {

    private final ReservationRepository reservationRepository;
    private final ContactRepository contactRepository;
    private final PropertyRepository propertyRepository;
    private final UserRepository userRepository;
    private final PropertyCommercialWorkflowService propertyWorkflow;
    private final DepositService depositService;
    private final CommercialAuditService auditService;
    private final ApplicationEventPublisher eventPublisher;
    private final ReservationRefGenerator refGenerator;
    private final VenteRepository venteRepository;
    private final TrancheRepository trancheRepository;

    public ReservationService(
            ReservationRepository reservationRepository,
            ContactRepository contactRepository,
            PropertyRepository propertyRepository,
            UserRepository userRepository,
            PropertyCommercialWorkflowService propertyWorkflow,
            DepositService depositService,
            CommercialAuditService auditService,
            ApplicationEventPublisher eventPublisher,
            ReservationRefGenerator refGenerator,
            VenteRepository venteRepository,
            TrancheRepository trancheRepository
    ) {
        this.reservationRepository = reservationRepository;
        this.contactRepository = contactRepository;
        this.propertyRepository = propertyRepository;
        this.userRepository = userRepository;
        this.propertyWorkflow = propertyWorkflow;
        this.depositService = depositService;
        this.auditService = auditService;
        this.eventPublisher = eventPublisher;
        this.refGenerator = refGenerator;
        this.venteRepository = venteRepository;
        this.trancheRepository = trancheRepository;
    }

    /**
     * Creates a lightweight reservation for a property.
     * The property must be ACTIVE with no existing ACTIVE reservation or deposit.
     * On success: property transitions to RESERVED.
     */
    @Transactional
    public ReservationResponse create(CreateReservationRequest req) {
        UUID societeId = requireSocieteId();
        UUID actorUserId = requireUserId();

        Contact contact = contactRepository.findBySocieteIdAndId(societeId, req.contactId())
                .orElseThrow(() -> new ContactNotFoundException(req.contactId()));

        User agent = userRepository.findById(actorUserId)
                .orElseThrow(() -> new CrossTenantAccessException("Unknown user: " + actorUserId));

        // Acquire pessimistic write lock to prevent concurrent reservation
        Property property = propertyRepository.findBySocieteIdAndIdForUpdate(societeId, req.propertyId())
                .orElseThrow(() -> new PropertyNotFoundException(req.propertyId()));

        if (property.getStatus() != PropertyStatus.ACTIVE) {
            throw new PropertyNotAvailableForReservationException(req.propertyId());
        }

        // Reject if an active reservation already exists for this property
        if (reservationRepository.existsBySocieteIdAndPropertyIdAndStatus(
                societeId, req.propertyId(), ReservationStatus.ACTIVE)) {
            throw new PropertyNotAvailableForReservationException(req.propertyId());
        }

        LocalDateTime expiry = (req.expiryDate() != null)
                ? req.expiryDate()
                : LocalDateTime.now().plusDays(7);

        String ref = refGenerator.generate(societeId);
        Reservation reservation = new Reservation(societeId, contact, req.propertyId(), agent, ref);
        reservation.setReservationPrice(req.reservationPrice());
        reservation.setExpiryDate(expiry);
        reservation.setNotes(req.notes());
        reservation.setStatus(ReservationStatus.ACTIVE);

        Reservation saved = reservationRepository.save(reservation);

        // Reserve the property
        propertyWorkflow.reserve(property, LocalDateTime.now());

        auditService.record(societeId, AuditEventType.RESERVATION_CREATED, actorUserId,
                "RESERVATION", saved.getId(), null);

        // Auto-promote prospect status on reservation
        eventPublisher.publishEvent(new PropertyInterestEvent(
                societeId, actorUserId, contact.getId(), req.propertyId(), "RESERVATION"));

        return toResponse(saved);
    }

    public ReservationResponse get(UUID id) {
        UUID societeId = requireSocieteId();
        Reservation r = reservationRepository.findBySocieteIdAndId(societeId, id)
                .orElseThrow(() -> new ReservationNotFoundException(id));
        return toResponse(r);
    }

    /** Returns an enriched reservation detail with contact, property, and linked vente info. */
    public ReservationDetailResponse getDetail(UUID id) {
        UUID societeId = requireSocieteId();
        Reservation r = reservationRepository.findBySocieteIdAndId(societeId, id)
                .orElseThrow(() -> new ReservationNotFoundException(id));

        var contact  = r.getContact();
        var property = propertyRepository.findBySocieteIdAndId(societeId, r.getPropertyId()).orElse(null);

        String trancheNom = null;
        if (property != null && property.getTrancheId() != null) {
            trancheNom = trancheRepository.findBySocieteIdAndId(societeId, property.getTrancheId())
                    .map(t -> t.getNom()).orElse(null);
        }

        UUID linkedVenteId = venteRepository.findBySocieteIdAndReservationId(societeId, id)
                .map(v -> v.getId()).orElse(null);

        return new ReservationDetailResponse(
                r.getId(), r.getSocieteId(), r.getReservationRef(), r.getStatus(),
                r.getReservationDate(), r.getExpiryDate(), r.getReservationPrice(), r.getNotes(),
                r.getConvertedDepositId(), r.getCreatedAt(), r.getUpdatedAt(),
                // Contact summary
                contact.getId(), contact.getFullName(), contact.getPhone(), contact.getEmail(),
                // Property summary
                property != null ? property.getId() : null,
                property != null ? property.getTitle() : null,
                property != null ? property.getReferenceCode() : null,
                property != null ? property.getPrice() : null,
                property != null ? property.getProjectName() : null,
                trancheNom,
                property != null ? property.getImmeubleName() : null,
                // Linked vente
                linkedVenteId
        );
    }

    /**
     * Returns prefill data for the "Create Vente from Reservation" form.
     * Only ACTIVE reservations can be used for prefill.
     */
    public VentePrefillResponse getVentePrefill(UUID id) {
        UUID societeId = requireSocieteId();
        Reservation r = reservationRepository.findBySocieteIdAndId(societeId, id)
                .orElseThrow(() -> new ReservationNotFoundException(id));

        if (r.getStatus() != ReservationStatus.ACTIVE) {
            throw new InvalidReservationStateException(
                    "Only ACTIVE reservations can be used for vente prefill (current: " + r.getStatus() + ")");
        }

        var contact  = r.getContact();
        var property = propertyRepository.findBySocieteIdAndId(societeId, r.getPropertyId()).orElse(null);

        String trancheNom = null;
        if (property != null && property.getTrancheId() != null) {
            trancheNom = trancheRepository.findBySocieteIdAndId(societeId, property.getTrancheId())
                    .map(t -> t.getNom()).orElse(null);
        }

        BigDecimal propertyPrice    = (property != null) ? property.getPrice() : null;
        BigDecimal reservationPrice = r.getReservationPrice();
        BigDecimal suggested = null;
        if (propertyPrice != null) {
            suggested = (reservationPrice != null)
                    ? propertyPrice.subtract(reservationPrice)
                    : propertyPrice;
        }

        return new VentePrefillResponse(
                r.getId(),
                r.getReservationRef(),
                reservationPrice,
                contact.getId(),
                contact.getFullName(),
                property != null ? property.getId() : null,
                property != null ? property.getTitle() : null,
                property != null ? property.getReferenceCode() : null,
                propertyPrice,
                property != null ? property.getProjectName() : null,
                trancheNom,
                property != null ? property.getImmeubleName() : null,
                suggested
        );
    }

    /**
     * Lists reservations for the current société.
     *
     * @param contactId optional — when provided, only returns reservations for that contact;
     *                  used by the ProspectDetail page to show a contact's reservation history.
     */
    public List<ReservationResponse> list(UUID contactId) {
        UUID societeId = requireSocieteId();
        List<Reservation> rows = (contactId != null)
                ? reservationRepository.findAllBySocieteIdAndContact_IdOrderByCreatedAtDesc(societeId, contactId)
                : reservationRepository.findAllBySocieteIdOrderByCreatedAtDesc(societeId);
        return rows.stream().map(this::toResponse).toList();
    }

    /**
     * Cancels an ACTIVE reservation and releases the property back to ACTIVE.
     */
    @Transactional
    public ReservationResponse cancel(UUID id) {
        UUID societeId = requireSocieteId();
        UUID actorUserId = requireUserId();

        Reservation reservation = reservationRepository.findBySocieteIdAndId(societeId, id)
                .orElseThrow(() -> new ReservationNotFoundException(id));

        if (reservation.getStatus() != ReservationStatus.ACTIVE) {
            throw new InvalidReservationStateException(
                    "Only ACTIVE reservations can be cancelled (current: " + reservation.getStatus() + ")");
        }

        reservation.setStatus(ReservationStatus.CANCELLED);
        Reservation saved = reservationRepository.save(reservation);

        releasePropertyIfStillReserved(societeId, reservation.getPropertyId());

        auditService.record(societeId, AuditEventType.RESERVATION_CANCELLED, actorUserId,
                "RESERVATION", saved.getId(), null);

        return toResponse(saved);
    }

    /**
     * Converts an ACTIVE reservation into a formal Deposit.
     * The reservation transitions to CONVERTED_TO_DEPOSIT.
     * The property remains RESERVED (the new deposit takes over).
     * The deposit is created via DepositService to preserve all existing business rules.
     */
    @Transactional
    public DepositResponse convertToDeposit(UUID id, ConvertReservationToDepositRequest req) {
        UUID societeId = requireSocieteId();
        UUID actorUserId = requireUserId();

        Reservation reservation = reservationRepository.findBySocieteIdAndId(societeId, id)
                .orElseThrow(() -> new ReservationNotFoundException(id));

        if (reservation.getStatus() != ReservationStatus.ACTIVE) {
            throw new InvalidReservationStateException(
                    "Only ACTIVE reservations can be converted (current: " + reservation.getStatus() + ")");
        }

        // Acquire pessimistic write lock to guard against concurrent state changes
        // (e.g. a contract signing that transitions the property to SOLD concurrently).
        Property property = propertyRepository.findBySocieteIdAndIdForUpdate(societeId, reservation.getPropertyId())
                .orElseThrow(() -> new PropertyNotFoundException(reservation.getPropertyId()));

        // Guard: property must still be RESERVED before we release it.
        // A concurrent contract sign could have moved it to SOLD; re-opening a SOLD
        // property to ACTIVE would allow a new deposit on an already-sold asset.
        if (property.getStatus() != PropertyStatus.RESERVED) {
            throw new InvalidReservationStateException(
                    "Property is no longer RESERVED (current: " + property.getStatus()
                    + ") — cannot convert reservation to deposit");
        }

        // Transition reservation first to avoid the active-reservation check in DepositService
        reservation.setStatus(ReservationStatus.CONVERTED_TO_DEPOSIT);
        reservationRepository.save(reservation);

        // Release property so DepositService can re-reserve it
        propertyWorkflow.releaseReservation(property);

        // Create the deposit via the canonical DepositService (applies all existing business rules)
        CreateDepositRequest depositReq = new CreateDepositRequest(
                reservation.getContact().getId(),
                reservation.getPropertyId(),
                req.amount(),
                req.depositDate(),
                req.reference(),
                req.currency(),
                req.dueDate(),
                null
        );
        DepositResponse depositResponse = depositService.create(depositReq);

        // Record the deposit ID on the reservation for traceability
        reservation.setConvertedDepositId(depositResponse.id());
        reservationRepository.save(reservation);

        auditService.record(societeId, AuditEventType.RESERVATION_CONVERTED_TO_DEPOSIT, actorUserId,
                "RESERVATION", id, null);

        return depositResponse;
    }

    /**
     * Called by the scheduler: expires ACTIVE reservations past their expiry date.
     */
    @Transactional
    public void runExpiryCheck() {
        List<Reservation> expired = reservationRepository.findExpired(LocalDateTime.now());
        for (Reservation r : expired) {
            expireReservation(r);
        }
    }

    // ---- Pipeline metrics ----

    public long countActiveReservations(UUID societeId) {
        return reservationRepository.countBySocieteIdAndStatus(societeId, ReservationStatus.ACTIVE);
    }

    public long countExpiringSoon(UUID societeId) {
        LocalDateTime now = LocalDateTime.now();
        return reservationRepository.countExpiringBefore(societeId, now, now.plusHours(48));
    }

    // ---- Private helpers ----

    private void expireReservation(Reservation r) {
        r.setStatus(ReservationStatus.EXPIRED);
        reservationRepository.save(r);

        releasePropertyIfStillReserved(r.getSocieteId(), r.getPropertyId());

        auditService.record(r.getSocieteId(), AuditEventType.RESERVATION_EXPIRED,
                r.getReservedByUser().getId(), "RESERVATION", r.getId(), null);
    }

    private void releasePropertyIfStillReserved(UUID societeId, UUID propertyId) {
        propertyRepository.findBySocieteIdAndId(societeId, propertyId).ifPresent(property -> {
            if (property.getStatus() == PropertyStatus.RESERVED) {
                propertyWorkflow.releaseReservation(property);
            }
        });
    }

    private UUID requireSocieteId() {
        UUID id = SocieteContext.getSocieteId();
        if (id == null) throw new CrossTenantAccessException("Missing société context");
        return id;
    }

    private UUID requireUserId() {
        UUID id = SocieteContext.getUserId();
        if (id == null) throw new CrossTenantAccessException("Missing user context");
        return id;
    }

    private ReservationResponse toResponse(Reservation r) {
        return new ReservationResponse(
                r.getId(),
                r.getSocieteId(),
                r.getReservationRef(),
                r.getContact().getId(),
                r.getPropertyId(),
                r.getReservedByUser().getId(),
                r.getReservationPrice(),
                r.getReservationDate(),
                r.getExpiryDate(),
                r.getStatus(),
                r.getNotes(),
                r.getConvertedDepositId(),
                r.getCreatedAt(),
                r.getUpdatedAt()
        );
    }
}
