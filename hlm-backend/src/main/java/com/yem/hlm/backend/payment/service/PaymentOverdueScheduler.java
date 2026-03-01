package com.yem.hlm.backend.payment.service;

import com.yem.hlm.backend.payment.repo.PaymentCallRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Scheduler that marks ISSUED payment calls as OVERDUE when their due_date has passed.
 * <p>
 * Runs daily at 06:00 by default (configurable via {@code app.payments.overdue-cron}).
 * Disabled in test profile via {@code spring.task.scheduling.enabled=false}.
 */
@Component
@ConditionalOnProperty(name = "spring.task.scheduling.enabled", matchIfMissing = true)
public class PaymentOverdueScheduler {

    private static final Logger log = LoggerFactory.getLogger(PaymentOverdueScheduler.class);

    private final PaymentCallRepository callRepo;
    private final PaymentCallService    callService;

    public PaymentOverdueScheduler(PaymentCallRepository callRepo,
                                   PaymentCallService callService) {
        this.callRepo    = callRepo;
        this.callService = callService;
    }

    @Scheduled(cron = "${app.payments.overdue-cron:0 0 6 * * *}")
    public void runOverdueCheck() {
        var tenantIds = callRepo.findTenantsWithIssuedCalls();
        log.info("[OVERDUE-SCHEDULER] checking {} tenants", tenantIds.size());
        for (var tenantId : tenantIds) {
            try {
                callService.markOverdueCalls(tenantId);
            } catch (Exception e) {
                log.error("[OVERDUE-SCHEDULER] error for tenant={}: {}", tenantId, e.getMessage(), e);
            }
        }
    }
}
