package com.yem.hlm.backend.dashboard.service;

import com.yem.hlm.backend.contact.domain.ContactStatus;
import com.yem.hlm.backend.contact.repo.ContactRepository;
import com.yem.hlm.backend.dashboard.api.dto.AlertDTO;
import com.yem.hlm.backend.dashboard.api.dto.FunnelDTO;
import com.yem.hlm.backend.dashboard.api.dto.KpiComparisonDTO;
import com.yem.hlm.backend.reservation.domain.ReservationStatus;
import com.yem.hlm.backend.reservation.repo.ReservationRepository;
import com.yem.hlm.backend.vente.domain.VenteStatut;
import com.yem.hlm.backend.vente.repo.VenteEcheanceRepository;
import com.yem.hlm.backend.vente.repo.VenteRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Pure unit tests — no Spring context, no DB. Verifies the delta arithmetic,
 * funnel conversion math, and alert rule firing in isolation.
 */
class DashboardCockpitServiceTest {

    private VenteRepository         venteRepo;
    private VenteEcheanceRepository echeanceRepo;
    private ReservationRepository   reservationRepo;
    private ContactRepository       contactRepo;
    private DashboardCockpitService service;
    private final UUID societeId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        venteRepo       = mock(VenteRepository.class);
        echeanceRepo    = mock(VenteEcheanceRepository.class);
        reservationRepo = mock(ReservationRepository.class);
        contactRepo     = mock(ContactRepository.class);

        // Defaults that keep getKpiComparison happy when individual tests don't override
        when(venteRepo.sumPrixVenteInPeriod(any(), any(), any(), anyList())).thenReturn(BigDecimal.ZERO);
        when(venteRepo.countCreatedInPeriod(any(), any(), any())).thenReturn(0L);
        when(venteRepo.countByStatutInPeriod(any(), any(), any(), any())).thenReturn(0L);
        when(venteRepo.countStalledVentes(any(), anyList(), any())).thenReturn(0L);
        when(venteRepo.sumPrixVenteByWeek(any(), any())).thenReturn(Collections.emptyList());
        when(venteRepo.countCreatedByWeek(any(), any())).thenReturn(Collections.emptyList());
        when(echeanceRepo.sumPaidInPeriod(any(), any(), any())).thenReturn(BigDecimal.ZERO);
        when(reservationRepo.countCreatedInPeriod(any(), any(), any())).thenReturn(0L);

