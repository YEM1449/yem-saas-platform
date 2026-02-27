package com.yem.hlm.backend.outbox.scheduler;

import com.yem.hlm.backend.outbox.service.OutboundDispatcherService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Triggers outbound message dispatch on a fixed delay.
 *
 * <p>Activated only when {@code spring.task.scheduling.enabled=true} (default).
 * Set {@code spring.task.scheduling.enabled=false} in tests that must not run the scheduler.
 * Polling interval is configurable via {@code app.outbox.polling-interval-ms} (default 5 000 ms).
 */
@Component
@ConditionalOnProperty(name = "spring.task.scheduling.enabled", havingValue = "true", matchIfMissing = true)
public class OutboundDispatcherScheduler {

    private final OutboundDispatcherService dispatcherService;

    public OutboundDispatcherScheduler(OutboundDispatcherService dispatcherService) {
        this.dispatcherService = dispatcherService;
    }

    @Scheduled(fixedDelayString = "${app.outbox.polling-interval-ms:5000}")
    public void poll() {
        dispatcherService.runDispatch();
    }
}
