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
import com.yem.hlm.backend.reservation.api.dto.ReservationResponse;
import com.yem.hlm.backend.reservation.domain.Reservation;
import com.yem.hlm.backend.reservation.domain.ReservationStatus;
import com.yem.hlm.backend.reservation.repo.ReservationRepository;
import com.yem.hlm.backend.societe.SocieteContext;
import com.yem.hlm.backend.user.domain.User;
import com.yem.hlm.backend.user.repo.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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

    public ReservationService(
            ReservationRepository reservationRepository,
            ContactRepository contactRepository,
            PropertyRepository propertyRepository,
            UserRepository userRepository,
            PropertyCommercialWorkflowService propertyWorkflow,
            DepositService depositService,
            CommercialAuditService auditService
    ) {
        this.reservationRepository = reservationRepository;
        this.contactRepository = contactRepository;
        this.propertyRepository = propertyRepository;
        this.userRepository = userRepository;
        this.propertyWorkflow = propertyWorkflow;
        this.depositService = depositService;
        this.auditService = auditService;
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
        Property property = propertyRepository.findByTenantIdAndIdForUpdate(societeId, req.propertyId())
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

        Reservation reservation = new Reservation(societeId, contact, req.propertyId(), agent);
        reservation.setReservationPrice(req.reservationPrice());
        reservation.setExpiryDate(expiry);
        reservation.setNotes(req.notes());
        reservation.setStatus(ReservationStatus.ACTIVE);

        Reservation saved = reservationRepository.save(reservation);

        // Reserve the property
        propertyWorkflow.reserve(property, LocalDateTime.now());

        auditService.record(societeId, AuditEventType.RESERVATION_CREATED, actorUserId,
                "RESERVATION", saved.getId(), null);

        return toResponse(saved);
    }

    public ReservationResponse get(UUID id) {
        UUID societeId = requireSocieteId();
        Reservation r = reservationRepository.findBySocieteIdAndId(societeId, id)
                .orElseThrow(() -> new ReservationNotFoundException(id));
        return toResponse(r);
    }

    public List<ReservationResponse> list() {
        UUID societeId = requireSocieteId();
        return reservationRepository.findAllBySocieteIdOrderByCreatedAtDesc(societeId)
                .stream().map(this::toResponse).toList();
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
        Property property = propertyRepository.findByTenantIdAndIdForUpdate(societeId, reservation.getPropertyId())
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
