package com.yem.hlm.backend.deposit.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yem.hlm.backend.audit.domain.AuditEventType;
import com.yem.hlm.backend.audit.service.CommercialAuditService;
import com.yem.hlm.backend.contact.api.dto.ConvertToClientRequest;
import com.yem.hlm.backend.contact.domain.*;
import com.yem.hlm.backend.contact.repo.ClientDetailRepository;
import com.yem.hlm.backend.contact.repo.ContactInterestRepository;
import com.yem.hlm.backend.contact.repo.ContactRepository;
import com.yem.hlm.backend.contact.service.ContactNotFoundException;
import com.yem.hlm.backend.contact.service.CrossTenantAccessException;
import com.yem.hlm.backend.contact.service.PropertyNotFoundException;
import com.yem.hlm.backend.deposit.api.dto.*;
import com.yem.hlm.backend.deposit.domain.Deposit;
import com.yem.hlm.backend.deposit.domain.DepositStatus;
import com.yem.hlm.backend.deposit.repo.DepositRepository;
import com.yem.hlm.backend.notification.domain.NotificationType;
import com.yem.hlm.backend.notification.service.NotificationService;
import com.yem.hlm.backend.property.domain.Property;
import com.yem.hlm.backend.property.domain.PropertyStatus;
import com.yem.hlm.backend.property.repo.PropertyRepository;
import com.yem.hlm.backend.property.service.PropertyCommercialWorkflowService;
import com.yem.hlm.backend.tenant.context.TenantContext;
import com.yem.hlm.backend.tenant.domain.Tenant;
import com.yem.hlm.backend.tenant.repo.TenantRepository;
import com.yem.hlm.backend.user.domain.User;
import com.yem.hlm.backend.user.repo.UserRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

@Service
public class DepositService {

    private static final List<DepositStatus> ACTIVE_STATUSES = List.of(DepositStatus.PENDING, DepositStatus.CONFIRMED);

    private final DepositRepository depositRepository;
    private final ContactRepository contactRepository;
    private final ContactInterestRepository contactInterestRepository;
    private final ClientDetailRepository clientDetailRepository;
    private final PropertyRepository propertyRepository;
    private final TenantRepository tenantRepository;
    private final UserRepository userRepository;
    private final NotificationService notificationService;
    private final ObjectMapper objectMapper;
    private final PropertyCommercialWorkflowService propertyCommercialWorkflowService;
    private final CommercialAuditService auditService;

    public DepositService(
            DepositRepository depositRepository,
            ContactRepository contactRepository,
            ContactInterestRepository contactInterestRepository,
            ClientDetailRepository clientDetailRepository,
            PropertyRepository propertyRepository,
            TenantRepository tenantRepository,
            UserRepository userRepository,
            NotificationService notificationService,
            ObjectMapper objectMapper,
            PropertyCommercialWorkflowService propertyCommercialWorkflowService,
            CommercialAuditService auditService
    ) {
        this.depositRepository = depositRepository;
        this.contactRepository = contactRepository;
        this.contactInterestRepository = contactInterestRepository;
        this.clientDetailRepository = clientDetailRepository;
        this.propertyRepository = propertyRepository;
        this.tenantRepository = tenantRepository;
        this.userRepository = userRepository;
        this.notificationService = notificationService;
        this.objectMapper = objectMapper;
        this.propertyCommercialWorkflowService = propertyCommercialWorkflowService;
        this.auditService = auditService;
    }

    /**
     * Called from /api/contacts/{id}/convert-to-client (backward compatible).
     * It creates a PENDING deposit and converts the contact to TEMP_CLIENT until dueDate.
     */
    @Transactional
    public void createReservationForContact(UUID contactId, ConvertToClientRequest req) {
        if (req.propertyId() == null) throw new InvalidDepositRequestException("propertyId is required");
        if (req.amount() == null || req.amount().compareTo(BigDecimal.ZERO) <= 0) throw new InvalidDepositRequestException("amount must be > 0");

        CreateDepositRequest mapped = new CreateDepositRequest(
                contactId,
                req.propertyId(),
                req.amount(),
                req.depositDate(),
                req.reference(),
                req.currency(),
                req.dueDate(),
                null
        );
        create(mapped);
    }

