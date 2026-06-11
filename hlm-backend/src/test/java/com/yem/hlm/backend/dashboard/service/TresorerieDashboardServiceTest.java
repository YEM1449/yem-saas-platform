package com.yem.hlm.backend.dashboard.service;

import com.yem.hlm.backend.societe.SocieteContextHelper;
import com.yem.hlm.backend.vente.domain.VenteStatut;
import com.yem.hlm.backend.vente.repo.DossierFinancementRepository;
import com.yem.hlm.backend.vente.repo.VenteEcheanceRepository;
import com.yem.hlm.backend.vente.repo.VenteRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TresorerieDashboardServiceTest {

    private static final UUID SOC = UUID.randomUUID();

    @Mock VenteEcheanceRepository echeanceRepository;
    @Mock VenteRepository venteRepository;
    @Mock DossierFinancementRepository dossierRepository;
    @Mock SocieteContextHelper societeCtx;

    @Test
    void getTresorerie_aggregatesCashAndAlerts() {
        UUID venteId = UUID.randomUUID();
        LocalDate due = LocalDate.now().minusDays(15);
        when(societeCtx.requireSocieteId()).thenReturn(SOC);
        when(echeanceRepository.sumPaidAll(SOC)).thenReturn(new BigDecimal("500000"));
        when(echeanceRepository.sumDueAll(SOC)).thenReturn(new BigDecimal("1500000"));
        when(echeanceRepository.sumMontantDueInPeriod(eq(SOC), any(), any())).thenReturn(new BigDecimal("300000"));
        when(echeanceRepository.sumMontantOverdue(eq(SOC), any())).thenReturn(new BigDecimal("42000"));
        when(echeanceRepository.countOverdue(eq(SOC), any())).thenReturn(3L);
        when(venteRepository.countBySocieteIdAndStatut(SOC, VenteStatut.OPTION)).thenReturn(2L);
        when(venteRepository.countBySocieteIdAndStatut(SOC, VenteStatut.EN_RETRACTATION)).thenReturn(1L);
        when(dossierRepository.countBySocieteIdAndStatutInAndDateExpirationAccordBetween(eq(SOC), anyList(), any(), any()))
                .thenReturn(4L);
        when(echeanceRepository.findOverdueDetails(eq(SOC), any(), any())).thenReturn(List.<Object[]>of(
                new Object[]{venteId, "VTE-2026-001", "Mohamed El Amrani", "Appel fondations",
                        new BigDecimal("42000"), due}));

        var service = new TresorerieDashboardService(echeanceRepository, venteRepository, dossierRepository, societeCtx);
        TresorerieDashboardDTO dto = service.getTresorerie();

        assertThat(dto.encaisseTotal()).isEqualByComparingTo("500000");
        assertThat(dto.aEncaisser()).isEqualByComparingTo("1500000");
        assertThat(dto.enRetardCount()).isEqualTo(3);
        assertThat(dto.optionsActives()).isEqualTo(2);
        assertThat(dto.retractationsEnCours()).isEqualTo(1);
        assertThat(dto.accordsExpirant15j()).isEqualTo(4);
        assertThat(dto.appelsEnRetard()).hasSize(1);
        assertThat(dto.appelsEnRetard().get(0).joursRetard()).isEqualTo(15);
        assertThat(dto.appelsEnRetard().get(0).venteRef()).isEqualTo("VTE-2026-001");
    }
}
