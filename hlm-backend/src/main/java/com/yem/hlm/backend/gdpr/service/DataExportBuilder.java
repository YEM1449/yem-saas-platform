package com.yem.hlm.backend.gdpr.service;

import com.yem.hlm.backend.audit.repo.CommercialAuditRepository;
import com.yem.hlm.backend.contact.domain.Contact;
import com.yem.hlm.backend.contact.repo.ContactInterestRepository;
import com.yem.hlm.backend.contract.repo.SaleContractRepository;
import com.yem.hlm.backend.deposit.repo.DepositRepository;
import com.yem.hlm.backend.gdpr.api.dto.DataExportResponse;
import com.yem.hlm.backend.gdpr.api.dto.DataExportResponse.AuditEventExport;
import com.yem.hlm.backend.gdpr.api.dto.DataExportResponse.ContractExport;
import com.yem.hlm.backend.gdpr.api.dto.DataExportResponse.DepositExport;
import com.yem.hlm.backend.gdpr.api.dto.DataExportResponse.InterestExport;
import com.yem.hlm.backend.gdpr.api.dto.DataExportResponse.MessageExport;
import com.yem.hlm.backend.outbox.repo.OutboundMessageRepository;
import com.yem.hlm.backend.property.repo.PropertyRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Assembles a complete personal-data export (GDPR Art. 15 / Art. 20 portability) for a contact.
 * All queries are tenant-scoped to prevent cross-tenant data leakage.
 */
@Component
public class DataExportBuilder {

    private final ContactInterestRepository interestRepo;
    private final DepositRepository depositRepo;
    private final SaleContractRepository contractRepo;
    private final CommercialAuditRepository auditRepo;
    private final OutboundMessageRepository messageRepo;
    private final PropertyRepository propertyRepo;

    public DataExportBuilder(
            ContactInterestRepository interestRepo,
            DepositRepository depositRepo,
            SaleContractRepository contractRepo,
            CommercialAuditRepository auditRepo,
            OutboundMessageRepository messageRepo,
            PropertyRepository propertyRepo
    ) {
        this.interestRepo = interestRepo;
        this.depositRepo = depositRepo;
        this.contractRepo = contractRepo;
        this.auditRepo = auditRepo;
        this.messageRepo = messageRepo;
        this.propertyRepo = propertyRepo;
    }

    /**
     * Builds the data export for the given contact.
     *
     * @param contact     the resolved contact entity (already tenant-checked)
     * @param actorUserId the CRM user triggering the export (for audit trail)
     */
    @Transactional(readOnly = true)
    public DataExportResponse build(Contact contact, UUID actorUserId) {
        UUID tenantId = contact.getTenant().getId();
        UUID contactId = contact.getId();

        // Interests
        List<InterestExport> interests = interestRepo
                .findAllByTenant_IdAndContactId(tenantId, contactId)
                .stream()
                .map(i -> {
                    String refCode = propertyRepo
                            .findById(i.getPropertyId())
                            .map(p -> p.getReferenceCode())
                            .orElse(null);
                    return new InterestExport(i.getPropertyId(), refCode, i.getCreatedAt());
                })
                .toList();

        // Deposits (any status — all are personal records)
        List<DepositExport> deposits = depositRepo
                .report(tenantId, null, null, contactId, null, null, null)
                .stream()
                .map(d -> new DepositExport(
                        d.getId(), d.getAmount(), d.getCurrency(),
                        d.getStatus().name(), d.getCreatedAt()))
                .toList();

        // Contracts for this contact
        List<ContractExport> contracts = contractRepo
                .findPortalContracts(tenantId, contactId)
                .stream()
                .map(c -> new ContractExport(
                        c.getId(), c.getAgreedPrice(),
                        c.getStatus().name(), c.getSignedAt()))
                .toList();

        // Audit events correlated with the contact (correlation type = "CONTACT")
        List<AuditEventExport> auditEvents = auditRepo
                .search(tenantId, null, null, "CONTACT", contactId, PageRequest.of(0, 500))
                .stream()
                .map(e -> new AuditEventExport(e.getEventType().name(), e.getOccurredAt()))
                .toList();

        // Outbound messages correlated with the contact
        List<MessageExport> messages = messageRepo
                .findByTenant(tenantId, null, null, contactId, null, null, PageRequest.of(0, 500))
                .stream()
                .map(m -> new MessageExport(m.getChannel().name(), m.getCreatedAt(), m.getStatus().name()))
                .toList();

        return new DataExportResponse(
                contactId,
                contact.getFullName(),
                contact.getEmail(),
                contact.getPhone(),
                contact.getStatus(),
                contact.getContactType(),
                contact.isConsentGiven(),
                contact.getConsentDate(),
                contact.getConsentMethod(),
                contact.getProcessingBasis(),
                interests,
                deposits,
                contracts,
                auditEvents,
                messages,
                Instant.now(),
                actorUserId
        );
    }
}
