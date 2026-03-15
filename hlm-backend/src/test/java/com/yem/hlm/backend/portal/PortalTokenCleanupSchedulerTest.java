package com.yem.hlm.backend.portal;

import com.yem.hlm.backend.portal.repo.PortalTokenRepository;
import com.yem.hlm.backend.portal.scheduler.PortalTokenCleanupScheduler;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PortalTokenCleanupSchedulerTest {

    @Mock
    private PortalTokenRepository portalTokenRepository;

    @InjectMocks
    private PortalTokenCleanupScheduler scheduler;

    @Test
    void cleanup_calls_deleteExpiredAndUsed_with_current_instant() {
        when(portalTokenRepository.deleteExpiredAndUsed(any(Instant.class))).thenReturn(3);

        Instant before = Instant.now().minus(1, ChronoUnit.SECONDS);
        scheduler.cleanup();
        Instant after = Instant.now().plus(1, ChronoUnit.SECONDS);

        ArgumentCaptor<Instant> captor = ArgumentCaptor.forClass(Instant.class);
        verify(portalTokenRepository).deleteExpiredAndUsed(captor.capture());

        Instant capturedInstant = captor.getValue();
        assertThat(capturedInstant).isAfter(before).isBefore(after);
    }

    @Test
    void cleanup_logs_deleted_count() {
        when(portalTokenRepository.deleteExpiredAndUsed(any(Instant.class))).thenReturn(7);

        // Just verify it doesn't throw and calls the repo
        scheduler.cleanup();
        verify(portalTokenRepository).deleteExpiredAndUsed(any(Instant.class));
    }
}