        service = new DashboardCockpitService(venteRepo, echeanceRepo, reservationRepo, contactRepo);
    }

    // ── KPI deltas ───────────────────────────────────────────────────────────

    @Test
    void kpiComparison_computesCaSigneDeltaAndPercentage() {
        // First call (current month) returns 120 000, second call (previous month) returns 100 000.
        when(venteRepo.sumPrixVenteInPeriod(any(), any(), any(), anyList()))
                .thenReturn(new BigDecimal("120000"))   // current month
                .thenReturn(new BigDecimal("100000"));  // previous month

        KpiComparisonDTO dto = service.getKpiComparison(societeId);

        KpiComparisonDTO.KpiDelta caSigne = dto.caSigne();
        assertEquals(0, new BigDecimal("120000").compareTo(caSigne.current()));
        assertEquals(0, new BigDecimal("100000").compareTo(caSigne.previous()));
        assertEquals(0, new BigDecimal("20000").compareTo(caSigne.delta()));
        assertEquals(0, new BigDecimal("20.0").compareTo(caSigne.deltaPct()));
    }

    @Test
    void kpiComparison_returnsNullPctWhenPreviousIsZero() {
        when(venteRepo.sumPrixVenteInPeriod(any(), any(), any(), anyList()))
                .thenReturn(new BigDecimal("50000"))    // current
                .thenReturn(BigDecimal.ZERO);           // previous

        KpiComparisonDTO dto = service.getKpiComparison(societeId);
        assertNull(dto.caSigne().deltaPct(), "deltaPct must be null when the previous period is zero");
    }

    @Test
    void kpiComparison_buildsTwelveWeekSparkline() {
        when(venteRepo.sumPrixVenteByWeek(any(), any())).thenReturn(Collections.emptyList());
        KpiComparisonDTO dto = service.getKpiComparison(societeId);
        assertEquals(12, dto.caSparkline().size(), "Sparkline must always have 12 buckets, padded with zero");
        assertEquals(12, dto.ventesSparkline().size());
    }

    // ── Funnel ───────────────────────────────────────────────────────────────

    @Test
    void funnel_computesConversionAndDropOff() {
        when(contactRepo.countBySocieteIdAndStatusAndDeletedFalse(societeId, ContactStatus.PROSPECT))
                .thenReturn(100L);
        when(contactRepo.countBySocieteIdAndStatusAndDeletedFalse(societeId, ContactStatus.QUALIFIED_PROSPECT))
                .thenReturn(40L);
        when(reservationRepo.countBySocieteIdAndStatus(societeId, ReservationStatus.ACTIVE))
                .thenReturn(20L);
        List<Object[]> statutRows = new java.util.ArrayList<>();
        statutRows.add(new Object[]{VenteStatut.COMPROMIS, 8L});
        when(venteRepo.countByStatut(ArgumentMatchers.eq(societeId), anyList()))
                .thenReturn(statutRows);
        when(venteRepo.countBySocieteIdAndStatut(societeId, VenteStatut.LIVRE)).thenReturn(4L);

        FunnelDTO dto = service.getFunnel(societeId);
        assertEquals(5, dto.stages().size());

        FunnelDTO.FunnelStage prospects = dto.stages().get(0);
        assertEquals("PROSPECTS", prospects.stage());
        assertEquals(100L, prospects.count());
        // Head stage: conversion is conventionally 100 %
        assertEquals(0, new BigDecimal("100.0").compareTo(prospects.conversionRate()));

        FunnelDTO.FunnelStage qualified = dto.stages().get(1);
        assertEquals(40L, qualified.count());
        assertEquals(0, new BigDecimal("40.0").compareTo(qualified.conversionRate()));
        assertEquals(0, new BigDecimal("60.0").compareTo(qualified.dropOffRate()));

        FunnelDTO.FunnelStage reservations = dto.stages().get(2);
        assertEquals(0, new BigDecimal("50.0").compareTo(reservations.conversionRate()));
    }

    @Test
    void funnel_returnsNullConversionWhenUpstreamIsZero() {
        when(contactRepo.countBySocieteIdAndStatusAndDeletedFalse(any(), any())).thenReturn(0L);
        when(reservationRepo.countBySocieteIdAndStatus(any(), any())).thenReturn(0L);
        when(venteRepo.countByStatut(any(), anyList())).thenReturn(Collections.emptyList());
        when(venteRepo.countBySocieteIdAndStatut(any(), any())).thenReturn(0L);

        FunnelDTO dto = service.getFunnel(societeId);
        // Downstream stages should report null conversion (no division-by-zero rendering)
        for (int i = 1; i < dto.stages().size(); i++) {
            assertNull(dto.stages().get(i).conversionRate(),
                    "Stage " + i + " conversion must be null when upstream is zero");
        }
    }

    // ── Alerts ───────────────────────────────────────────────────────────────

    @Test
    void alerts_emptyWhenNothingFires() {
        List<AlertDTO> alerts = service.getAlerts(societeId);
        assertTrue(alerts.isEmpty(), "No data → no alerts");
    }

    @Test
    void alerts_firesStuckDealsCriticalAboveThreshold() {
        when(venteRepo.countStalledVentes(any(), anyList(), any())).thenReturn(7L);

        List<AlertDTO> alerts = service.getAlerts(societeId);
        assertEquals(1, alerts.size());
        AlertDTO alert = alerts.get(0);
        assertEquals("stuck-deals", alert.id());
        assertEquals(AlertDTO.Severity.CRITICAL, alert.severity());
    }

    @Test
    void alerts_firesCancellationSpikeWarningBetweenTenAndFifteen() {
        // 100 ventes total, 12 ANNULE → 12 % cancellation rate → WARNING (not CRITICAL)
        when(venteRepo.countCreatedInPeriod(any(), any(), any())).thenReturn(100L);
        when(venteRepo.countByStatutInPeriod(any(), any(), any(), any())).thenReturn(12L);

        List<AlertDTO> alerts = service.getAlerts(societeId);
        assertFalse(alerts.isEmpty());
        AlertDTO cancel = alerts.stream()
                .filter(a -> "cancellation-spike".equals(a.id()))
                .findFirst().orElseThrow();
        assertEquals(AlertDTO.Severity.WARNING, cancel.severity());
    }

    @Test
    void alerts_firesCancellationSpikeCriticalAtFifteenPercent() {
        when(venteRepo.countCreatedInPeriod(any(), any(), any())).thenReturn(100L);
        when(venteRepo.countByStatutInPeriod(any(), any(), any(), any())).thenReturn(20L);

        AlertDTO cancel = service.getAlerts(societeId).stream()
                .filter(a -> "cancellation-spike".equals(a.id()))
                .findFirst().orElseThrow();
        assertEquals(AlertDTO.Severity.CRITICAL, cancel.severity());
    }
}
