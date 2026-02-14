package com.yem.hlm.backend.contact.service;

import com.yem.hlm.backend.contact.api.dto.*;
import com.yem.hlm.backend.contact.domain.*;
import com.yem.hlm.backend.contact.repo.ContactInterestRepository;
import com.yem.hlm.backend.contact.repo.ContactRepository;
import com.yem.hlm.backend.contact.repo.ProspectDetailRepository;
import com.yem.hlm.backend.deposit.service.DepositService;
import com.yem.hlm.backend.tenant.context.TenantContext;
import com.yem.hlm.backend.tenant.domain.Tenant;
import com.yem.hlm.backend.tenant.repo.TenantRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Service
public class ContactService {

    private final ContactRepository contactRepository;
    private final ContactInterestRepository contactInterestRepository;
    private final TenantRepository tenantRepository;
    private final ProspectDetailRepository prospectDetailRepository;
    private final DepositService depositService;

    public ContactService(
            ContactRepository contactRepository,
            ContactInterestRepository contactInterestRepository,
            TenantRepository tenantRepository,
            ProspectDetailRepository prospectDetailRepository,
            DepositService depositService
    ) {
        this.contactRepository = contactRepository;
        this.contactInterestRepository = contactInterestRepository;
        this.tenantRepository = tenantRepository;
        this.prospectDetailRepository = prospectDetailRepository;
        this.depositService = depositService;
    }

    public ContactResponse create(CreateContactRequest req) {
        Tenant tenant = requireTenantEntity();
        UUID actorUserId = requireUserId();

        if (req.email() != null && contactRepository.existsByTenant_IdAndEmail(tenant.getId(), req.email())) {
            throw new ContactEmailAlreadyExistsException(req.email());
        }

        Contact contact = new Contact(tenant, actorUserId, req.firstName(), req.lastName());
        contact.setPhone(req.phone());
        contact.setEmail(req.email());
        contact.setNationalId(req.nationalId());
        contact.setAddress(req.address());
        contact.setNotes(req.notes());

        Contact saved = contactRepository.save(contact);
        // Ensure 1-1 row exists for MVP (future-proofing)
        prospectDetailRepository.save(new ProspectDetail(saved));
        return toResponse(saved);
    }

    public ContactResponse get(UUID contactId) {
        UUID tenantId = requireTenantId();
        Contact contact = contactRepository.findByTenant_IdAndId(tenantId, contactId)
                .orElseThrow(() -> new ContactNotFoundException(contactId));
        return toResponse(contact);
    }

    public Page<ContactResponse> list(List<ContactType> contactTypes, ContactStatus status, String q, Pageable pageable) {
        UUID tenantId = requireTenantId();
        String query = (q == null || q.isBlank()) ? null : q.trim();
        boolean filterByType = contactTypes != null && !contactTypes.isEmpty();
        return contactRepository.search(tenantId, filterByType, filterByType ? contactTypes : List.of(), status, query, pageable)
                .map(this::toResponse);
    }

    public ContactResponse update(UUID contactId, UpdateContactRequest req) {
        UUID tenantId = requireTenantId();
        UUID actorUserId = requireUserId();
        Contact contact = contactRepository.findByTenant_IdAndId(tenantId, contactId)
                .orElseThrow(() -> new ContactNotFoundException(contactId));

        if (req.email() != null && contactRepository.existsByTenant_IdAndEmailAndIdNot(tenantId, req.email(), contactId)) {
            throw new ContactEmailAlreadyExistsException(req.email());
        }

        if (req.firstName() != null) contact.setFirstName(req.firstName());
        if (req.lastName() != null) contact.setLastName(req.lastName());
        if (req.phone() != null) contact.setPhone(req.phone());
        if (req.email() != null) contact.setEmail(req.email());
        if (req.nationalId() != null) contact.setNationalId(req.nationalId());
        if (req.address() != null) contact.setAddress(req.address());
        if (req.notes() != null) contact.setNotes(req.notes());

        // IMPORTANT: status/type/qualified/tempClientUntil are not editable here.
        contact.markUpdatedBy(actorUserId);

        Contact saved = contactRepository.save(contact);
        return toResponse(saved);
    }

