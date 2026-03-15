package com.yem.hlm.backend.gdpr.scheduler;

import com.yem.hlm.backend.contact.domain.Contact;
import com.yem.hlm.backend.contact.repo.ContactRepository;
import com.yem.hlm.backend.gdpr.service.AnonymizationService;
import com.yem.hlm.backend.gdpr.service.GdprErasureBlockedException;
import com.yem.hlm.backend.tenant.domain.Tenant;
import com.yem.hlm.backend.tenant.repo.TenantRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Daily data retention sweep (GDPR Art. 5(1)(e) / Law 09-08 Art. 4).
 *
 * <p>Runs at 02:00 by default ({@code DATA_RETENTION_CRON}).
 * For each tenant, finds soft-deleted contacts whose {@code updatedAt} is older than the
 * effective retention window and anonymizes them — unless SIGNED contracts block erasure
 * (legal archive obligation; a WARN is logged and the contact is skipped).
 */
@Component
@ConditionalOnProperty("spring.task.scheduling.enabled")
public class DataRetentionScheduler {

    private static final Logger log = LoggerFactory.getLogger(DataRetentionScheduler.class);

    /** System actor UUID used for automated anonymization audit events. */
    private static final UUID SYSTEM_ACTOR = new UUID(0L, 0L);

    @Value("${app.gdpr.default-retention-days:1825}")
    private int defaultRetentionDays;

    private final TenantRepository tenantRepo;
    private final ContactRepository contactRepo;
    private final AnonymizationService anonymizationService;

    public DataRetentionScheduler(TenantRepository tenantRepo,
                                  ContactRepository contactRepo,
                                  AnonymizationService anonymizationService) {
        this.tenantRepo = tenantRepo;
        this.contactRepo = contactRepo;
        this.anonymizationService = anonymizationService;
    }

    @Scheduled(cron = "${app.gdpr.retention-cron:0 0 2 * * *}")
    @Transactional
    public void runRetention() {
        log.info("[RETENTION] Starting daily data retention sweep");

        List<Tenant> tenants = tenantRepo.findAll();

        for (Tenant tenant : tenants) {
            UUID tenantId = tenant.getId();
            int retentionDays = defaultRetentionDays;
            LocalDateTime cutoff = LocalDateTime.now().minusDays(retentionDays);

            List<Contact> candidates = contactRepo.findRetentionCandidates(tenantId, cutoff);

            int anonymized = 0;
            int skipped = 0;

            for (Contact contact : candidates) {
                try {
                    anonymizationService.anonymize(contact, SYSTEM_ACTOR);
                    anonymized++;
                } catch (GdprErasureBlockedException e) {
                    log.warn("[RETENTION] Tenant {}: contact {} skipped — active SIGNED contracts: {}",
                            tenantId, contact.getId(), e.getBlockingContractIds());
                    skipped++;
                }
            }

            log.info("[RETENTION] Tenant {}: anonymized {} contacts, skipped {} (active contracts)",
                    tenantId, anonymized, skipped);
        }

        log.info("[RETENTION] Daily data retention sweep complete");
    }
}
