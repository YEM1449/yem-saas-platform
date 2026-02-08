package com.yem.hlm.backend.deposit.scheduler;

import com.yem.hlm.backend.deposit.service.DepositService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
@ConditionalOnProperty(name = "spring.task.scheduling.enabled", havingValue = "true", matchIfMissing = true)
public class DepositWorkflowScheduler {

    private final DepositService depositService;

    public DepositWorkflowScheduler(DepositService depositService) {
        this.depositService = depositService;
    }

    /** Runs every hour at minute 0. */
    @Scheduled(cron = "0 0 * * * *")
    public void hourly() {
        depositService.runHourlyWorkflow(Duration.ofHours(24));
    }
}
