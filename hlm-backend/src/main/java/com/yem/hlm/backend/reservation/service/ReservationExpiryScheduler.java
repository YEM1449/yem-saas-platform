package com.yem.hlm.backend.reservation.service;

import com.yem.hlm.backend.societe.SocieteContextHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Scheduled job that expires ACTIVE reservations whose {@code expiryDate} has passed.
 * Runs every hour. Disabled in test profile via {@code spring.task.scheduling.enabled=false}.
 */
@Component
@ConditionalOnProperty(value = "spring.task.scheduling.enabled", matchIfMissing = true)
public class ReservationExpiryScheduler {

    private static final Logger log = LoggerFactory.getLogger(ReservationExpiryScheduler.class);

    private final ReservationService reservationService;
    private final SocieteContextHelper societeContextHelper;

    public ReservationExpiryScheduler(ReservationService reservationService,
                                      SocieteContextHelper societeContextHelper) {
        this.reservationService = reservationService;
        this.societeContextHelper = societeContextHelper;
    }

    /** Runs every hour — check for expired reservations and release properties. */
    @Scheduled(cron = "${app.reservation.expiry-cron:0 0 * * * *}")
    public void runExpiryCheck() {
        societeContextHelper.runAsSystem(() -> {
            log.info("Reservation expiry check starting");
            try {
                reservationService.runExpiryCheck();
            } catch (Exception e) {
                log.error("Reservation expiry check failed", e);
            }
        });
    }
}
