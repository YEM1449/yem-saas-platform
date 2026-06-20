package com.yem.hlm.backend.visite.service;

import com.yem.hlm.backend.societe.SocieteContextHelper;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Persistent reminder dispatcher (RG-V07, SRE lesson).
 *
 * <p>Scans the {@code visite_rappel} table every 5 minutes and sends the due reminders. This is
 * deliberately a DB-scan job — <b>not</b> an in-memory {@code TaskScheduler.schedule()} — so
 * reminders survive a redeploy/restart. Runs in system mode (cross-société) via
 * {@link SocieteContextHelper#runAsSystem}. Disabled in tests via
 * {@code spring.task.scheduling.enabled=false}.
 *
 * <p>{@code @SchedulerLock} (ShedLock, same pattern as the outbox dispatcher and the digest job)
 * serialises the scan across instances: in a multi-replica deployment only one node claims the
 * lock and sends a given batch, so an {@code EN_ATTENTE} reminder cannot be read-and-sent twice
 * before the first transaction marks it {@code ENVOYE} — i.e. no duplicate reminder emails.
 */
@Component
@ConditionalOnProperty(value = "spring.task.scheduling.enabled", matchIfMissing = true)
public class RappelVisiteJob {

    private static final Logger log = LoggerFactory.getLogger(RappelVisiteJob.class);

    private final VisiteRappelService rappelService;
    private final SocieteContextHelper societeCtx;

    public RappelVisiteJob(VisiteRappelService rappelService, SocieteContextHelper societeCtx) {
        this.rappelService = rappelService;
        this.societeCtx = societeCtx;
    }

    /** Every 5 minutes — send the reminders whose due time has passed. */
    @Scheduled(fixedDelayString = "${app.visite.rappel-scan-ms:300000}")
    @SchedulerLock(name = "visite_rappel_dispatcher", lockAtMostFor = "PT5M", lockAtLeastFor = "PT0.2S")
    public void scanRappelsDus() {
        societeCtx.runAsSystem(() -> {
            try {
                rappelService.envoyerRappelsDus();
            } catch (Exception e) {
                log.error("Scan des rappels de visite échoué", e);
            }
        });
    }
}
