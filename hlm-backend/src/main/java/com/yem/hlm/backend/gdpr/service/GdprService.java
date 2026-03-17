package com.yem.hlm.backend.gdpr.service;

import com.yem.hlm.backend.contact.domain.Contact;
import com.yem.hlm.backend.contact.repo.ContactRepository;
import com.yem.hlm.backend.contact.service.CrossTenantAccessException;
import com.yem.hlm.backend.gdpr.api.dto.DataExportResponse;
import com.yem.hlm.backend.gdpr.api.dto.RectifyContactResponse;
import com.yem.hlm.backend.tenant.context.TenantContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Orchestrates GDPR data subject rights operations (Art. 15-17).
 *
 * <p>All operations are tenant-scoped via {@link TenantContext}.
 */
@Service
@Transactional(readOnly = true)
public class GdprService {

    private final ContactRepository contactRepo;
    private final DataExportBuilder exportBuilder;
    private final AnonymizationService anonymizationService;

    public GdprService(ContactRepository contactRepo,
                       DataExportBuilder exportBuilder,
                       AnonymizationService anonymizationService) {
        this.contactRepo = contactRepo;
        this.exportBuilder = exportBuilder;
        this.anonymizationService = anonymizationService;
    }

    /**
     * GDPR Art. 15 / Art. 20: Export all personal data for one contact.
     *
     * @throws GdprExportNotFoundException if the contact is not found within the tenant
     */
    public DataExportResponse exportContact(UUID contactId) {
        UUID tenantId = requireTenantId();
        UUID actorUserId = requireUserId();
        Contact contact = contactRepo.findByTenant_IdAndId(tenantId, contactId)
                .orElseThrow(() -> new GdprExportNotFoundException(contactId));
        return exportBuilder.build(contact, actorUserId);
    }

    /**
     * GDPR Art. 17: Anonymize (erase) a contact's personal data.
     *
     * @throws GdprExportNotFoundException   if the contact is not found within the tenant
     * @throws GdprErasureBlockedException   if SIGNED contracts prevent erasure
     */
    @Transactional
    public void anonymizeContact(UUID contactId) {
        UUID tenantId = requireTenantId();
        UUID actorUserId = requireUserId();
        Contact contact = contactRepo.findByTenant_IdAndId(tenantId, contactId)
                .orElseThrow(() -> new GdprExportNotFoundException(contactId));
        anonymizationService.anonymize(contact, actorUserId);
    }

    /**
     * GDPR Art. 16: Return current mutable personal fields to support the rectification workflow.
     * Actual rectification is performed via {@code PATCH /api/contacts/{id}}.
     *
     * @throws GdprExportNotFoundException if the contact is not found within the tenant
     */
    public RectifyContactResponse getRectifyView(UUID contactId) {
        UUID tenantId = requireTenantId();
        Contact contact = contactRepo.findByTenant_IdAndId(tenantId, contactId)
                .orElseThrow(() -> new GdprExportNotFoundException(contactId));
        return new RectifyContactResponse(
                contact.getId(),
                contact.getFullName(),
                contact.getEmail(),
                contact.getPhone(),
                contact.isConsentGiven(),
                contact.getConsentMethod(),
                contact.getProcessingBasis()
        );
    }

    private UUID requireTenantId() {
        UUID id = TenantContext.getTenantId();
        if (id == null) throw new CrossTenantAccessException("Missing tenant context");
        return id;
    }

    private UUID requireUserId() {
        UUID id = TenantContext.getUserId();
        if (id == null) throw new CrossTenantAccessException("Missing user context");
        return id;
    }
}
