package com.yem.hlm.backend.gdpr;

import com.yem.hlm.backend.contact.domain.Contact;
import com.yem.hlm.backend.contact.repo.ContactRepository;
import com.yem.hlm.backend.gdpr.scheduler.DataRetentionScheduler;
import com.yem.hlm.backend.gdpr.service.AnonymizationService;
import com.yem.hlm.backend.gdpr.service.GdprErasureBlockedException;
import com.yem.hlm.backend.societe.domain.Societe;
import com.yem.hlm.backend.societe.SocieteContextHelper;
import com.yem.hlm.backend.societe.SocieteRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link DataRetentionScheduler}.
 *
 * <p>All collaborators are mocked — no Spring context, no database.
 */
@ExtendWith(MockitoExtension.class)
class DataRetentionSchedulerTest {

    private static final UUID TENANT_ID   = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID SYSTEM_UUID = new UUID(0L, 0L);

    @Mock SocieteRepository   tenantRepo;
    @Mock ContactRepository   contactRepo;
    @Mock AnonymizationService anonymizationService;
    private final SocieteContextHelper societeContextHelper = new SocieteContextHelper();

    DataRetentionScheduler scheduler;

    private Societe societe;

    @BeforeEach
    void setup() {
        scheduler = new DataRetentionScheduler(tenantRepo, contactRepo, anonymizationService, societeContextHelper);
        // Set the default retention window to 5 years (1825 days)
        ReflectionTestUtils.setField(scheduler, "defaultRetentionDays", 1825);

        societe = new Societe("Acme Corp", "MA");
        // Inject the UUID so getId() returns a known value
        ReflectionTestUtils.setField(societe, "id", TENANT_ID);
    }

    // =========================================================================
    // 1. Contacts past the retention window are anonymized
    // =========================================================================

    @Test
    void runRetention_candidatesPresent_callsAnonymize() {
        Contact c1 = mock(Contact.class);
        Contact c2 = mock(Contact.class);

        when(tenantRepo.findAllByActifTrue()).thenReturn(List.of(societe));
        when(contactRepo.findRetentionCandidates(eq(TENANT_ID), any()))
                .thenReturn(List.of(c1, c2));

        scheduler.runRetention();

        // Both contacts must have been anonymized using the SYSTEM_ACTOR UUID
        verify(anonymizationService).anonymize(c1, SYSTEM_UUID);
        verify(anonymizationService).anonymize(c2, SYSTEM_UUID);
        verifyNoMoreInteractions(anonymizationService);
    }

    // =========================================================================
    // 2. Contacts with SIGNED contracts are skipped — GdprErasureBlockedException
    //    is caught and logged, not propagated
    // =========================================================================

    @Test
    void runRetention_contactWithSignedContract_isSkippedAndDoesNotThrow() {
        Contact blocked = mock(Contact.class);
        when(blocked.getId()).thenReturn(UUID.randomUUID());

        UUID contractId = UUID.randomUUID();
        doThrow(new GdprErasureBlockedException(List.of(contractId)))
                .when(anonymizationService).anonymize(blocked, SYSTEM_UUID);

        when(tenantRepo.findAllByActifTrue()).thenReturn(List.of(societe));
        when(contactRepo.findRetentionCandidates(eq(TENANT_ID), any()))
                .thenReturn(List.of(blocked));

        // Must not propagate the exception
        scheduler.runRetention();

        verify(anonymizationService).anonymize(blocked, SYSTEM_UUID);
    }

    // =========================================================================
    // 3. Contacts within the retention window are not touched
    //    (findRetentionCandidates returns empty list)
    // =========================================================================

    @Test
    void runRetention_noCandidates_anonymizeNeverCalled() {
        when(tenantRepo.findAllByActifTrue()).thenReturn(List.of(societe));
        when(contactRepo.findRetentionCandidates(eq(TENANT_ID), any()))
                .thenReturn(List.of());

        scheduler.runRetention();

        verifyNoInteractions(anonymizationService);
    }

    // =========================================================================
    // 4. Mixed tenant batch: only candidates from the right tenant are processed
    // =========================================================================

    @Test
    void runRetention_multipleTenants_candidatesQueriedPerTenant() {
        UUID tenant2Id = UUID.fromString("33333333-3333-3333-3333-333333333333");
        Societe tenant2 = new Societe("Acme Corp 2", "MA");
        ReflectionTestUtils.setField(tenant2, "id", tenant2Id);

        Contact c1 = mock(Contact.class);
        when(tenantRepo.findAllByActifTrue()).thenReturn(List.of(societe, tenant2));
        when(contactRepo.findRetentionCandidates(eq(TENANT_ID), any())).thenReturn(List.of(c1));
        when(contactRepo.findRetentionCandidates(eq(tenant2Id), any())).thenReturn(List.of());

        scheduler.runRetention();

        verify(contactRepo).findRetentionCandidates(eq(TENANT_ID), any());
        verify(contactRepo).findRetentionCandidates(eq(tenant2Id), any());
        verify(anonymizationService).anonymize(c1, SYSTEM_UUID);
        verifyNoMoreInteractions(anonymizationService);
    }

    // =========================================================================
    // 5. Cutoff passed to findRetentionCandidates is approximately now − retentionDays
    // =========================================================================

    @Test
    void runRetention_cutoffIsApproximatelyNowMinusRetentionDays() {
        when(tenantRepo.findAllByActifTrue()).thenReturn(List.of(societe));
        when(contactRepo.findRetentionCandidates(eq(TENANT_ID), any()))
                .thenReturn(List.of());

        var cutoffCaptor = ArgumentCaptor.forClass(java.time.LocalDateTime.class);
        scheduler.runRetention();

        verify(contactRepo).findRetentionCandidates(eq(TENANT_ID), cutoffCaptor.capture());
        var cutoff = cutoffCaptor.getValue();
        var expectedCutoff = java.time.LocalDateTime.now().minusDays(1825);
        // Allow ±5 seconds for test execution variance
        assertThat(cutoff).isBetween(expectedCutoff.minusSeconds(5), expectedCutoff.plusSeconds(5));
    }

    // =========================================================================
    // 6. No tenants — no work done at all
    // =========================================================================

    @Test
    void runRetention_noTenants_nothingQueried() {
        when(tenantRepo.findAllByActifTrue()).thenReturn(List.of());

        scheduler.runRetention();

        verifyNoInteractions(contactRepo);
        verifyNoInteractions(anonymizationService);
    }
}