    @Transactional
    public DepositResponse create(CreateDepositRequest req) {
        UUID tenantId = requireTenantId();
        UUID actorUserId = requireUserId();

        Contact contact = contactRepository.findByTenant_IdAndId(tenantId, req.contactId())
                .orElseThrow(() -> new ContactNotFoundException(req.contactId()));

        User agent = userRepository.findByTenant_IdAndId(tenantId, actorUserId)
                .orElseThrow(() -> new CrossTenantAccessException("Unknown agent in tenant scope: " + actorUserId));

        Tenant tenant = contact.getTenant();

        UUID propertyId = req.propertyId();
        if (propertyId == null) throw new InvalidDepositRequestException("propertyId is required");

        if (depositRepository.existsByTenant_IdAndContact_IdAndPropertyId(tenantId, contact.getId(), propertyId)) {
            throw new DepositAlreadyExistsException(contact.getId(), propertyId);
        }

        if (depositRepository.existsByTenant_IdAndPropertyIdAndStatusIn(tenantId, propertyId, ACTIVE_STATUSES)) {
            throw new PropertyAlreadyReservedException(propertyId);
        }

        // Lock ordering: Property first to avoid deadlocks with SaleContractService flows.
        Property property = propertyRepository.findByTenantIdAndIdForUpdate(tenantId, propertyId)
                .orElseThrow(() -> new PropertyNotFoundException(propertyId));

        if (property.getStatus() != PropertyStatus.ACTIVE) {
            throw new PropertyAlreadyReservedException(propertyId);
        }

        LocalDate depositDate = (req.depositDate() == null) ? LocalDate.now() : req.depositDate();
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime dueDate = (req.dueDate() == null) ? now.plusDays(7) : req.dueDate();
        String currency = (req.currency() == null) ? "MAD" : req.currency();
        String reference = (req.reference() == null) ? DepositReferenceGenerator.generate(now) : req.reference();

        Deposit deposit = new Deposit(tenant, contact, agent);
        deposit.setPropertyId(propertyId);
        deposit.setAmount(req.amount());
        deposit.setCurrency(currency);
        deposit.setDepositDate(depositDate);
        deposit.setReference(reference);
        deposit.setStatus(DepositStatus.PENDING);
        deposit.setNotes(req.notes());
        deposit.setDueDate(dueDate);

        try {
            Deposit saved = depositRepository.save(deposit);
            depositRepository.flush();

            // Mark property as RESERVED — delegate to canonical commercial workflow
            propertyCommercialWorkflowService.reserve(property, now);

            // Ensure interest link exists (contact remains "interested" even if deposit expires).
            ensureInterestExists(tenantId, tenant, contact.getId(), propertyId);

            // Apply workflow: prospect -> qualified + TEMP_CLIENT until dueDate.
            applyContactReservationWorkflow(contact, actorUserId, dueDate);
            contactRepository.save(contact);

            notificationService.notify(
                    tenant,
                    agent,
                    NotificationType.DEPOSIT_PENDING,
                    saved.getId(),
                    toPayload(Map.of(
                            "depositId", saved.getId(),
                            "contactId", contact.getId(),
                            "propertyId", propertyId,
                            "amount", req.amount(),
                            "currency", currency,
                            "dueDate", dueDate
                    ))
            );

            auditService.record(tenantId, AuditEventType.DEPOSIT_CREATED, actorUserId,
                    "DEPOSIT", saved.getId(), null);

            return toResponse(saved);
        } catch (DataIntegrityViolationException e) {
            // Handle race conditions in a deterministic way.
            if (depositRepository.existsByTenant_IdAndContact_IdAndPropertyId(tenantId, contact.getId(), propertyId)) {
                throw new DepositAlreadyExistsException(contact.getId(), propertyId);
            }
            if (depositRepository.existsByTenant_IdAndPropertyIdAndStatusIn(tenantId, propertyId, ACTIVE_STATUSES)) {
                throw new PropertyAlreadyReservedException(propertyId);
            }
            throw e;
        }
    }

    public DepositResponse get(UUID id) {
        UUID tenantId = requireTenantId();
        Deposit deposit = depositRepository.findByTenant_IdAndId(tenantId, id)
                .orElseThrow(() -> new DepositNotFoundException(id));
        return toResponse(deposit);
    }

