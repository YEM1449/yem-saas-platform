package com.yem.hlm.backend.vente.service;

import com.yem.hlm.backend.societe.SocieteContextHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Scheduled VEFA maintenance (Wave 12, Loi 44-00):
 * <ul>
 *   <li>expires OPTIONs whose temporary hold has passed (→ ANNULE, property freed),</li>
 *   <li>closes the cooling-off window for ventes past their retraction deadline (→ RESERVE).</li>
 * </ul>
 * Runs hourly in system context. Disabled in the test profile via
 * {@code spring.task.scheduling.enabled=false}.
 */
@Component
@ConditionalOnProperty(value = "spring.task.scheduling.enabled", matchIfMissing = true)
public class VenteVefaScheduler {

    private static final Logger log = LoggerFactory.getLogger(VenteVefaScheduler.class);

    private final VenteService venteService;
    private final SocieteContextHelper societeContextHelper;

    public VenteVefaScheduler(VenteService venteService, SocieteContextHelper societeContextHelper) {
        this.venteService = venteService;
        this.societeContextHelper = societeContextHelper;
    }

    @Scheduled(cron = "${app.vente.vefa-sweep-cron:0 5 * * * *}")
    @SchedulerLock(name = "vente_vefa_sweep", lockAtMostFor = "PT10M", lockAtLeastFor = "PT0.5S")
    public void runVefaSweep() {
        societeContextHelper.runAsSystem(() -> {
            try {
                int options = venteService.expireOverdueOptions();
                int retract = venteService.closeExpiredRetractations();
                if (options > 0 || retract > 0) {
                    log.info("VEFA sweep: {} option(s) expirée(s), {} délai(s) de rétractation clôturé(s)",
                            options, retract);
                }
            } catch (Exception e) {
                log.error("VEFA sweep failed", e);
            }
        });
    }
}
