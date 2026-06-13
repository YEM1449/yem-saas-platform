package com.yem.hlm.backend.notification.service;

import com.yem.hlm.backend.outbox.service.provider.EmailSender;
import com.yem.hlm.backend.societe.AppUserSocieteRepository;
import com.yem.hlm.backend.societe.SocieteRepository;
import com.yem.hlm.backend.societe.domain.AppUserSociete;
import com.yem.hlm.backend.societe.domain.Societe;
import com.yem.hlm.backend.user.repo.UserRepository;
import com.yem.hlm.backend.vente.domain.VenteStatut;
import com.yem.hlm.backend.vente.repo.VenteEcheanceRepository;
import com.yem.hlm.backend.vente.repo.VenteRepository;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * Daily digest scheduler — C-006.
 *
 * <p>Sends one email per ADMIN/MANAGER per active société, summarising:
 * <ul>
 *   <li>Overdue échéances (late payment calls)</li>
 *   <li>Active OPTION holds (time-sensitive)</li>
 *   <li>Ventes EN_RETRACTATION (retraction window active)</li>
 *   <li>Deliveries behind schedule (joursRetard &gt; 0)</li>
 * </ul>
 *
 * <p>Skips sending when all counts are zero (no news = no noise).
 * Configurable via {@code app.digest.cron} (default: daily at 07:30, weekdays).
 */
@Component
@ConditionalOnProperty(name = "spring.task.scheduling.enabled", matchIfMissing = true)
public class NotificationDigestScheduler {

    private static final Logger log = LoggerFactory.getLogger(NotificationDigestScheduler.class);