    @Transactional
    public DepositResponse confirm(UUID depositId) {
        UUID tenantId = requireTenantId();
        UUID actorUserId = requireUserId();

        Deposit deposit = depositRepository.findByTenant_IdAndId(tenantId, depositId)
                .orElseThrow(() -> new DepositNotFoundException(depositId));

        if (deposit.getStatus() != DepositStatus.PENDING) {
            throw new InvalidDepositStateException("Only PENDING deposits can be confirmed");
        }

        // Lock ordering: Property first to avoid deadlocks with SaleContractService flows.
        // Prevents a concurrent contract signing from marking the property SOLD between
        // our PENDING status check and the deposit save.
        Property property = propertyRepository.findByTenantIdAndIdForUpdate(tenantId, deposit.getPropertyId())
                .orElseThrow(() -> new PropertyNotFoundException(deposit.getPropertyId()));
        if (property.getStatus() == PropertyStatus.SOLD) {
            throw new InvalidDepositStateException(
                    "Cannot confirm deposit: property " + deposit.getPropertyId() + " is already SOLD");
        }

        deposit.setStatus(DepositStatus.CONFIRMED);
        deposit.setConfirmedAt(LocalDateTime.now());

        Contact contact = deposit.getContact();
        contact.setQualified(true);
        contact.setContactType(ContactType.CLIENT);
        contact.setStatus(ContactStatus.CLIENT);
        contact.setTempClientUntil(null);
        contact.markUpdatedBy(actorUserId);
        contactRepository.save(contact);

        // Ensure client_detail exists (future-proofing).
        if (clientDetailRepository.findById(contact.getId()).isEmpty()) {
            ClientDetail cd = new ClientDetail(contact);
            cd.setClientKind(ClientKind.PERSONNE_PHYSIQUE);
            clientDetailRepository.save(cd);        }

        Deposit saved = depositRepository.save(deposit);

        notificationService.notify(
                saved.getTenant(),
                saved.getAgent(),
                NotificationType.DEPOSIT_CONFIRMED,
                saved.getId(),
                toPayload(Map.of("depositId", saved.getId(), "status", saved.getStatus(), "confirmedAt", saved.getConfirmedAt()))
        );

        auditService.record(tenantId, AuditEventType.DEPOSIT_CONFIRMED, actorUserId,
                "DEPOSIT", saved.getId(), null);

        return toResponse(saved);
    }

    @Transactional
    public DepositResponse cancel(UUID depositId) {
        UUID tenantId = requireTenantId();
        UUID actorUserId = requireUserId();

        Deposit deposit = depositRepository.findByTenant_IdAndId(tenantId, depositId)
                .orElseThrow(() -> new DepositNotFoundException(depositId));

        if (deposit.getStatus() == DepositStatus.CANCELLED || deposit.getStatus() == DepositStatus.EXPIRED) {
            throw new InvalidDepositStateException("Deposit already ended: " + deposit.getStatus());
        }
        if (deposit.getStatus() == DepositStatus.CONFIRMED) {
            throw new InvalidDepositStateException("Confirmed deposits cannot be cancelled in MVP");
        }

        deposit.setStatus(DepositStatus.CANCELLED);
        deposit.setCancelledAt(LocalDateTime.now());

        // Release property reservation
        releasePropertyReservation(deposit);

        Contact contact = deposit.getContact();
        // If not a real client, revert to prospect (qualified stays true)
        if (contact.getContactType() != ContactType.CLIENT) {
            contact.setContactType(ContactType.PROSPECT);
            contact.setStatus(ContactStatus.QUALIFIED_PROSPECT);
            contact.setQualified(true);
            contact.setTempClientUntil(null);
            contact.markUpdatedBy(actorUserId);
            contactRepository.save(contact);
        }

        Deposit saved = depositRepository.save(deposit);

        notificationService.notify(
                saved.getTenant(),
                saved.getAgent(),
                NotificationType.DEPOSIT_CANCELLED,
                saved.getId(),
                toPayload(Map.of("depositId", saved.getId(), "status", saved.getStatus(), "cancelledAt", saved.getCancelledAt()))
        );

        auditService.record(tenantId, AuditEventType.DEPOSIT_CANCELED, actorUserId,
                "DEPOSIT", saved.getId(), null);

        return toResponse(saved);
    }

    public DepositReportResponse report(
            DepositStatus status,
            UUID agentId,
            UUID contactId,
            UUID propertyId,
            LocalDateTime from,
            LocalDateTime to
    ) {
        UUID tenantId = requireTenantId();
        List<Deposit> deposits = depositRepository.report(tenantId, status, agentId, contactId, propertyId, from, to);

        List<DepositResponse> items = deposits.stream().map(this::toResponse).toList();
        BigDecimal total = deposits.stream()
                .map(Deposit::getAmount)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        Map<UUID, AgentAgg> byAgent = new LinkedHashMap<>();
        for (Deposit d : deposits) {
            UUID aId = d.getAgent().getId();
            byAgent.computeIfAbsent(aId, k -> new AgentAgg(aId, d.getAgent().getEmail()))
                    .add(d.getAmount());
        }

        List<DepositReportByAgent> agentAggs = byAgent.values().stream()
                .map(a -> new DepositReportByAgent(a.agentId, a.email, a.count, a.total))
                .toList();

        return new DepositReportResponse(items, deposits.size(), total, agentAggs);
    }

