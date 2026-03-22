package com.yem.hlm.backend.deposit.scheduler;

import com.yem.hlm.backend.deposit.service.DepositService;
import com.yem.hlm.backend.societe.SocieteContextHelper;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
@ConditionalOnProperty(name = "spring.task.scheduling.enabled", havingValue = "true", matchIfMissing = true)
public class DepositWorkflowScheduler {

    private final DepositService depositService;
    private final SocieteContextHelper societeContextHelper;

    public DepositWorkflowScheduler(DepositService depositService, SocieteContextHelper societeContextHelper) {
        this.depositService = depositService;
        this.societeContextHelper = societeContextHelper;
    }

    /** Runs every hour at minute 0. */
    @Scheduled(cron = "0 0 * * * *")
    public void hourly() {
        societeContextHelper.runAsSystem(() ->
            depositService.runHourlyWorkflow(Duration.ofHours(24))
        );
    }
}
