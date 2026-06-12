package com.yem.hlm.backend.dashboard.service;

import com.yem.hlm.backend.societe.SocieteContextHelper;
import com.yem.hlm.backend.vente.domain.StatutDossierFinancement;
import com.yem.hlm.backend.vente.domain.VenteStatut;
import com.yem.hlm.backend.vente.repo.DossierFinancementRepository;
import com.yem.hlm.backend.vente.repo.VenteEcheanceRepository;
import com.yem.hlm.backend.vente.repo.VenteRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Computes the VEFA treasury dashboard from the legal échéancier + VEFA alert sources. */
@Service
public class TresorerieDashboardService {

    private static final int OVERDUE_LIST_LIMIT = 20;
    private static final int ACCORD_ALERT_DAYS  = 15;
    private static final int FORECAST_MONTHS    = 6;

    /** Short French month labels, e.g. "oct. 2026" — index 1-12. */
    private static final java.time.format.DateTimeFormatter MONTH_LABEL =
            java.time.format.DateTimeFormatter.ofPattern("MMM yyyy", java.util.Locale.FRENCH);

    private final VenteEcheanceRepository echeanceRepository;
    private final VenteRepository venteRepository;
    private final DossierFinancementRepository dossierRepository;
    private final SocieteContextHelper societeCtx;

    public TresorerieDashboardService(VenteEcheanceRepository echeanceRepository,
                                      VenteRepository venteRepository,
                                      DossierFinancementRepository dossierRepository,
                                      SocieteContextHelper societeCtx) {
        this.echeanceRepository = echeanceRepository;
        this.venteRepository = venteRepository;
        this.dossierRepository = dossierRepository;
        this.societeCtx = societeCtx;
    }

    @Transactional(readOnly = true)
    public TresorerieDashboardDTO getTresorerie() {
        UUID societeId = societeCtx.requireSocieteId();
        LocalDate today = LocalDate.now();

        BigDecimal encaisse      = echeanceRepository.sumPaidAll(societeId);
        BigDecimal aEncaisser    = echeanceRepository.sumDueAll(societeId);
        BigDecimal previsionnel  = echeanceRepository.sumMontantDueInPeriod(societeId, today, today.plusMonths(FORECAST_MONTHS));
        BigDecimal enRetardMnt   = echeanceRepository.sumMontantOverdue(societeId, today);
        long       enRetardCount = echeanceRepository.countOverdue(societeId, today);

        List<TresorerieDashboardDTO.MoisPrevision> previsionnelParMois = buildMonthlyForecast(societeId, today);

        long optionsActives       = venteRepository.countBySocieteIdAndStatut(societeId, VenteStatut.OPTION);
        long retractationsEnCours = venteRepository.countBySocieteIdAndStatut(societeId, VenteStatut.EN_RETRACTATION);
        long accordsExpirant      = dossierRepository.countBySocieteIdAndStatutInAndDateExpirationAccordBetween(
                societeId,
                List.of(StatutDossierFinancement.EN_COURS, StatutDossierFinancement.ACCORD_PRINCIPE),
                today, today.plusDays(ACCORD_ALERT_DAYS));

        List<TresorerieDashboardDTO.AppelEnRetard> appels = echeanceRepository
                .findOverdueDetails(societeId, today, PageRequest.of(0, OVERDUE_LIST_LIMIT))
                .stream()
                .map(r -> new TresorerieDashboardDTO.AppelEnRetard(
                        (UUID) r[0], (String) r[1], (String) r[2], (String) r[3],
                        (BigDecimal) r[4], (LocalDate) r[5],
                        ChronoUnit.DAYS.between((LocalDate) r[5], today)))
                .toList();

        return new TresorerieDashboardDTO(
                encaisse, aEncaisser, previsionnel, enRetardMnt, enRetardCount,
                optionsActives, retractationsEnCours, accordsExpirant, appels,
                previsionnelParMois);
    }

    /**
     * Six calendar-month buckets of expected cash, current month first. The window starts at
     * {@code today} (so overdue amounts already shown as "en retard" are not counted again)
     * and ends at the first day of the sixth month. Months with no unpaid échéance return 0.
     */
    private List<TresorerieDashboardDTO.MoisPrevision> buildMonthlyForecast(UUID societeId, LocalDate today) {
        LocalDate firstOfMonth = today.withDayOfMonth(1);
        LocalDate to = firstOfMonth.plusMonths(FORECAST_MONTHS);

        Map<java.time.YearMonth, BigDecimal> byMonth = new HashMap<>();
        for (Object[] row : echeanceRepository.sumUnpaidByMonth(societeId, today, to)) {
            int year  = ((Number) row[0]).intValue();
            int month = ((Number) row[1]).intValue();
            byMonth.put(java.time.YearMonth.of(year, month), (BigDecimal) row[2]);
        }

        List<TresorerieDashboardDTO.MoisPrevision> forecast = new java.util.ArrayList<>(FORECAST_MONTHS);
        for (int i = 0; i < FORECAST_MONTHS; i++) {
            java.time.YearMonth ym = java.time.YearMonth.from(firstOfMonth.plusMonths(i));
            forecast.add(new TresorerieDashboardDTO.MoisPrevision(
                    ym.getYear(), ym.getMonthValue(),
                    ym.atDay(1).format(MONTH_LABEL),
                    byMonth.getOrDefault(ym, BigDecimal.ZERO)));
        }
        return forecast;
    }
}