    public ContactResponse updateStatus(UUID contactId, ContactStatus newStatus) {
        UUID tenantId = requireTenantId();
        UUID actorUserId = requireUserId();
        Contact contact = contactRepository.findByTenant_IdAndId(tenantId, contactId)
                .orElseThrow(() -> new ContactNotFoundException(contactId));

        contact.setStatus(newStatus);
        contact.markUpdatedBy(actorUserId);

        Contact saved = contactRepository.save(contact);
        return toResponse(saved);
    }

    @Transactional
    public ContactResponse convertToClient(UUID contactId, ConvertToClientRequest req) {
        // Backward compatible endpoint: now creates a deposit reservation.
        depositService.createReservationForContact(contactId, req);
        return get(contactId);
    }

    @Transactional
    public void addInterest(UUID contactId, ContactInterestRequest req) {
        Tenant tenant = requireTenantEntity();
        UUID tenantId = tenant.getId();

        // ensure contact exists in tenant scope
        contactRepository.findByTenant_IdAndId(tenantId, contactId)
                .orElseThrow(() -> new ContactNotFoundException(contactId));

        UUID propertyId = req.propertyId();

        // TODO: once property module exists, verify property belongs to tenantId
        // e.g. propertyRepository.existsByTenant_IdAndId(tenantId, propertyId)

        if (contactInterestRepository.existsByTenant_IdAndContactIdAndPropertyId(tenantId, contactId, propertyId)) {
            throw new ContactInterestAlreadyExistsException(contactId, propertyId);
        }

        InterestStatus status = (req.interestStatus() == null) ? InterestStatus.NEW : req.interestStatus();
        contactInterestRepository.save(new ContactInterest(tenant, contactId, propertyId, status));
    }

    @Transactional
    public void removeInterest(UUID contactId, UUID propertyId) {
        UUID tenantId = requireTenantId();

        ContactInterest interest = contactInterestRepository
                .findByTenant_IdAndContactIdAndPropertyId(tenantId, contactId, propertyId)
                .orElseThrow(() -> new ContactInterestNotFoundException(contactId, propertyId));

        contactInterestRepository.delete(interest);
    }

    public List<ContactInterestResponse> listInterestsForContact(UUID contactId) {
        UUID tenantId = requireTenantId();

        // ensure contact exists
        contactRepository.findByTenant_IdAndId(tenantId, contactId)
                .orElseThrow(() -> new ContactNotFoundException(contactId));

        return contactInterestRepository.findAllByTenant_IdAndContactId(tenantId, contactId)
                .stream()
                .map(i -> new ContactInterestResponse(i.getPropertyId(), i.getInterestStatus(), i.getCreatedAt()))
                .toList();
    }

    public List<UUID> listContactsForProperty(UUID propertyId) {
        UUID tenantId = requireTenantId();
        return contactInterestRepository.findAllByTenant_IdAndPropertyId(tenantId, propertyId)
                .stream()
                .map(ContactInterest::getContactId)
                .distinct()
                .toList();
    }

    // Conversion validation now happens in DepositService.

    private UUID requireTenantId() {
        UUID tenantId = TenantContext.getTenantId();
        if (tenantId == null) {
            throw new CrossTenantAccessException("Missing tenant context");
        }
        return tenantId;
    }

    private UUID requireUserId() {
        UUID userId = TenantContext.getUserId();
        if (userId == null) {
            throw new CrossTenantAccessException("Missing user context");
        }
        return userId;
    }

    private Tenant requireTenantEntity() {
        UUID tenantId = requireTenantId();
        return tenantRepository.findById(tenantId)
                .orElseThrow(() -> new CrossTenantAccessException("Unknown tenant: " + tenantId));
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
                c.getUpdatedAt()
        );
    }
}