    /**
     * Scheduler entrypoint (hourly):
     * - expire overdue pending deposits
     * - notify due soon (within horizon)
     */
    @Transactional
    public void runHourlyWorkflow(Duration dueSoonHorizon) {
        LocalDateTime now = LocalDateTime.now();

        // Expire overdue pending deposits
        List<Deposit> overdue = depositRepository.findAllByStatusAndDueDateBefore(DepositStatus.PENDING, now);
        for (Deposit d : overdue) {
            expireDeposit(d);
        }

        // Notify due soon
        LocalDateTime to = now.plus(dueSoonHorizon);
        List<Deposit> dueSoon = depositRepository.findAllByStatusAndDueDateBetween(DepositStatus.PENDING, now, to);
        for (Deposit d : dueSoon) {
            notificationService.notify(
                    d.getTenant(),
                    d.getAgent(),
                    NotificationType.DEPOSIT_DUE_SOON,
                    d.getId(),
                    toPayload(Map.of(
                            "depositId", d.getId(),
                            "contactId", d.getContact().getId(),
                            "propertyId", d.getPropertyId(),
                            "dueDate", d.getDueDate(),
                            "amount", d.getAmount(),
                            "currency", d.getCurrency()
                    ))
            );
        }
    }

    private void expireDeposit(Deposit d) {
        if (d.getStatus() != DepositStatus.PENDING) return;
        d.setStatus(DepositStatus.EXPIRED);
        d.setCancelledAt(LocalDateTime.now());
        depositRepository.save(d);

        // Release property reservation
        releasePropertyReservation(d);

        Contact contact = d.getContact();
        if (contact.getContactType() != ContactType.CLIENT) {
            contact.setContactType(ContactType.PROSPECT);
            contact.setStatus(ContactStatus.QUALIFIED_PROSPECT); // stays qualified per MVP decision
            contact.setQualified(true);
            contact.setTempClientUntil(null);
            contactRepository.save(contact);
        }

        notificationService.notify(
                d.getTenant(),
                d.getAgent(),
                NotificationType.DEPOSIT_EXPIRED,
                d.getId(),
                toPayload(Map.of("depositId", d.getId(), "status", d.getStatus(), "expiredAt", d.getCancelledAt()))
        );

        auditService.record(d.getTenant().getId(), AuditEventType.DEPOSIT_EXPIRED,
                d.getAgent().getId(), "DEPOSIT", d.getId(), null);
    }

    private void releasePropertyReservation(Deposit deposit) {
        if (deposit.getPropertyId() == null) return;
        propertyRepository.findByTenant_IdAndId(deposit.getTenant().getId(), deposit.getPropertyId())
                .ifPresent(property -> {
                    if (property.getStatus() == PropertyStatus.RESERVED) {
                        propertyCommercialWorkflowService.releaseReservation(property);
                    }
                });
    }

    private void ensureInterestExists(UUID tenantId, Tenant tenant, UUID contactId, UUID propertyId) {
        if (!contactInterestRepository.existsByTenant_IdAndContactIdAndPropertyId(tenantId, contactId, propertyId)) {
            contactInterestRepository.save(new ContactInterest(tenant, contactId, propertyId, InterestStatus.NEW));
        }
    }

    private void applyContactReservationWorkflow(Contact contact, UUID actorUserId, LocalDateTime dueDate) {
        contact.setQualified(true);
        contact.setStatus(ContactStatus.QUALIFIED_PROSPECT);
        if (contact.getContactType() != ContactType.CLIENT) {
            contact.setContactType(ContactType.TEMP_CLIENT);
            contact.setTempClientUntil(dueDate);
        }
        contact.markUpdatedBy(actorUserId);
    }

    private UUID requireTenantId() {
        UUID tenantId = TenantContext.getTenantId();
        if (tenantId == null) throw new CrossTenantAccessException("Missing tenant context");
        return tenantId;
    }

    private UUID requireUserId() {
        UUID userId = TenantContext.getUserId();
        if (userId == null) throw new CrossTenantAccessException("Missing user context");
        return userId;
    }

    private DepositResponse toResponse(Deposit d) {
        return new DepositResponse(
                d.getId(),
                d.getContact().getId(),
                d.getPropertyId(),
                d.getAgent().getId(),
                d.getAmount(),
                d.getCurrency(),
                d.getDepositDate(),
                d.getReference(),
                d.getStatus(),
                d.getNotes(),
                d.getDueDate(),
                d.getConfirmedAt(),
                d.getCancelledAt(),
                d.getCreatedAt(),
                d.getUpdatedAt()
        );
    }

    private String toPayload(Map<String, Object> payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            // Never fail the business flow for a notification payload
            return "{}";
        }
    }

    private static final class AgentAgg {
        final UUID agentId;
        final String email;
        long count;
        BigDecimal total = BigDecimal.ZERO;

        AgentAgg(UUID agentId, String email) {
            this.agentId = agentId;
            this.email = email;
        }

        void add(BigDecimal amount) {
            count++;
            if (amount != null) total = total.add(amount);
        }
    }
}
