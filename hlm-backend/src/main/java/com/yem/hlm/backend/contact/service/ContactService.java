package com.yem.hlm.backend.contact.service;

import com.yem.hlm.backend.audit.domain.AuditEventType;
import com.yem.hlm.backend.audit.service.CommercialAuditService;
import com.yem.hlm.backend.common.error.ErrorCode;
import com.yem.hlm.backend.common.event.ContactCreatedEvent;
import com.yem.hlm.backend.common.event.ContactStatusChangedEvent;
import com.yem.hlm.backend.contact.api.dto.*;
import com.yem.hlm.backend.usermanagement.exception.BusinessRuleException;
import com.yem.hlm.backend.contact.api.dto.ConvertToProspectRequest;
import com.yem.hlm.backend.contact.domain.*;
import com.yem.hlm.backend.contact.repo.ContactInterestRepository;
import com.yem.hlm.backend.contact.repo.ContactRepository;
import com.yem.hlm.backend.contact.repo.ProspectDetailRepository;
import com.yem.hlm.backend.deposit.service.DepositService;
import com.yem.hlm.backend.property.repo.PropertyRepository;
import com.yem.hlm.backend.property.service.PropertyNotFoundException;
import com.yem.hlm.backend.societe.SocieteContext;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class ContactService {

    private final ContactRepository contactRepository;
    private final ContactInterestRepository contactInterestRepository;
    private final ProspectDetailRepository prospectDetailRepository;
    private final PropertyRepository propertyRepository;
    private final DepositService depositService;
    private final ApplicationEventPublisher eventPublisher;
    private final CommercialAuditService auditService;

    public ContactService(
            ContactRepository contactRepository,
            ContactInterestRepository contactInterestRepository,
            ProspectDetailRepository prospectDetailRepository,
            PropertyRepository propertyRepository,
            DepositService depositService,
            ApplicationEventPublisher eventPublisher,
            CommercialAuditService auditService
    ) {
        this.contactRepository = contactRepository;
        this.contactInterestRepository = contactInterestRepository;
        this.prospectDetailRepository = prospectDetailRepository;
        this.propertyRepository = propertyRepository;
        this.depositService = depositService;
        this.eventPublisher = eventPublisher;
        this.auditService = auditService;
    }

    public ContactResponse create(CreateContactRequest req) {
        UUID societeId = requireSocieteId();
        UUID actorUserId = requireUserId();

        if (req.email() != null && contactRepository.existsBySocieteIdAndEmail(societeId, req.email())) {
            throw new ContactEmailAlreadyExistsException(req.email());
        }

        // GDPR / Loi 09-08: require either explicit consent or a stated legal basis
        if (!Boolean.TRUE.equals(req.consentGiven()) && req.processingBasis() == null) {
            throw new BusinessRuleException(ErrorCode.CONSENT_REQUIRED,
                    "Le consentement ou une base juridique est requis (Loi 09-08 Art. 4 / RGPD Art. 6).");
        }

        Contact contact = new Contact(societeId, actorUserId, req.firstName(), req.lastName());
        contact.setPhone(req.phone());
        contact.setEmail(req.email());
        contact.setNationalId(req.nationalId());
        contact.setAddress(req.address());
        contact.setNotes(req.notes());

        // GDPR consent fields
        boolean givenConsent = Boolean.TRUE.equals(req.consentGiven());
        contact.setConsentGiven(givenConsent);
        if (givenConsent) {
            contact.setConsentDate(Instant.now());
        }
        if (req.consentMethod() != null) contact.setConsentMethod(req.consentMethod());
        if (req.processingBasis() != null) contact.setProcessingBasis(req.processingBasis());

        Contact saved = contactRepository.save(contact);
        // Ensure 1-1 row exists for MVP (future-proofing)
        prospectDetailRepository.save(new ProspectDetail(saved));
        eventPublisher.publishEvent(new ContactCreatedEvent(societeId, actorUserId, saved.getId(), saved.getFullName()));
        return toResponse(saved);
    }

    public ContactResponse get(UUID contactId) {
        UUID societeId = requireSocieteId();
        Contact contact = contactRepository.findBySocieteIdAndId(societeId, contactId)
                .orElseThrow(() -> new ContactNotFoundException(contactId));
        return toResponse(contact);
    }

    public Page<ContactResponse> list(List<ContactType> contactTypes, ContactStatus status, String q, Pageable pageable) {
        UUID societeId = requireSocieteId();
        String query = (q == null || q.isBlank()) ? null : q.trim();
        boolean filterByType = contactTypes != null && !contactTypes.isEmpty();
        return contactRepository.search(societeId, filterByType, filterByType ? contactTypes : List.of(), status, query, pageable)
                .map(this::toResponse);
    }

    public ContactResponse update(UUID contactId, UpdateContactRequest req) {
        UUID societeId = requireSocieteId();
        UUID actorUserId = requireUserId();
        Contact contact = contactRepository.findBySocieteIdAndId(societeId, contactId)
                .orElseThrow(() -> new ContactNotFoundException(contactId));

        if (req.email() != null && contactRepository.existsBySocieteIdAndEmailAndIdNot(societeId, req.email(), contactId)) {
            throw new ContactEmailAlreadyExistsException(req.email());
        }

        if (req.firstName() != null) contact.setFirstName(req.firstName());
        if (req.lastName() != null) contact.setLastName(req.lastName());
        if (req.phone() != null) contact.setPhone(req.phone());
        if (req.email() != null) contact.setEmail(req.email());
        if (req.nationalId() != null) contact.setNationalId(req.nationalId());
        if (req.address() != null) contact.setAddress(req.address());
        if (req.notes() != null) contact.setNotes(req.notes());

        // GDPR consent fields — only update when explicitly provided
        if (req.consentGiven() != null) {
            boolean nowConsenting = Boolean.TRUE.equals(req.consentGiven());
            boolean changed = nowConsenting != contact.isConsentGiven();
            // Record consent date only when transitioning to true
            if (nowConsenting && !contact.isConsentGiven()) {
                contact.setConsentDate(Instant.now());
            }
            contact.setConsentGiven(nowConsenting);
            if (changed) {
                String payload = String.format(
                        "{\"oldValue\":%b,\"newValue\":%b,\"method\":\"%s\",\"basis\":\"%s\"}",
                        !nowConsenting, nowConsenting,
                        req.consentMethod() != null ? req.consentMethod() : "",
                        req.processingBasis() != null ? req.processingBasis() : "");
                auditService.record(societeId, AuditEventType.CONSENT_CHANGED, actorUserId,
                        "CONTACT", contactId, payload);
            }
        }
        if (req.consentMethod() != null) contact.setConsentMethod(req.consentMethod());
        if (req.processingBasis() != null) contact.setProcessingBasis(req.processingBasis());

        // IMPORTANT: status/type/qualified/tempClientUntil are not editable here.
        contact.markUpdatedBy(actorUserId);

        Contact saved = contactRepository.save(contact);
        return toResponse(saved);
    }

    public ContactResponse updateStatus(UUID contactId, ContactStatus newStatus) {
        UUID societeId = requireSocieteId();
        UUID actorUserId = requireUserId();
        Contact contact = contactRepository.findBySocieteIdAndId(societeId, contactId)
                .orElseThrow(() -> new ContactNotFoundException(contactId));

        ContactStatus current = contact.getStatus();
        if (!current.canTransitionTo(newStatus)) {
            throw new InvalidStatusTransitionException(current, newStatus);
        }

        contact.setStatus(newStatus);
        contact.markUpdatedBy(actorUserId);

        Contact saved = contactRepository.save(contact);
        eventPublisher.publishEvent(new ContactStatusChangedEvent(societeId, actorUserId, saved.getId(), current, newStatus));
        return toResponse(saved);
    }

    /**
     * Qualifies a contact as a prospect: sets status to QUALIFIED_PROSPECT
     * and upserts ProspectDetail with the supplied budget/source enrichment.
     */
    @Transactional
    public ContactResponse convertToProspect(UUID contactId, ConvertToProspectRequest req) {
        UUID societeId = requireSocieteId();
        UUID actorUserId = requireUserId();

        Contact contact = contactRepository.findBySocieteIdAndId(societeId, contactId)
                .orElseThrow(() -> new ContactNotFoundException(contactId));

        ContactStatus current = contact.getStatus();
        if (current == ContactStatus.LOST) {
            contact.setStatus(ContactStatus.PROSPECT);
        } else if (current == ContactStatus.PROSPECT) {
            contact.setStatus(ContactStatus.QUALIFIED_PROSPECT);
        } else if (current != ContactStatus.QUALIFIED_PROSPECT) {
            // Already a client — silently enrich ProspectDetail but do not demote status
        }
        contact.setQualified(true);
        contact.markUpdatedBy(actorUserId);
        contactRepository.save(contact);

        // Upsert ProspectDetail
        ProspectDetail pd = prospectDetailRepository.findById(contactId)
                .orElseGet(() -> new ProspectDetail(contact));
        if (req.budgetMin() != null) pd.setBudgetMin(req.budgetMin());
        if (req.budgetMax() != null) pd.setBudgetMax(req.budgetMax());
        if (req.source() != null && !req.source().isBlank()) pd.setSource(req.source().trim());
        if (req.notes() != null && !req.notes().isBlank()) pd.setNotes(req.notes().trim());
        prospectDetailRepository.save(pd);

        return get(contactId);
    }

    @Transactional
    public ContactResponse convertToClient(UUID contactId, ConvertToClientRequest req) {
        // Backward compatible endpoint: now creates a deposit reservation.
        depositService.createReservationForContact(contactId, req);
        return get(contactId);
    }

    @Transactional
    public void addInterest(UUID contactId, ContactInterestRequest req) {
        UUID societeId = requireSocieteId();

        // ensure contact exists in société scope
        contactRepository.findBySocieteIdAndId(societeId, contactId)
                .orElseThrow(() -> new ContactNotFoundException(contactId));

        UUID propertyId = req.propertyId();

        // Verify property exists and belongs to current société
        propertyRepository.findBySocieteIdAndIdAndDeletedAtIsNull(societeId, propertyId)
                .orElseThrow(() -> new PropertyNotFoundException(propertyId));

        if (contactInterestRepository.existsBySocieteIdAndContactIdAndPropertyId(societeId, contactId, propertyId)) {
            throw new ContactInterestAlreadyExistsException(contactId, propertyId);
        }

        InterestStatus status = (req.interestStatus() == null) ? InterestStatus.NEW : req.interestStatus();
        contactInterestRepository.save(new ContactInterest(societeId, contactId, propertyId, status));
    }

    @Transactional
    public void removeInterest(UUID contactId, UUID propertyId) {
        UUID societeId = requireSocieteId();

        ContactInterest interest = contactInterestRepository
                .findBySocieteIdAndContactIdAndPropertyId(societeId, contactId, propertyId)
                .orElseThrow(() -> new ContactInterestNotFoundException(contactId, propertyId));

        contactInterestRepository.delete(interest);
    }

    public List<ContactInterestResponse> listInterestsForContact(UUID contactId) {
        UUID societeId = requireSocieteId();

        // ensure contact exists
        contactRepository.findBySocieteIdAndId(societeId, contactId)
                .orElseThrow(() -> new ContactNotFoundException(contactId));

        return contactInterestRepository.findAllBySocieteIdAndContactId(societeId, contactId)
                .stream()
                .map(i -> new ContactInterestResponse(i.getPropertyId(), i.getInterestStatus(), i.getCreatedAt()))
                .toList();
    }

    public List<UUID> listContactsForProperty(UUID propertyId) {
        UUID societeId = requireSocieteId();
        return contactInterestRepository.findAllBySocieteIdAndPropertyId(societeId, propertyId)
                .stream()
                .map(ContactInterest::getContactId)
                .distinct()
                .toList();
    }

    private UUID requireSocieteId() {
        UUID societeId = SocieteContext.getSocieteId();
        if (societeId == null) {
            throw new CrossTenantAccessException("Missing société context");
        }
        return societeId;
    }

    private UUID requireUserId() {
        UUID userId = SocieteContext.getUserId();
        if (userId == null) {
            throw new CrossTenantAccessException("Missing user context");
        }
        return userId;
    }

    private ContactResponse toResponse(Contact c) {
        return new ContactResponse(
                c.getId(),
                c.getContactType(),
                c.getStatus(),
                c.isQualified(),
                c.getTempClientUntil(),
                c.getFirstName(),
                c.getLastName(),
                c.getFullName(),
                c.getPhone(),
                c.getEmail(),
                c.getNationalId(),
                c.getAddress(),
                c.getNotes(),
                c.getCreatedAt(),
                c.getUpdatedAt(),
                c.isConsentGiven(),
                c.getConsentDate(),
                c.getConsentMethod(),
                c.getProcessingBasis()
        );
    }
}
