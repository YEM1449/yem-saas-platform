package com.yem.hlm.backend.dashboard.service;

import com.yem.hlm.backend.auth.config.CacheConfig;
import com.yem.hlm.backend.contact.domain.ContactStatus;
import com.yem.hlm.backend.contact.repo.ContactRepository;
import com.yem.hlm.backend.dashboard.api.dto.AgentPerformanceDTO;
import com.yem.hlm.backend.dashboard.api.dto.AlertDTO;
import com.yem.hlm.backend.dashboard.api.dto.AlertDTO.Severity;
import com.yem.hlm.backend.dashboard.api.dto.ForecastDTO;
import com.yem.hlm.backend.dashboard.api.dto.FunnelDTO;
import com.yem.hlm.backend.dashboard.api.dto.FunnelDTO.FunnelStage;
import com.yem.hlm.backend.dashboard.api.dto.KpiComparisonDTO;
import com.yem.hlm.backend.dashboard.api.dto.KpiComparisonDTO.KpiDelta;
import com.yem.hlm.backend.dashboard.api.dto.KpiComparisonDTO.SparklinePoint;
import com.yem.hlm.backend.dashboard.api.dto.PipelineAnalysisDTO;
import com.yem.hlm.backend.reservation.domain.ReservationStatus;
import com.yem.hlm.backend.reservation.repo.ReservationRepository;
import com.yem.hlm.backend.vente.domain.VenteStatut;
import com.yem.hlm.backend.vente.repo.VenteEcheanceRepository;
import com.yem.hlm.backend.vente.repo.VenteRepository;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Date;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Decision-grade dashboard cockpit service.
 *
 * <p>Provides three orthogonal endpoints used by the executive cockpit shell:
 * <ul>
 *   <li>{@link #getKpiComparison(UUID)} — period-over-period KPIs with sparklines</li>
 *   <li>{@link #getFunnel(UUID)} — sales funnel with conversion / drop-off ratios</li>
 *   <li>{@link #getAlerts(UUID)} — rule-based alerts (no persistence)</li>
 * </ul>
 *
 * <p>Each method is société-scoped and cached for 60 s under
 * {@link CacheConfig#DASHBOARD_COCKPIT_CACHE}. The class is read-only and pure:
 * no Spring events, no side-effects.
 */
@Service
@Transactional(readOnly = true)
public class DashboardCockpitService {

    private static final List<VenteStatut> ANNULE_ONLY = List.of(VenteStatut.ANNULE);
    private static final List<VenteStatut> NON_TERMINAL =
            List.of(VenteStatut.LIVRE, VenteStatut.ANNULE);
    private static final int SPARKLINE_WEEKS = 12;

    private final VenteRepository         venteRepo;
    private final VenteEcheanceRepository echeanceRepo;
    private final ReservationRepository   reservationRepo;
    private final ContactRepository       contactRepo;

    public DashboardCockpitService(VenteRepository venteRepo,
                                   VenteEcheanceRepository echeanceRepo,
                                   ReservationRepository reservationRepo,
                                   ContactRepository contactRepo) {
        this.venteRepo       = venteRepo;
        this.echeanceRepo    = echeanceRepo;
        this.reservationRepo = reservationRepo;
        this.contactRepo     = contactRepo;
    }

    // ── 1. KPI comparison ────────────────────────────────────────────────────

    @Cacheable(value = CacheConfig.DASHBOARD_COCKPIT_CACHE, key = "'kpi:' + #societeId")
    public KpiComparisonDTO getKpiComparison(UUID societeId) {
        LocalDateTime now = LocalDateTime.now();
        YearMonth thisMonth = YearMonth.from(now);
        YearMonth prevMonth = thisMonth.minusMonths(1);

        LocalDateTime thisMonthFrom = thisMonth.atDay(1).atStartOfDay();
        LocalDateTime thisMonthTo   = thisMonth.plusMonths(1).atDay(1).atStartOfDay();
        LocalDateTime prevMonthFrom = prevMonth.atDay(1).atStartOfDay();
        LocalDateTime prevMonthTo   = thisMonthFrom;

        LocalDateTime now30From = now.minusDays(30);
        LocalDateTime prev30From = now.minusDays(60);

        // CA signé — current month vs previous month
        BigDecimal caCurrent  = nz(venteRepo.sumPrixVenteInPeriod(societeId, thisMonthFrom, thisMonthTo, ANNULE_ONLY));
        BigDecimal caPrevious = nz(venteRepo.sumPrixVenteInPeriod(societeId, prevMonthFrom, prevMonthTo, ANNULE_ONLY));
        KpiDelta caSigne = buildDelta(caCurrent, caPrevious);

        // Ventes créées — last 30d vs prior 30d
        long ventesCurrent  = venteRepo.countCreatedInPeriod(societeId, now30From, now);
        long ventesPrevious = venteRepo.countCreatedInPeriod(societeId, prev30From, now30From);
        KpiDelta ventesCreated = buildDelta(BigDecimal.valueOf(ventesCurrent), BigDecimal.valueOf(ventesPrevious));

        // Réservations créées — last 30d vs prior 30d
        long resCurrent  = reservationRepo.countCreatedInPeriod(societeId, now30From, now);
        long resPrevious = reservationRepo.countCreatedInPeriod(societeId, prev30From, now30From);
        KpiDelta reservations = buildDelta(BigDecimal.valueOf(resCurrent), BigDecimal.valueOf(resPrevious));

        // Encaissé — current month vs previous month (uses LocalDate)
        BigDecimal encaisseCurrent = nz(echeanceRepo.sumPaidInPeriod(
                societeId, thisMonth.atDay(1), thisMonth.plusMonths(1).atDay(1)));
        BigDecimal encaissePrevious = nz(echeanceRepo.sumPaidInPeriod(
                societeId, prevMonth.atDay(1), prevMonth.plusMonths(1).atDay(1)));
        KpiDelta encaisse = buildDelta(encaisseCurrent, encaissePrevious);

        // Sparklines — 12 ISO weeks (Monday-aligned)
        LocalDate firstWeekStart = now.toLocalDate()
                .with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
                .minusWeeks(SPARKLINE_WEEKS - 1L);
        LocalDateTime sparklineFrom = firstWeekStart.atStartOfDay();

        List<SparklinePoint> caSpark = buildSparkline(
                venteRepo.sumPrixVenteByWeek(societeId, sparklineFrom),
                firstWeekStart);
        List<SparklinePoint> ventesSpark = buildSparkline(
                venteRepo.countCreatedByWeek(societeId, sparklineFrom),
                firstWeekStart);

        return new KpiComparisonDTO(now, caSigne, ventesCreated, reservations, encaisse, caSpark, ventesSpark);
    }

    // ── 2. Funnel ────────────────────────────────────────────────────────────

    @Cacheable(value = CacheConfig.DASHBOARD_COCKPIT_CACHE, key = "'funnel:' + #societeId")
    public FunnelDTO getFunnel(UUID societeId) {
        long prospects = contactRepo.countBySocieteIdAndStatusAndDeletedFalse(
                societeId, ContactStatus.PROSPECT);
        long qualified = contactRepo.countBySocieteIdAndStatusAndDeletedFalse(
                societeId, ContactStatus.QUALIFIED_PROSPECT);
        long activeReservations = reservationRepo.countBySocieteIdAndStatus(
                societeId, ReservationStatus.ACTIVE);

        // Active ventes = pipeline (non-terminal)
        long activeVentes = venteRepo.countByStatut(societeId, NON_TERMINAL).stream()
                .mapToLong(r -> ((Number) r[1]).longValue())
                .sum();
        long livre = venteRepo.countBySocieteIdAndStatut(societeId, VenteStatut.LIVRE);

        List<FunnelStage> stages = new ArrayList<>(5);
        stages.add(buildStage("PROSPECTS",    "Prospects",          prospects,          null));
        stages.add(buildStage("QUALIFIED",    "Qualifiés",          qualified,          prospects));
        stages.add(buildStage("RESERVATIONS", "Réservations",       activeReservations, qualified));
        stages.add(buildStage("VENTES",       "Ventes en pipeline", activeVentes,       activeReservations));
        stages.add(buildStage("LIVRE",        "Livrés",             livre,              activeVentes));

        return new FunnelDTO(LocalDateTime.now(), stages);
    }

    // ── 3. Alerts ────────────────────────────────────────────────────────────

    @Cacheable(value = CacheConfig.DASHBOARD_COCKPIT_CACHE, key = "'alerts:' + #societeId")
    public List<AlertDTO> getAlerts(UUID societeId) {
        LocalDateTime now = LocalDateTime.now();
        List<AlertDTO> alerts = new ArrayList<>();

        // Rule 1 — conversion rate drop > 10 % vs previous 30 d
        LocalDateTime now30From  = now.minusDays(30);
        LocalDateTime prev30From = now.minusDays(60);
        long ventes30   = venteRepo.countCreatedInPeriod(societeId, now30From, now);
        long res30      = reservationRepo.countCreatedInPeriod(societeId, now30From, now);
        long ventesPrev = venteRepo.countCreatedInPeriod(societeId, prev30From, now30From);
        long resPrev    = reservationRepo.countCreatedInPeriod(societeId, prev30From, now30From);

        BigDecimal currentConv  = ratio(ventes30, res30);
        BigDecimal previousConv = ratio(ventesPrev, resPrev);
        if (currentConv != null && previousConv != null
                && previousConv.compareTo(BigDecimal.ZERO) > 0
                && currentConv.compareTo(previousConv.multiply(BigDecimal.valueOf(0.9))) < 0) {
            BigDecimal drop = previousConv.subtract(currentConv).setScale(1, RoundingMode.HALF_UP);
            alerts.add(new AlertDTO(
                    "conversion-drop",
                    Severity.WARNING,
                    "PERFORMANCE",
                    "Taux de conversion en baisse",
                    "Conversion réservation→vente : " + currentConv + "% (−" + drop + " pts vs 30j précédents)",
                    "Voir l'analyse",
                    "/app/dashboard/commercial"));
        }

        // Rule 2 — ventes stuck > 30 days in early statuts
        long stalled = venteRepo.countStalledVentes(
                societeId,
                List.of(VenteStatut.COMPROMIS, VenteStatut.FINANCEMENT),
                now.minusDays(30));
        if (stalled > 0) {
            Severity sev = stalled > 5 ? Severity.CRITICAL : Severity.WARNING;
            alerts.add(new AlertDTO(
                    "stuck-deals",
                    sev,
                    "PIPELINE",
                    stalled + " vente" + (stalled > 1 ? "s" : "") + " bloquée" + (stalled > 1 ? "s" : ""),
                    "Aucun mouvement depuis +30 jours en COMPROMIS ou FINANCEMENT",
                    "Voir les ventes",
                    "/app/ventes"));
        }

        // Rule 3 — cancellation rate spike (90 d window)
        LocalDateTime ninetyDaysAgo = now.minusDays(90);
        long ventes90 = venteRepo.countCreatedInPeriod(societeId, ninetyDaysAgo, now);
        long annule90 = venteRepo.countByStatutInPeriod(societeId, VenteStatut.ANNULE, ninetyDaysAgo, now);
        if (ventes90 > 0) {
            BigDecimal cancelRate = BigDecimal.valueOf(annule90)
                    .divide(BigDecimal.valueOf(ventes90), 4, RoundingMode.HALF_UP)
                    .multiply(BigDecimal.valueOf(100))
                    .setScale(1, RoundingMode.HALF_UP);
            if (cancelRate.compareTo(BigDecimal.valueOf(15)) >= 0) {
                alerts.add(new AlertDTO(
                        "cancellation-spike",
                        Severity.CRITICAL,
                        "RISK",
                        "Taux d'annulation élevé",
                        cancelRate + "% des ventes annulées sur 90 jours (seuil : 15%)",
                        "Analyser les causes",
                        "/app/ventes"));
            } else if (cancelRate.compareTo(BigDecimal.TEN) >= 0) {
                alerts.add(new AlertDTO(
                        "cancellation-spike",
                        Severity.WARNING,
                        "RISK",
                        "Taux d'annulation à surveiller",
                        cancelRate + "% des ventes annulées sur 90 jours",
                        "Analyser",
                        "/app/ventes"));
            }
        }

        return alerts;
    }

    // ── 4. Pipeline Analysis ───────────────────────────────────────────────

    @Cacheable(value = CacheConfig.DASHBOARD_COCKPIT_CACHE, key = "'pipeline:' + #societeId")
    public PipelineAnalysisDTO getPipelineAnalysis(UUID societeId) {
        List<Object[]> raw = venteRepo.pipelineAnalysis(societeId);

        BigDecimal totalRaw = BigDecimal.ZERO;
        BigDecimal totalWeighted = BigDecimal.ZERO;
        List<PipelineAnalysisDTO.PipelineStage> stages = new ArrayList<>();

        for (Object[] r : raw) {
            String statut         = (String) r[0];
            long count            = ((Number) r[1]).longValue();
            BigDecimal rawCA      = toBigDecimal(r[2]);
            BigDecimal weightedCA = toBigDecimal(r[3]);
            int prob              = ((Number) r[4]).intValue();
            long avgAging         = Math.round(((Number) r[5]).doubleValue());

            totalRaw      = totalRaw.add(rawCA);
            totalWeighted = totalWeighted.add(weightedCA);
            stages.add(new PipelineAnalysisDTO.PipelineStage(statut, count, rawCA, weightedCA, prob, avgAging));
        }

        List<Object[]> riskRaw = venteRepo.atRiskDeals(societeId, 30.0);
        List<PipelineAnalysisDTO.AtRiskDeal> atRisk = riskRaw.stream().map(r -> {
            BigDecimal prix = toBigDecimal(r[4]);
            int prob = ((Number) r[5]).intValue();
            BigDecimal weighted = prix.multiply(BigDecimal.valueOf(prob))
                    .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
            return new PipelineAnalysisDTO.AtRiskDeal(
                    (UUID) r[0], str(r[1]), str(r[2]), str(r[3]),
                    prix, weighted, Math.round(((Number) r[6]).doubleValue()));
        }).toList();

        return new PipelineAnalysisDTO(LocalDateTime.now(), totalWeighted, totalRaw, stages, atRisk);
    }

    // ── 5. Forecast ─────────────────────────────────────────────────────────

    @Cacheable(value = CacheConfig.DASHBOARD_COCKPIT_CACHE, key = "'forecast:' + #societeId")
    public ForecastDTO getForecast(UUID societeId) {
        List<Object[]> raw = venteRepo.forecastRawData(societeId);
        LocalDate today = LocalDate.now();
        LocalDate d30 = today.plusDays(30);
        LocalDate d60 = today.plusDays(60);
        LocalDate d90 = today.plusDays(90);

        BigDecimal next30 = BigDecimal.ZERO;
        BigDecimal next60 = BigDecimal.ZERO;
        BigDecimal next90 = BigDecimal.ZERO;
        BigDecimal undated = BigDecimal.ZERO;
        long undatedCount = 0;

        for (Object[] r : raw) {
            BigDecimal prix = toBigDecimal(r[1]);
            int prob = ((Number) r[2]).intValue();
            BigDecimal weighted = prix.multiply(BigDecimal.valueOf(prob))
                    .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);

            Object dateObj = r[0];
            LocalDate closingDate = null;
            if (dateObj instanceof java.sql.Date d) closingDate = d.toLocalDate();
            else if (dateObj instanceof LocalDate ld) closingDate = ld;

            if (closingDate == null) {
                undated = undated.add(weighted);
                undatedCount++;
            } else {
                if (!closingDate.isAfter(d30)) next30 = next30.add(weighted);
                if (!closingDate.isAfter(d60)) next60 = next60.add(weighted);
                if (!closingDate.isAfter(d90)) next90 = next90.add(weighted);
            }
        }

        return new ForecastDTO(LocalDateTime.now(), next30, next60, next90, undated, undatedCount);
    }

    // ── 6. Agent Performance ────────────────────────────────────────────────

    @Cacheable(value = CacheConfig.DASHBOARD_COCKPIT_CACHE, key = "'agents:' + #societeId")
    public AgentPerformanceDTO getAgentPerformance(UUID societeId) {
        List<Object[]> raw = venteRepo.agentPerformance(societeId);
        List<AgentPerformanceDTO.AgentRow> agents = raw.stream().map(r -> {
            UUID agentId = (UUID) r[0];
            String name = ((str(r[1]) + " " + str(r[2])).trim());
            if (name.isBlank()) name = "—";
            long livreCount = ((Number) r[3]).longValue();
            BigDecimal totalCA = toBigDecimal(r[4]);
            long annuleCount = ((Number) r[5]).longValue();
            Double avgDays = r[6] != null ? ((Number) r[6]).doubleValue() : null;
            long activeCount = ((Number) r[7]).longValue();

            long terminal = livreCount + annuleCount;
            BigDecimal convRate = terminal > 0
                    ? BigDecimal.valueOf(livreCount)
                            .divide(BigDecimal.valueOf(terminal), 4, RoundingMode.HALF_UP)
                            .multiply(BigDecimal.valueOf(100))
                            .setScale(1, RoundingMode.HALF_UP)
                    : null;
            BigDecimal avgDeal = livreCount > 0
                    ? totalCA.divide(BigDecimal.valueOf(livreCount), 0, RoundingMode.HALF_UP)
                    : null;
            BigDecimal avgClose = avgDays != null
                    ? BigDecimal.valueOf(avgDays).setScale(1, RoundingMode.HALF_UP)
                    : null;

            return new AgentPerformanceDTO.AgentRow(
                    agentId, name, livreCount, totalCA, convRate, avgDeal, avgClose, activeCount);
        }).toList();

        return new AgentPerformanceDTO(LocalDateTime.now(), agents);
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private static KpiDelta buildDelta(BigDecimal current, BigDecimal previous) {
        BigDecimal cur  = nz(current);
        BigDecimal prev = nz(previous);
        BigDecimal delta = cur.subtract(prev);
        BigDecimal pct = null;
        if (prev.compareTo(BigDecimal.ZERO) != 0) {
            pct = delta.divide(prev.abs(), 4, RoundingMode.HALF_UP)
                    .multiply(BigDecimal.valueOf(100))
                    .setScale(1, RoundingMode.HALF_UP);
        }
        return new KpiDelta(cur, prev, delta, pct);
    }

    private static FunnelStage buildStage(String code, String label, long count, Long upstreamOrNull) {
        BigDecimal conversion;
        BigDecimal dropOff;
        if (upstreamOrNull == null) {
            conversion = BigDecimal.valueOf(100).setScale(1, RoundingMode.HALF_UP);
            dropOff    = BigDecimal.ZERO.setScale(1, RoundingMode.HALF_UP);
        } else if (upstreamOrNull == 0L) {
            conversion = null;
            dropOff    = null;
        } else {
            conversion = BigDecimal.valueOf(count)
                    .divide(BigDecimal.valueOf(upstreamOrNull), 4, RoundingMode.HALF_UP)
                    .multiply(BigDecimal.valueOf(100))
                    .setScale(1, RoundingMode.HALF_UP);
            dropOff = BigDecimal.valueOf(100).subtract(conversion).setScale(1, RoundingMode.HALF_UP);
        }
        return new FunnelStage(code, label, count, conversion, dropOff);
    }

    /** Pads a query result (week → value) to a contiguous {@value #SPARKLINE_WEEKS}-week series. */
    private static List<SparklinePoint> buildSparkline(List<Object[]> rows, LocalDate firstWeekStart) {
        Map<LocalDate, BigDecimal> byWeek = new HashMap<>();
        for (Object[] row : rows) {
            LocalDate week;
            Object rawDate = row[0];
            if (rawDate instanceof Date d) {
                week = d.toLocalDate();
            } else if (rawDate instanceof LocalDate ld) {
                week = ld;
            } else {
                continue;
            }
            // PostgreSQL date_trunc('week', …) returns Monday — keep as-is.
            BigDecimal value = row[1] instanceof BigDecimal bd
                    ? bd
                    : BigDecimal.valueOf(((Number) row[1]).longValue());
            byWeek.put(week, value);
        }
        List<SparklinePoint> points = new ArrayList<>(SPARKLINE_WEEKS);
        for (int i = 0; i < SPARKLINE_WEEKS; i++) {
            LocalDate weekStart = firstWeekStart.plusWeeks(i);
            points.add(new SparklinePoint(weekStart, byWeek.getOrDefault(weekStart, BigDecimal.ZERO)));
        }
        points.sort(Comparator.comparing(SparklinePoint::weekStart));
        return points;
    }

    private static BigDecimal nz(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }

    private static BigDecimal toBigDecimal(Object o) {
        if (o == null) return BigDecimal.ZERO;
        if (o instanceof BigDecimal bd) return bd;
        return BigDecimal.valueOf(((Number) o).doubleValue());
    }

    private static String str(Object o) {
        return o != null ? o.toString() : "";
    }

    private static BigDecimal ratio(long numerator, long denominator) {
        if (denominator <= 0) return null;
        return BigDecimal.valueOf(numerator)
                .divide(BigDecimal.valueOf(denominator), 4, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100))
                .setScale(1, RoundingMode.HALF_UP);
    }
}
