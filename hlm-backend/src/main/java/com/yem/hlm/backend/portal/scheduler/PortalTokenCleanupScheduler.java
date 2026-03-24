package com.yem.hlm.backend.portal.scheduler;

import com.yem.hlm.backend.portal.repo.PortalTokenRepository;
import com.yem.hlm.backend.societe.SocieteContextHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/**
 * Scheduled task that removes expired and already-used portal tokens.
 *
 * <p>Runs daily at 03:00 by default (configurable via {@code app.portal.cleanup-cron}).
 * Disabled when {@code spring.task.scheduling.enabled=false} (e.g. in test profile).
 */
@Component
@ConditionalOnProperty(name = "spring.task.scheduling.enabled", matchIfMissing = true)
public class PortalTokenCleanupScheduler {

    private static final Logger log = LoggerFactory.getLogger(PortalTokenCleanupScheduler.class);

    private final PortalTokenRepository portalTokenRepository;
    private final SocieteContextHelper societeContextHelper;

    public PortalTokenCleanupScheduler(PortalTokenRepository portalTokenRepository,
                                       SocieteContextHelper societeContextHelper) {
        this.portalTokenRepository = portalTokenRepository;
        this.societeContextHelper = societeContextHelper;
    }

    @Scheduled(cron = "${app.portal.cleanup-cron:0 0 3 * * *}")
    @Transactional
    public void cleanup() {
        societeContextHelper.runAsSystem(() -> {
            int deleted = portalTokenRepository.deleteExpiredAndUsed(Instant.now());
            log.info("[PORTAL-CLEANUP] Deleted {} expired/used portal tokens", deleted);
        });
    }
}
