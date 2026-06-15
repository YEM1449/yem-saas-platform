package com.yem.hlm.backend.visite.service;

import com.yem.hlm.backend.visite.domain.StatutRappel;
import com.yem.hlm.backend.visite.domain.StatutVisite;
import com.yem.hlm.backend.visite.domain.Visite;
import com.yem.hlm.backend.visite.domain.VisiteRappel;
import com.yem.hlm.backend.visite.repo.VisiteRappelRepository;
import com.yem.hlm.backend.visite.repo.VisiteRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

/**
 * Sends the due, persistent visite reminders (RG-V07). Invoked by {@link RappelVisiteJob} which
 * runs it in system mode (cross-société) — the nil-UUID RLS bypass lets the scan read every
 * société's pending rows.
 *
 * <p>Idempotent: an {@code ENVOYE} row is never re-sent. On a transient email failure the row is
 * left {@code EN_ATTENTE} with {@code tentatives} incremented; after {@link #MAX_TENTATIVES} it is
 * abandoned ({@code ANNULE}) to avoid an infinite retry loop.
 */
@Service
public class VisiteRappelService {

    private static final Logger log = LoggerFactory.getLogger(VisiteRappelService.class);
    static final int MAX_TENTATIVES = 5;

    private final VisiteRappelRepository rappelRepo;
    private final VisiteRepository visiteRepo;
    private final VisiteEmailService emailService;

    public VisiteRappelService(VisiteRappelRepository rappelRepo,
                               VisiteRepository visiteRepo,
                               VisiteEmailService emailService) {
        this.rappelRepo = rappelRepo;
        this.visiteRepo = visiteRepo;
        this.emailService = emailService;
    }

    /** Scans and sends every reminder whose {@code du_a} has passed. Returns the count sent. */
    @Transactional
    public int envoyerRappelsDus() {
        List<VisiteRappel> dus = rappelRepo.findByStatutAndDuABeforeOrderByDuAAsc(
                StatutRappel.EN_ATTENTE, Instant.now());
        int envoyes = 0;
        for (VisiteRappel rappel : dus) {
            Visite visite = visiteRepo.findById(rappel.getVisiteId()).orElse(null);
            // Visite gone or no longer relevant (cancelled / terminal) → abandon the reminder.
            if (visite == null || estTerminale(visite.getStatut())) {
                rappel.marquerAnnule();
                rappelRepo.save(rappel);
                continue;
            }
            String to = emailService.destinataireEmail(visite, rappel.getDestinataire());
            if (to == null || to.isBlank()) {
                rappel.marquerAnnule(); // no address to reach — nothing to do
                rappelRepo.save(rappel);
                continue;
            }
            try {
                emailService.envoyerRappel(visite, rappel, to);
                rappel.marquerEnvoye();
                envoyes++;
            } catch (RuntimeException e) {
                rappel.incrementerTentative();
                if (rappel.getTentatives() >= MAX_TENTATIVES) {
                    rappel.marquerAnnule();
                    log.error("Rappel {} abandonné après {} tentatives", rappel.getId(), rappel.getTentatives(), e);
                } else {
                    log.warn("Échec envoi rappel {} (tentative {}), retry au prochain scan",
                            rappel.getId(), rappel.getTentatives(), e);
                }
            }
            rappelRepo.save(rappel);
        }
        if (envoyes > 0) {
            log.info("Rappels de visite envoyés : {}", envoyes);
        }
        return envoyes;
    }

    private boolean estTerminale(StatutVisite statut) {
        return statut == StatutVisite.ANNULEE
                || statut == StatutVisite.REALISEE
                || statut == StatutVisite.NO_SHOW;
    }
}
