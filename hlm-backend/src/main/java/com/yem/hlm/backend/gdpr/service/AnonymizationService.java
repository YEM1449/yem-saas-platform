package com.yem.hlm.backend.gdpr.service;

import com.yem.hlm.backend.audit.domain.AuditEventType;
import com.yem.hlm.backend.audit.service.CommercialAuditService;
import com.yem.hlm.backend.contact.domain.Contact;
import com.yem.hlm.backend.contact.repo.ClientDetailRepository;
import com.yem.hlm.backend.contact.repo.ProspectDetailRepository;
import com.yem.hlm.backend.contract.domain.SaleContract;
import com.yem.hlm.backend.contract.domain.SaleContractStatus;
import com.yem.hlm.backend.contract.repo.SaleContractRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Implements the GDPR Right to Erasure (Art. 17) / Law 09-08 Art. 7.
 *
 * <h3>Business rules</h3>
 * <ul>
 *   <li>Contacts with SIGNED contracts cannot be anonymized (legal archive obligation).
 *       Returns a {@link GdprErasureBlockedException} listing the blocking contract IDs.</li>
 *   <li>Contacts with only DRAFT/CANCELED contracts or no contracts are anonymized:
 *       PII fields are zeroed and {@code deleted=true, anonymizedAt=NOW()} are set.</li>
 *   <li>DRAFT contract buyer snapshots are also zeroed (no legal obligation to keep them).</li>
 *   <li>Deposit rows and SIGNED contract rows are preserved (amount/currency not PII;
 *       SIGNED contracts are legal archive records).</li>
 * </ul>
 */
@Service
public class AnonymizationService {

    private static final Logger log = LoggerFactory.getLogger(AnonymizationService.class);

    private final SaleContractRepository contractRepo;
    private final ProspectDetailRepository prospectDetailRepo;
    private final ClientDetailRepository clientDetailRepo;
    private final CommercialAuditService auditService;

    public AnonymizationService(
            SaleContractRepository contractRepo,
            ProspectDetailRepository prospectDetailRepo,
            ClientDetailRepository clientDetailRepo,
            CommercialAuditService auditService
    ) {
        this.contractRepo = contractRepo;
        this.prospectDetailRepo = prospectDetailRepo;
        this.clientDetailRepo = clientDetailRepo;
        this.auditService = auditService;
    }

    /**
     * Anonymizes the given contact.
     * <p>
     * If the contact is already anonymized, this is a no-op (idempotent).
     *
     * @param contact     the contact entity to anonymize (already tenant-checked)
     * @param actorUserId the CRM admin triggering erasure (for audit trail)
     * @throws GdprErasureBlockedException if any SIGNED contract exists for this contact
     */
    @Transactional
    public void anonymize(Contact contact, UUID actorUserId) {
        UUID societeId = contact.getSocieteId();
        UUID contactId = contact.getId();

        // Idempotent guard
        if (contact.getAnonymizedAt() != null) {
            log.info("[GDPR] Contact {} already anonymized — no-op", contactId);
            return;
        }

        // Check for signed contracts — cannot erase
        List<SaleContract> contracts = contractRepo.findPortalContracts(societeId, contactId);
        List<UUID> signedContractIds = contracts.stream()
                .filter(c -> c.getStatus() == SaleContractStatus.SIGNED)
                .map(SaleContract::getId)
                .toList();

        if (!signedContractIds.isEmpty()) {
            throw new GdprErasureBlockedException(signedContractIds);
        }

        // Zero PII on DRAFT contracts (legal obligation does not yet exist)
        contracts.stream()
                .filter(c -> c.getStatus() == SaleContractStatus.DRAFT)
                .forEach(c -> {
                    c.setBuyerDisplayName("ANONYMIZED");
                    c.setBuyerEmail(null);
                    c.setBuyerPhone(null);
                    c.setBuyerIce(null);
                    c.setBuyerAddress(null);
                });

        // Anonymize contact main row
        contact.setFullName("ANONYMIZED");
        contact.setFirstName("ANONYMIZED");
        contact.setLastName("");
        contact.setEmail("anon-" + UUID.randomUUID() + "@anonymized.invalid");
        contact.setPhone(null);
        contact.setNationalId(null);
        contact.setAddress(null);
        contact.setNotes(null);
        contact.setConsentGiven(false);
        contact.setConsentDate(null);
        contact.setDeleted(true);
        contact.setAnonymizedAt(Instant.now());
        contact.markUpdatedBy(actorUserId);

        // Zero PII on prospect_detail
        prospectDetailRepo.findById(contactId).ifPresent(pd -> {
            pd.setNotes(null);
            pd.setSource(null);
            prospectDetailRepo.save(pd);
        });

        // Zero PII on client_detail
        clientDetailRepo.findBySocieteIdAndContactId(societeId, contactId).ifPresent(cd -> {
            cd.setCompanyName(null);
            cd.setIce(null);
            cd.setSiret(null);
            clientDetailRepo.save(cd);
        });

        // Write audit event
        auditService.record(societeId, AuditEventType.CONTACT_ANONYMIZED,
                actorUserId, "CONTACT", contactId, null);

        log.info("[GDPR] Contact {} anonymized by user {} in société {}", contactId, actorUserId, societeId);
    }
}
