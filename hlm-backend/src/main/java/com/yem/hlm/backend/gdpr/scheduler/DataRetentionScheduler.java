package com.yem.hlm.backend.gdpr.scheduler;

import com.yem.hlm.backend.contact.domain.Contact;
import com.yem.hlm.backend.contact.domain.ContactStatus;
import com.yem.hlm.backend.contact.repo.ContactRepository;
import com.yem.hlm.backend.gdpr.service.AnonymizationService;
import com.yem.hlm.backend.gdpr.service.GdprErasureBlockedException;
import com.yem.hlm.backend.societe.SocieteContextHelper;
import com.yem.hlm.backend.societe.SocieteRepository;
import com.yem.hlm.backend.societe.domain.Societe;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Daily data retention sweep (GDPR Art. 5(1)(e) / Loi 09-08 Art. 4 / Loi 44-00 Art. 618-17).
 *
 * <p>Runs at 02:00 by default ({@code DATA_RETENTION_CRON}).
 * Three passes per société, each with its own retention window (B-002):
 * <ul>
 *   <li>Prospect-only contacts: 2 years after deletion (Loi 09-08 § commercial prospection)</li>
 *   <li>Buyer contacts (CLIENT / ACTIVE_CLIENT / REFERRAL / LOST): 5 years (prescription commerciale)</li>
 *   <li>VEFA acquéreurs (COMPLETED_CLIENT): 10 years (Loi 44-00 archive obligation)</li>
 * </ul>
 */
@Component
@ConditionalOnProperty("spring.task.scheduling.enabled")
public class DataRetentionScheduler {

    private static final Logger log = LoggerFactory.getLogger(DataRetentionScheduler.class);

    /** System actor UUID used for automated anonymization audit events. */
    private static final UUID SYSTEM_ACTOR = new UUID(0L, 0L);

    // B-002 — named retention constants (Loi 09-08 / Loi 44-00)
    /** Prospect contacts: 2 years — Loi 09-08 commercial prospection limit. */
    private static final int RETENTION_PROSPECT_DAYS   = 730;
    /** Buyer contacts (CLIENT / ACTIVE_CLIENT / REFERRAL / LOST): 5 years — prescription commerciale. */
    private static final int RETENTION_ACQUEREUR_DAYS  = 1825;
    /** VEFA acquéreurs (COMPLETED_CLIENT): 10 years — archive obligation Art. 618-17 Loi 44-00. */
    private static final int RETENTION_VEFA_DAYS       = 3650;

    private static final List<ContactStatus> PROSPECT_STATUSES =
            List.of(ContactStatus.PROSPECT, ContactStatus.QUALIFIED_PROSPECT);
    private static final List<ContactStatus> ACQUEREUR_STATUSES =
            List.of(ContactStatus.CLIENT, ContactStatus.ACTIVE_CLIENT,
                    ContactStatus.REFERRAL, ContactStatus.LOST);
    private static final List<ContactStatus> VEFA_STATUSES =
            List.of(ContactStatus.COMPLETED_CLIENT);

    private final SocieteRepository      societeRepo;
    private final ContactRepository      contactRepo;
    private final AnonymizationService   anonymizationService;
    private final SocieteContextHelper   societeContextHelper;

    public DataRetentionScheduler(SocieteRepository societeRepo,
                                  ContactRepository contactRepo,
                                  AnonymizationService anonymizationService,
                                  SocieteContextHelper societeContextHelper) {
        this.societeRepo          = societeRepo;
        this.contactRepo          = contactRepo;
        this.anonymizationService = anonymizationService;
        this.societeContextHelper = societeContextHelper;
    }

    @Scheduled(cron = "${app.gdpr.retention-cron:0 0 2 * * *}")
    @Transactional
    public void runRetention() {
        societeContextHelper.runAsSystem(() -> {
            log.info("[RETENTION] Starting daily data retention sweep (3-tier B-002)");

            for (Societe societe : societeRepo.findAllByActifTrue()) {
                UUID societeId = societe.getId();
                LocalDateTime now = LocalDateTime.now();

                int[] counts = {0, 0}; // [anonymized, skipped]

                // Pass 1 — prospects (2 years)
                contactRepo.findRetentionCandidatesByStatuses(
                        societeId, now.minusDays(RETENTION_PROSPECT_DAYS), PROSPECT_STATUSES)
                        .forEach(c -> process(c, societeId, counts));

                // Pass 2 — buyers (5 years)
                contactRepo.findRetentionCandidatesByStatuses(
                        societeId, now.minusDays(RETENTION_ACQUEREUR_DAYS), ACQUEREUR_STATUSES)
                        .forEach(c -> process(c, societeId, counts));

                // Pass 3 — VEFA acquéreurs (10 years)
                contactRepo.findRetentionCandidatesByStatuses(
                        societeId, now.minusDays(RETENTION_VEFA_DAYS), VEFA_STATUSES)
                        .forEach(c -> process(c, societeId, counts));

                log.info("[RETENTION] Société {}: anonymized {} contacts, skipped {} (active contracts)",
                        societeId, counts[0], counts[1]);
            }

            log.info("[RETENTION] Daily data retention sweep complete");
        });
    }

    private void process(Contact contact, UUID societeId, int[] counts) {
        try {
            anonymizationService.anonymize(contact, SYSTEM_ACTOR);
            counts[0]++;
        } catch (GdprErasureBlockedException e) {
            log.warn("[RETENTION] Société {}: contact {} skipped — active SIGNED contracts: {}",
                    societeId, contact.getId(), e.getBlockingContractIds());
            counts[1]++;
        }
    }
}
