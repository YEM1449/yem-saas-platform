package com.yem.hlm.backend.gdpr;

import com.yem.hlm.backend.contact.domain.Contact;
import com.yem.hlm.backend.contact.domain.ContactStatus;
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
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link DataRetentionScheduler} — 3-tier type-aware retention (B-002).
 * All collaborators are mocked; no Spring context, no database.
 */
@ExtendWith(MockitoExtension.class)
class DataRetentionSchedulerTest {

    private static final UUID SOCIETE_ID  = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID SYSTEM_UUID = new UUID(0L, 0L);

    @Mock SocieteRepository   societeRepo;
    @Mock ContactRepository   contactRepo;
    @Mock AnonymizationService anonymizationService;
    private final SocieteContextHelper societeContextHelper = new SocieteContextHelper();

    DataRetentionScheduler scheduler;
    Societe societe;

    @BeforeEach
    void setup() {
        scheduler = new DataRetentionScheduler(societeRepo, contactRepo, anonymizationService, societeContextHelper);
        societe = new Societe("Acme Corp", "MA");
        ReflectionTestUtils.setField(societe, "id", SOCIETE_ID);
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    /** Stubs all 3 tiers to return empty lists by default. */
    private void stubAllTiersEmpty(UUID sid) {
        lenient().when(contactRepo.findRetentionCandidatesByStatuses(eq(sid), any(), anyList()))
                .thenReturn(List.of());
    }

    // =========================================================================
    // 1. Prospect contacts (tier 1, 2-year window) are anonymized
    // =========================================================================

    @Test
    void runRetention_prospectCandidate_anonymized() {
        Contact prospect = mock(Contact.class);
        when(societeRepo.findAllByActifTrue()).thenReturn(List.of(societe));
        // Tier 1 (PROSPECT, QUALIFIED_PROSPECT) returns one candidate
        when(contactRepo.findRetentionCandidatesByStatuses(eq(SOCIETE_ID), any(),
                argThat(l -> l.contains(ContactStatus.PROSPECT))))
                .thenReturn(List.of(prospect));
        // Tiers 2+3 return empty
        when(contactRepo.findRetentionCandidatesByStatuses(eq(SOCIETE_ID), any(),
                argThat(l -> !l.contains(ContactStatus.PROSPECT))))
                .thenReturn(List.of());

        scheduler.runRetention();

        verify(anonymizationService).anonymize(prospect, SYSTEM_UUID);
        verifyNoMoreInteractions(anonymizationService);
    }

    // =========================================================================
    // 2. COMPLETED_CLIENT (tier 3, 10-year window) is anonymized
    // =========================================================================

    @Test
    void runRetention_vefaCandidate_anonymized() {
        Contact vefaClient = mock(Contact.class);
        when(societeRepo.findAllByActifTrue()).thenReturn(List.of(societe));
        when(contactRepo.findRetentionCandidatesByStatuses(eq(SOCIETE_ID), any(),
                argThat(l -> l.contains(ContactStatus.COMPLETED_CLIENT))))
                .thenReturn(List.of(vefaClient));
        when(contactRepo.findRetentionCandidatesByStatuses(eq(SOCIETE_ID), any(),
                argThat(l -> !l.contains(ContactStatus.COMPLETED_CLIENT))))
                .thenReturn(List.of());

        scheduler.runRetention();

        verify(anonymizationService).anonymize(vefaClient, SYSTEM_UUID);
        verifyNoMoreInteractions(anonymizationService);
    }

    // =========================================================================
    // 3. Contact blocked by SIGNED contract is skipped; no exception propagated
    // =========================================================================

    @Test
    void runRetention_contactWithSignedContract_isSkippedAndDoesNotThrow() {
        Contact blocked = mock(Contact.class);
        when(blocked.getId()).thenReturn(UUID.randomUUID());
        doThrow(new GdprErasureBlockedException(List.of(UUID.randomUUID())))
                .when(anonymizationService).anonymize(blocked, SYSTEM_UUID);

        when(societeRepo.findAllByActifTrue()).thenReturn(List.of(societe));
        when(contactRepo.findRetentionCandidatesByStatuses(eq(SOCIETE_ID), any(),
                argThat(l -> l.contains(ContactStatus.PROSPECT))))
                .thenReturn(List.of(blocked));
        when(contactRepo.findRetentionCandidatesByStatuses(eq(SOCIETE_ID), any(),
                argThat(l -> !l.contains(ContactStatus.PROSPECT))))
                .thenReturn(List.of());

        scheduler.runRetention(); // must not throw

        verify(anonymizationService).anonymize(blocked, SYSTEM_UUID);
    }

    // =========================================================================
    // 4. No candidates — anonymize never called
    // =========================================================================

    @Test
    void runRetention_noCandidates_anonymizeNeverCalled() {
        when(societeRepo.findAllByActifTrue()).thenReturn(List.of(societe));
        stubAllTiersEmpty(SOCIETE_ID);

        scheduler.runRetention();

        verifyNoInteractions(anonymizationService);
    }

    // =========================================================================
    // 5. Three tiers queried per société, candidates from each tier are processed
    // =========================================================================

    @Test
    void runRetention_allThreeTiersQueried() {
        when(societeRepo.findAllByActifTrue()).thenReturn(List.of(societe));
        stubAllTiersEmpty(SOCIETE_ID);

        scheduler.runRetention();

        // Exactly 3 calls: one per tier
        verify(contactRepo, times(3)).findRetentionCandidatesByStatuses(eq(SOCIETE_ID), any(), anyList());
    }

    // =========================================================================
    // 6. No sociétés — no work done at all
    // =========================================================================

    @Test
    void runRetention_noSocietes_nothingQueried() {
        when(societeRepo.findAllByActifTrue()).thenReturn(List.of());

        scheduler.runRetention();

        verifyNoInteractions(contactRepo);
        verifyNoInteractions(anonymizationService);
    }
}