    private static final DateTimeFormatter FR_DATE =
            DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.FRENCH);

    private final SocieteRepository          societeRepository;
    private final AppUserSocieteRepository   appUserSocieteRepository;
    private final UserRepository             userRepository;
    private final VenteEcheanceRepository    echeanceRepository;
    private final VenteRepository            venteRepository;
    private final EmailSender                emailSender;

    public NotificationDigestScheduler(
            SocieteRepository        societeRepository,
            AppUserSocieteRepository appUserSocieteRepository,
            UserRepository           userRepository,
            VenteEcheanceRepository  echeanceRepository,
            VenteRepository          venteRepository,
            EmailSender              emailSender) {
        this.societeRepository        = societeRepository;
        this.appUserSocieteRepository = appUserSocieteRepository;
        this.userRepository           = userRepository;
        this.echeanceRepository       = echeanceRepository;
        this.venteRepository          = venteRepository;
        this.emailSender              = emailSender;
    }

    @Scheduled(cron = "${app.digest.cron:0 30 7 * * MON-FRI}")
    @SchedulerLock(name = "notification_digest", lockAtMostFor = "PT5M")
    public void runDigest() {
        LocalDate today = LocalDate.now();
        List<Societe> societes = societeRepository.findAllByActifTrue();
        log.info("[DIGEST] Running daily digest for {} sociétés", societes.size());

        for (Societe societe : societes) {
            try {
                processSociete(societe, today);
            } catch (Exception e) {
                log.error("[DIGEST] Failed for société {} — skipping", societe.getId(), e);
            }
        }
    }

    // ── private ───────────────────────────────────────────────────────────────

    private void processSociete(Societe societe, LocalDate today) {
        UUID societeId = societe.getId();
        if (societeId == null) return; // persisted entities always have an ID; guard for null-safety

        // Collect alert counts
        long overdueEcheances    = echeanceRepository.countOverdue(societeId, today);
        long optionsActives      = venteRepository.countBySocieteIdAndStatut(societeId, VenteStatut.OPTION);
        long retractations       = venteRepository.countBySocieteIdAndStatut(societeId, VenteStatut.EN_RETRACTATION);
        long livraisonsEnRetard  = venteRepository.countVentesEnRetardLivraison(societeId, today);

        // Skip if everything is green
        if (overdueEcheances == 0 && optionsActives == 0 && retractations == 0 && livraisonsEnRetard == 0) {
            log.debug("[DIGEST] Société {} — no alerts, skipping email", societeId);
            return;
        }

        // Find ADMIN and MANAGER recipients
        List<AppUserSociete> members = appUserSocieteRepository.findByIdSocieteIdAndActifTrue(societeId);
        List<UUID> recipientUserIds  = members.stream()
                .filter(m -> "ADMIN".equals(m.getRole()) || "MANAGER".equals(m.getRole()))
                .map(AppUserSociete::getUserId)
                .toList();

        if (recipientUserIds.isEmpty()) return;

        String subject = "Résumé des alertes — %s · %s".formatted(societe.getNom(), today.format(FR_DATE));
        String body    = buildHtmlBody(societe.getNom(), today, overdueEcheances, optionsActives, retractations, livraisonsEnRetard);

        for (UUID userId : recipientUserIds) {
            userRepository.findById(userId).ifPresent(user -> {
                try {
                    emailSender.send(user.getEmail(), subject, body);
                    log.debug("[DIGEST] Sent to {} for société {}", user.getEmail(), societe.getNom());
                } catch (Exception e) {
                    log.warn("[DIGEST] Failed to send to {} — {}", user.getEmail(), e.getMessage());
                }
            });
        }
    }

    private String buildHtmlBody(String societeNom, LocalDate today,
                                  long overdueEcheances, long optionsActives,
                                  long retractations, long livraisonsEnRetard) {
        var sb = new StringBuilder();
        sb.append("""
                <!DOCTYPE html>
                <html lang="fr"><head><meta charset="UTF-8">
                <style>
                  body{font-family:Arial,sans-serif;font-size:14px;color:#111827;background:#f9fafb;margin:0;padding:0}
                  .container{max-width:560px;margin:24px auto;background:#fff;border-radius:8px;
                             border:1px solid #e5e7eb;overflow:hidden}
                  .header{background:#1e293b;padding:20px 24px;color:#fff}
                  .header h1{margin:0;font-size:18px;font-weight:700}
                  .header p{margin:4px 0 0;font-size:12px;color:#94a3b8}
                  .body{padding:20px 24px}
                  .alert-row{display:flex;justify-content:space-between;align-items:center;
                              padding:12px 0;border-bottom:1px solid #f3f4f6}
                  .alert-row:last-child{border-bottom:none}
                  .alert-label{font-size:13px;color:#374151}
                  .alert-badge{font-size:15px;font-weight:700;padding:3px 10px;border-radius:20px}
                  .badge-red{background:#fee2e2;color:#dc2626}
                  .badge-amber{background:#fef3c7;color:#d97706}
                  .badge-blue{background:#dbeafe;color:#2563eb}
                  .footer{padding:14px 24px;background:#f9fafb;font-size:11px;color:#9ca3af;border-top:1px solid #e5e7eb}
                  .cta{display:inline-block;margin-top:18px;padding:9px 20px;background:#6366f1;
                       color:#fff;border-radius:6px;text-decoration:none;font-size:13px;font-weight:600}
                </style></head><body>
                <div class="container">
                  <div class="header">
                    <h1>Résumé des alertes</h1>
                    <p>
                """).append(societeNom).append(" &bull; ").append(today.format(FR_DATE)).append("""
                    </p>
                  </div>
                  <div class="body">
                    <p style="margin:0 0 16px;font-size:13px;color:#6b7280">
                      Voici les items nécessitant votre attention aujourd&#8217;hui.
                    </p>
                """);

        if (overdueEcheances > 0) {
            sb.append(alertRow("&#201;ch&#233;ances en retard de paiement", overdueEcheances, "badge-red"));
        }
        if (livraisonsEnRetard > 0) {
            sb.append(alertRow("Livraisons en retard (Art. 618-17 Loi 44-00)", livraisonsEnRetard, "badge-red"));
        }
        if (retractations > 0) {
            sb.append(alertRow("Ventes en p&#233;riode de r&#233;tractation", retractations, "badge-amber"));
        }
        if (optionsActives > 0) {
            sb.append(alertRow("Options actives (&#224; confirmer avant expiration)", optionsActives, "badge-blue"));
        }

        sb.append("""
                    <a class="cta" href="${app.frontend.base-url:/}app/dashboard">
                      Ouvrir le tableau de bord &#8594;
                    </a>
                  </div>
                  <div class="footer">
                    Vous recevez ce message car vous &#234;tes ADMIN ou MANAGER sur la soci&#233;t&#233;
                """).append(societeNom).append("""
                    . Pour modifier vos pr&#233;f&#233;rences, contactez votre administrateur.
                  </div>
                </div></body></html>
                """);

        return sb.toString();
    }

    private static String alertRow(String label, long count, String badgeClass) {
        return """
                <div class="alert-row">
                  <span class="alert-label">%s</span>
                  <span class="alert-badge %s">%d</span>
                </div>
                """.formatted(label, badgeClass, count);
    }
}
