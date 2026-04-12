package com.yem.hlm.backend.dashboard.service;

import com.yem.hlm.backend.auth.config.CacheConfig;
import com.yem.hlm.backend.contact.domain.ContactStatus;
import com.yem.hlm.backend.contact.repo.ContactRepository;
import com.yem.hlm.backend.dashboard.api.dto.AgentPerformanceDTO;
import com.yem.hlm.backend.dashboard.api.dto.AlertDTO;
import com.yem.hlm.backend.dashboard.api.dto.AlertDTO.Severity;
import com.yem.hlm.backend.dashboard.api.dto.DiscountAnalyticsDTO;
import com.yem.hlm.backend.dashboard.api.dto.ForecastDTO;
import com.yem.hlm.backend.dashboard.api.dto.FunnelDTO;
import com.yem.hlm.backend.dashboard.api.dto.FunnelDTO.FunnelStage;
import com.yem.hlm.backend.dashboard.api.dto.InventoryIntelligenceDTO;
import com.yem.hlm.backend.dashboard.api.dto.KpiComparisonDTO;
import com.yem.hlm.backend.dashboard.api.dto.KpiComparisonDTO.KpiDelta;
import com.yem.hlm.backend.dashboard.api.dto.KpiComparisonDTO.SparklinePoint;
import com.yem.hlm.backend.dashboard.api.dto.PipelineAnalysisDTO;
import com.yem.hlm.backend.dashboard.api.dto.SmartInsightDTO;
import com.yem.hlm.backend.dashboard.api.dto.SmartInsightDTO.InsightPriority;
import com.yem.hlm.backend.dashboard.api.dto.SmartInsightDTO.InsightType;
import com.yem.hlm.backend.property.domain.PropertyStatus;
import com.yem.hlm.backend.property.repo.PropertyRepository;
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
import java.util.LinkedHashMap;
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
    private final PropertyRepository      propertyRepo;

    public DashboardCockpitService(VenteRepository venteRepo,
                                   VenteEcheanceRepository echeanceRepo,
                                   ReservationRepository reservationRepo,
                                   ContactRepository contactRepo,
                                   PropertyRepository propertyRepo) {
        this.venteRepo       = venteRepo;
        this.echeanceRepo    = echeanceRepo;
        this.reservationRepo = reservationRepo;
        this.contactRepo     = contactRepo;
        this.propertyRepo    = propertyRepo;
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

    // ── 7. Inventory Intelligence ──────────────────────────────────────────

    @Cacheable(value = CacheConfig.DASHBOARD_COCKPIT_CACHE, key = "'inventory:' + #societeId")
    public InventoryIntelligenceDTO getInventoryIntelligence(UUID societeId) {
        List<Object[]> raw = propertyRepo.inventoryByProjectStatusWithValues(societeId);

        Map<UUID, long[]> projectCounts = new LinkedHashMap<>();
        Map<UUID, String> projectNames = new LinkedHashMap<>();
        Map<UUID, BigDecimal[]> projectValues = new LinkedHashMap<>();

        long totalAll = 0, availAll = 0, resAll = 0, soldAll = 0, withdAll = 0;

        for (Object[] r : raw) {
            UUID projectId = (UUID) r[0];
            String projectName = (String) r[1];
            PropertyStatus status = (PropertyStatus) r[2];
            long count = ((Number) r[3]).longValue();
            BigDecimal value = toBigDecimal(r[4]);

            projectNames.putIfAbsent(projectId, projectName);
            long[] c = projectCounts.computeIfAbsent(projectId, k -> new long[5]);
            BigDecimal[] v = projectValues.computeIfAbsent(projectId,
                    k -> new BigDecimal[]{BigDecimal.ZERO, BigDecimal.ZERO});

            c[0] += count;
            v[0] = v[0].add(value);
            totalAll += count;

            switch (status) {
                case ACTIVE   -> { c[1] += count; availAll += count; }
                case RESERVED -> { c[2] += count; resAll   += count; }
                case SOLD     -> { c[3] += count; soldAll  += count; v[1] = v[1].add(value); }
                case WITHDRAWN, ARCHIVED -> { c[4] += count; withdAll += count; }
                default -> {}
            }
        }

        BigDecimal overallAbsorption = absorptionRate(soldAll, availAll + resAll + soldAll);

        List<InventoryIntelligenceDTO.ProjectStock> projects = new ArrayList<>();
        for (Map.Entry<UUID, long[]> e : projectCounts.entrySet()) {
            UUID pid = e.getKey();
            long[] c = e.getValue();
            BigDecimal[] v = projectValues.get(pid);
            long base = c[1] + c[2] + c[3];
            projects.add(new InventoryIntelligenceDTO.ProjectStock(
                    pid, projectNames.get(pid),
                    c[0], c[1], c[2], c[3],
                    absorptionRate(c[3], base),
                    v[0], v[1]));
        }

        return new InventoryIntelligenceDTO(LocalDateTime.now(),
                new InventoryIntelligenceDTO.StockSummary(
                        totalAll, availAll, resAll, soldAll, withdAll, overallAbsorption),
                projects);
    }

    // ── 8. Discount Analytics ───────────────────────────────────────────────

    @Cacheable(value = CacheConfig.DASHBOARD_COCKPIT_CACHE, key = "'discount:' + #societeId")
    public DiscountAnalyticsDTO getDiscountAnalytics(UUID societeId) {
        List<Object[]> summaryRows = venteRepo.discountSummary(societeId);
        Object[] summary = (summaryRows != null && !summaryRows.isEmpty()) ? summaryRows.get(0) : null;

        long dealsWithDiscount = summary != null && summary[0] != null
                ? ((Number) summary[0]).longValue() : 0;
        long totalDeals = summary != null && summary[1] != null
                ? ((Number) summary[1]).longValue() : 0;
        BigDecimal avgPct = summary != null && summary[2] != null
                ? toBigDecimal(summary[2]).setScale(1, RoundingMode.HALF_UP) : null;
        BigDecimal maxPct = summary != null && summary[3] != null
                ? toBigDecimal(summary[3]).setScale(1, RoundingMode.HALF_UP) : null;
        BigDecimal totalVol = summary != null ? toBigDecimal(summary[4]) : BigDecimal.ZERO;

        List<Object[]> agentRows = venteRepo.discountByAgent(societeId);
        List<DiscountAnalyticsDTO.AgentDiscount> agents = agentRows.stream().map(r -> {
            UUID agentId = (UUID) r[0];
            String name = (str(r[1]) + " " + str(r[2])).trim();
            if (name.isBlank()) name = "—";
            long agentDiscounted = ((Number) r[3]).longValue();
            long agentTotal = ((Number) r[4]).longValue();
            BigDecimal agentAvg = r[5] != null
                    ? toBigDecimal(r[5]).setScale(1, RoundingMode.HALF_UP) : null;
            BigDecimal agentVol = toBigDecimal(r[6]);
            return new DiscountAnalyticsDTO.AgentDiscount(
                    agentId, name, agentDiscounted, agentTotal, agentAvg, agentVol);
        }).toList();

        return new DiscountAnalyticsDTO(LocalDateTime.now(),
                dealsWithDiscount, totalDeals, avgPct, maxPct, totalVol, agents);
    }

    // ── 9. Smart Insights ───────────────────────────────────────────────────

    @Cacheable(value = CacheConfig.DASHBOARD_COCKPIT_CACHE, key = "'insights:' + #societeId")
    public List<SmartInsightDTO> getInsights(UUID societeId) {
        List<SmartInsightDTO> insights = new ArrayList<>();
        LocalDateTime now = LocalDateTime.now();
        YearMonth thisMonth = YearMonth.from(now);
        YearMonth prevMonth = thisMonth.minusMonths(1);

        LocalDateTime thisMonthFrom = thisMonth.atDay(1).atStartOfDay();
        LocalDateTime thisMonthTo   = thisMonth.plusMonths(1).atDay(1).atStartOfDay();
        LocalDateTime prevMonthFrom = prevMonth.atDay(1).atStartOfDay();

        // Rule 1 — Revenue pace (current month vs previous)
        BigDecimal caCurrent  = nz(venteRepo.sumPrixVenteInPeriod(
                societeId, thisMonthFrom, thisMonthTo, List.of(VenteStatut.ANNULE)));
        BigDecimal caPrevious = nz(venteRepo.sumPrixVenteInPeriod(
                societeId, prevMonthFrom, thisMonthFrom, List.of(VenteStatut.ANNULE)));

        if (caPrevious.compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal pct = caCurrent.subtract(caPrevious)
                    .divide(caPrevious.abs(), 4, RoundingMode.HALF_UP)
                    .multiply(BigDecimal.valueOf(100))
                    .setScale(1, RoundingMode.HALF_UP);
            if (pct.compareTo(BigDecimal.TEN) > 0) {
                insights.add(new SmartInsightDTO("revenue-up",
                        InsightType.TREND, InsightPriority.MEDIUM,
                        "CA en progression",
                        "+" + pct + "% vs mois précédent — " + fmtAmount(caCurrent) + " signé ce mois",
                        "Détail commercial", "/app/dashboard/commercial"));
            } else if (pct.compareTo(BigDecimal.valueOf(-10)) < 0) {
                insights.add(new SmartInsightDTO("revenue-down",
                        InsightType.RISK, InsightPriority.HIGH,
                        "CA en baisse significative",
                        pct + "% vs mois précédent — action recommandée",
                        "Analyser", "/app/dashboard/commercial"));
            }
        }

        // Rule 2 — Top-performing project by absorption rate
        List<Object[]> inv = propertyRepo.inventoryByProjectStatusWithValues(societeId);
        Map<UUID, long[]> projCounts = new LinkedHashMap<>();
        Map<UUID, String> projNames = new HashMap<>();
        for (Object[] r : inv) {
            UUID pid = (UUID) r[0];
            projNames.putIfAbsent(pid, (String) r[1]);
            long[] c = projCounts.computeIfAbsent(pid, k -> new long[3]);
            PropertyStatus st = (PropertyStatus) r[2];
            long cnt = ((Number) r[3]).longValue();
            switch (st) {
                case ACTIVE   -> c[0] += cnt;
                case RESERVED -> c[1] += cnt;
                case SOLD     -> c[2] += cnt;
                default -> {}
            }
        }
        UUID bestProjId = null;
        BigDecimal bestAbsorption = BigDecimal.ZERO;
        for (Map.Entry<UUID, long[]> e : projCounts.entrySet()) {
            long[] c = e.getValue();
            long base = c[0] + c[1] + c[2];
            if (base >= 5) {
                BigDecimal abs = absorptionRate(c[2], base);
                if (abs != null && abs.compareTo(bestAbsorption) > 0) {
                    bestAbsorption = abs;
                    bestProjId = e.getKey();
                }
            }
        }
        if (bestProjId != null && bestAbsorption.compareTo(BigDecimal.valueOf(60)) >= 0) {
            insights.add(new SmartInsightDTO("top-project",
                    InsightType.OPPORTUNITY, InsightPriority.LOW,
                    projNames.get(bestProjId) + " — forte commercialisation",
                    "Taux d'absorption de " + bestAbsorption + "% — envisagez d'accélérer la livraison",
                    "Voir le projet", "/app/projects/" + bestProjId));
        }

        // Rule 3 — Stalled deals in pipeline
        long stalled = venteRepo.countStalledVentes(societeId,
                List.of(VenteStatut.COMPROMIS, VenteStatut.FINANCEMENT), now.minusDays(30));
        if (stalled >= 3) {
            insights.add(new SmartInsightDTO("stalled-deals",
                    InsightType.RISK, InsightPriority.HIGH,
                    stalled + " vente" + (stalled > 1 ? "s" : "") + " bloquée" + (stalled > 1 ? "s" : ""),
                    "Aucun mouvement depuis +30 jours en COMPROMIS/FINANCEMENT — relance recommandée",
                    "Voir les ventes", "/app/ventes"));
        }

        // Rule 4 — Conversion rate health
        LocalDateTime now30 = now.minusDays(30);
        LocalDateTime prev30 = now.minusDays(60);
        long res30 = reservationRepo.countCreatedInPeriod(societeId, now30, now);
        long ventes30 = venteRepo.countCreatedInPeriod(societeId, now30, now);
        long resPrev = reservationRepo.countCreatedInPeriod(societeId, prev30, now30);
        long ventesPrev = venteRepo.countCreatedInPeriod(societeId, prev30, now30);

        BigDecimal convCurrent = ratio(ventes30, res30);
        BigDecimal convPrev = ratio(ventesPrev, resPrev);
        if (convCurrent != null && convCurrent.compareTo(BigDecimal.valueOf(60)) >= 0) {
            insights.add(new SmartInsightDTO("conversion-healthy",
                    InsightType.INFO, InsightPriority.LOW,
                    "Conversion saine à " + convCurrent + "%",
                    "Réservation → vente : " + ventes30 + " conversions sur " + res30 + " réservations (30j)",
                    null, null));
        } else if (convCurrent != null && convPrev != null
                && convCurrent.compareTo(convPrev.multiply(BigDecimal.valueOf(0.8))) < 0) {
            insights.add(new SmartInsightDTO("conversion-drop",
                    InsightType.RISK, InsightPriority.HIGH,
                    "Conversion en chute",
                    convCurrent + "% vs " + convPrev + "% le mois précédent — identifiez les blocages",
                    "Analyser le pipeline", "/app/ventes"));
        }

        // Rule 5 — Discount margin pressure
        List<Object[]> discSummary = venteRepo.discountSummary(societeId);
        if (discSummary != null && !discSummary.isEmpty()) {
            Object[] ds = discSummary.get(0);
            if (ds[2] != null) {
                BigDecimal avgDisc = toBigDecimal(ds[2]).setScale(1, RoundingMode.HALF_UP);
                if (avgDisc.compareTo(BigDecimal.TEN) > 0) {
                    insights.add(new SmartInsightDTO("discount-pressure",
                            InsightType.RISK, InsightPriority.MEDIUM,
                            "Pression sur les marges",
                            "Remise moyenne de " + avgDisc + "% — revoyez la politique de prix",
                            "Voir les remises", "/app/dashboard"));
                }
            }
        }

        // Rule 6 — Best agent this month
        List<Object[]> topAgents = venteRepo.topAgentsByCA(societeId, thisMonthFrom, now,
                org.springframework.data.domain.PageRequest.of(0, 1));
        if (!topAgents.isEmpty()) {
            Object[] top = topAgents.get(0);
            String agentName = (str(top[1]) + " " + str(top[2])).trim();
            BigDecimal agentCA = toBigDecimal(top[3]);
            long agentCount = ((Number) top[4]).longValue();
            if (agentCA.compareTo(BigDecimal.ZERO) > 0 && !agentName.isBlank()) {
                insights.add(new SmartInsightDTO("top-agent",
                        InsightType.INFO, InsightPriority.LOW,
                        agentName + " — leader du mois",
                        agentCount + " vente" + (agentCount > 1 ? "s" : "") + " pour " + fmtAmount(agentCA),
                        null, null));
            }
        }

        insights.sort(Comparator.comparing(i -> i.priority().ordinal()));
        return insights;
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

    private static String fmtAmount(BigDecimal n) {
        if (n == null || n.compareTo(BigDecimal.ZERO) == 0) return "0 MAD";
        if (n.compareTo(BigDecimal.valueOf(1_000_000)) >= 0) {
            return n.divide(BigDecimal.valueOf(1_000_000), 1, RoundingMode.HALF_UP)
                    .toPlainString().replace('.', ',') + " M MAD";
        }
        if (n.compareTo(BigDecimal.valueOf(1_000)) >= 0) {
            return n.divide(BigDecimal.valueOf(1_000), 0, RoundingMode.HALF_UP)
                    .toPlainString() + " K MAD";
        }
        return n.setScale(0, RoundingMode.HALF_UP).toPlainString() + " MAD";
    }

    private static BigDecimal absorptionRate(long sold, long commercializedBase) {
        if (commercializedBase <= 0) return null;
        return BigDecimal.valueOf(sold)
                .divide(BigDecimal.valueOf(commercializedBase), 4, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100))
                .setScale(1, RoundingMode.HALF_UP);
    }
}
