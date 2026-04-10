package com.yem.hlm.backend.dashboard.service;

import com.yem.hlm.backend.auth.config.CacheConfig;
import com.yem.hlm.backend.contact.domain.ContactStatus;
import com.yem.hlm.backend.contact.repo.ContactRepository;
import com.yem.hlm.backend.contract.repo.SaleContractRepository;
import com.yem.hlm.backend.dashboard.api.dto.CommercialDashboardSalesDTO;
import com.yem.hlm.backend.dashboard.api.dto.CommercialDashboardSummaryDTO;
import com.yem.hlm.backend.dashboard.api.dto.DailySalesPoint;
import com.yem.hlm.backend.dashboard.api.dto.DiscountByAgentRow;
import com.yem.hlm.backend.dashboard.api.dto.ProspectSourceRow;
import com.yem.hlm.backend.dashboard.api.dto.SalesByAgentRow;
import com.yem.hlm.backend.dashboard.api.dto.SalesByProjectRow;
import com.yem.hlm.backend.dashboard.api.dto.SalesTableRow;
import com.yem.hlm.backend.deposit.repo.DepositRepository;
import com.yem.hlm.backend.project.repo.ProjectRepository;
import com.yem.hlm.backend.reservation.domain.ReservationStatus;
import com.yem.hlm.backend.reservation.repo.ReservationRepository;
import com.yem.hlm.backend.project.service.ProjectNotFoundException;
import com.yem.hlm.backend.property.repo.PropertyRepository;
import com.yem.hlm.backend.property.service.InvalidPeriodException;
import com.yem.hlm.backend.societe.SocieteContext;
import com.yem.hlm.backend.vente.domain.VenteStatut;
import com.yem.hlm.backend.vente.repo.VenteRepository;
import com.yem.hlm.backend.user.repo.UserRepository;
import com.yem.hlm.backend.user.service.UserNotFoundException;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Provides commercial dashboard KPIs in a single logical call per screen.
 *
 * <h3>RBAC scoping</h3>
 * <ul>
 *   <li>ADMIN / MANAGER: full tenant data; optional {@code agentId} filter accepted.</li>
 *   <li>AGENT: {@code agentId} forced to caller's userId; any supplied value is ignored.</li>
 * </ul>
 *
 * <h3>Caching</h3>
 * Cache name: {@value CacheConfig#COMMERCIAL_DASHBOARD_CACHE}, TTL 30 s.
 * Key = societeId + effectiveAgentId + from + to + projectId.
 *
 * <h3>Query budget (summary)</h3>
 * Up to 16 aggregate queries; no entity hydration loops.
 * Queries 12–13: discount analytics (F3.2); query 14: prospect source funnel (F3.4).
 * Queries 15–16: property holds (property_reservation ACTIVE count + expiring-soon count).
 */
@Service
@Transactional(readOnly = true)
public class CommercialDashboardService {

    private static final Logger log = LoggerFactory.getLogger(CommercialDashboardService.class);
    private static final int TOP_N = 10;
    /** Log a warning when summary generation exceeds this threshold (ms). */
    private static final long SLOW_QUERY_THRESHOLD_MS = 300;

    private static final List<VenteStatut> TERMINAL_STATUTS =
            List.of(VenteStatut.LIVRE, VenteStatut.ANNULE);

    private final SaleContractRepository contractRepository;
    private final DepositRepository      depositRepository;
    private final PropertyRepository     propertyRepository;
    private final ProjectRepository      projectRepository;
    private final UserRepository         userRepository;
    private final ContactRepository      contactRepository;
    private final ReservationRepository  reservationRepository;
    private final VenteRepository        venteRepository;
    private final MeterRegistry          meterRegistry;
    private final Timer                  summaryTimer;

    public CommercialDashboardService(
            SaleContractRepository contractRepository,
            DepositRepository depositRepository,
            PropertyRepository propertyRepository,
            ProjectRepository projectRepository,
            UserRepository userRepository,
            ContactRepository contactRepository,
            ReservationRepository reservationRepository,
            VenteRepository venteRepository,
            MeterRegistry meterRegistry) {
        this.contractRepository  = contractRepository;
        this.depositRepository   = depositRepository;
        this.propertyRepository  = propertyRepository;
        this.projectRepository   = projectRepository;
        this.userRepository      = userRepository;
        this.contactRepository   = contactRepository;
        this.reservationRepository = reservationRepository;
        this.venteRepository     = venteRepository;
        this.meterRegistry       = meterRegistry;
        this.summaryTimer = Timer.builder("commercial_dashboard_summary_duration")
                .description("Time to compute a fresh commercial dashboard summary (cache misses only)")
                .register(meterRegistry);
    }

    // =========================================================================
    // Summary endpoint
    // =========================================================================

    /**
     * Builds the full commercial dashboard summary.
     *
     * <p>The method body only executes on cache misses; cached results are returned directly
     * by the Spring proxy without entering this method. The Micrometer timer therefore measures
     * only the actual DB computation cost (cache-miss latency).
     *
     * @param from       range start (inclusive); defaults to 30 days ago if null
     * @param to         range end (inclusive); defaults to now if null
     * @param projectId  optional project filter (validated against tenant)
     * @param agentId    optional agent filter (ADMIN/MANAGER only; AGENT callers override with self)
     * @return fully populated {@link CommercialDashboardSummaryDTO}
     * @throws InvalidPeriodException if {@code from} is after {@code to}
     * @throws ProjectNotFoundException if {@code projectId} doesn't exist in the tenant
     * @throws UserNotFoundException if {@code agentId} doesn't exist in the tenant (ADMIN/MANAGER only)
     */
    @Cacheable(
            value = CacheConfig.COMMERCIAL_DASHBOARD_CACHE,
            // Truncate to minutes so requests within the same minute share a cache entry.
            // LocalDateTime.toString() includes nanoseconds which differ on every call,
            // making every request a cache miss and rendering the 30 s TTL useless.
            key   = "#societeId + ':' + #effectiveAgentId + ':'"
                  + "+ (#from  == null ? 'null' : #from.truncatedTo(T(java.time.temporal.ChronoUnit).MINUTES).toString()) + ':'"
                  + "+ (#to    == null ? 'null' : #to.truncatedTo(T(java.time.temporal.ChronoUnit).MINUTES).toString()) + ':'"
                  + "+ #projectId"
    )
    public CommercialDashboardSummaryDTO getSummary(UUID societeId,
                                                    LocalDateTime from,
                                                    LocalDateTime to,
                                                    UUID projectId,
                                                    UUID effectiveAgentId) {
        Timer.Sample sample = Timer.start(meterRegistry);
        long startMs = System.currentTimeMillis();
        meterRegistry.counter("commercial_dashboard_summary_cache_misses_total").increment();

        try {
            return computeSummary(societeId, from, to, projectId, effectiveAgentId);
        } finally {
            sample.stop(summaryTimer);
            long elapsedMs = System.currentTimeMillis() - startMs;
            if (elapsedMs > SLOW_QUERY_THRESHOLD_MS) {
                log.warn("[CommercialDashboard] Slow summary generation: {}ms (societe={}, agent={})",
                        elapsedMs, societeId, effectiveAgentId);
            }
        }
    }

    private CommercialDashboardSummaryDTO computeSummary(UUID societeId,
                                                         LocalDateTime from,
                                                         LocalDateTime to,
                                                         UUID projectId,
                                                         UUID effectiveAgentId) {
        // 1 ─ Sales totals ──────────────────────────────────────────────────────
        List<Object[]> salesRows = contractRepository.salesTotals(societeId, from, to, projectId, effectiveAgentId);
        long salesCount = 0L;
        BigDecimal salesTotalAmount = BigDecimal.ZERO;
        BigDecimal avgSaleValue     = BigDecimal.ZERO;
        if (!salesRows.isEmpty()) {
            Object[] row = salesRows.get(0);
            salesCount       = toLong(row[0]);
            salesTotalAmount = toBD(row[1]);
            avgSaleValue     = toBD(row[2]);
        }

        // 2 ─ Deposit totals ────────────────────────────────────────────────────
        List<Object[]> depRows = depositRepository.depositTotals(societeId, from, to, effectiveAgentId);
        long depositsCount          = 0L;
        BigDecimal depositsTotalAmount = BigDecimal.ZERO;
        if (!depRows.isEmpty()) {
            Object[] row = depRows.get(0);
            depositsCount       = toLong(row[0]);
            depositsTotalAmount = toBD(row[1]);
        }

        // 3 ─ Sales by project (top 10) ─────────────────────────────────────────
        List<SalesByProjectRow> salesByProject = contractRepository
                .salesByProject(societeId, from, to, effectiveAgentId, PageRequest.of(0, TOP_N))
                .stream()
                .map(r -> new SalesByProjectRow((UUID) r[0], (String) r[1], toLong(r[2]), toBD(r[3])))
                .toList();

        // 4 ─ Sales by agent (top 10) ───────────────────────────────────────────
        List<SalesByAgentRow> salesByAgent = contractRepository
                .salesByAgent(societeId, from, to, projectId, PageRequest.of(0, TOP_N))
                .stream()
                .map(r -> new SalesByAgentRow((UUID) r[0], (String) r[1], toLong(r[2]), toBD(r[3])))
                .toList();

        // 5 ─ Inventory by status ───────────────────────────────────────────────
        Map<String, Long> inventoryByStatus = new HashMap<>();
        propertyRepository.inventoryByStatus(societeId, projectId)
                .forEach(r -> inventoryByStatus.put(String.valueOf(r[0]), toLong(r[1])));

        // 6 ─ Inventory by type ─────────────────────────────────────────────────
        Map<String, Long> inventoryByType = new HashMap<>();
        propertyRepository.inventoryByType(societeId, projectId)
                .forEach(r -> inventoryByType.put(String.valueOf(r[0]), toLong(r[1])));

        // 7 ─ Sales trend (daily) ───────────────────────────────────────────────
        List<DailySalesPoint> salesAmountByDay = contractRepository
                .salesAmountByDay(societeId, from, to, projectId, effectiveAgentId)
                .stream()
                .map(r -> new DailySalesPoint(toLocalDate(r[0]), toBD(r[1])))
                .toList();

        // 8 ─ Deposits trend (daily) ────────────────────────────────────────────
        List<DailySalesPoint> depositsAmountByDay = depositRepository
                .depositsAmountByDay(societeId, from, to, effectiveAgentId)
                .stream()
                .map(r -> new DailySalesPoint(toLocalDate(r[0]), toBD(r[1])))
                .toList();

        // 9 ─ Cycle time (avgDaysDepositToSale) ────────────────────────────────
        BigDecimal avgDaysDepositToSale = computeAvgCycleTime(
                contractRepository.cycleTimePairs(societeId, from, to, effectiveAgentId));

        // 10 ─ Active reservations snapshot (not date-filtered) ────────────────
        List<Object[]> activeRows = depositRepository.activeReservationTotals(societeId, effectiveAgentId);
        long activeReservationsCount = 0L;
        BigDecimal activeReservationsTotalAmount = BigDecimal.ZERO;
        if (!activeRows.isEmpty()) {
            Object[] row = activeRows.get(0);
            activeReservationsCount       = toLong(row[0]);
            activeReservationsTotalAmount = toBD(row[1]);
        }

        BigDecimal avgReservationAgeDays = computeAvgReservationAge(
                depositRepository.activeReservationDepositDates(societeId, effectiveAgentId));

        // 11 ─ Active prospects (tenant-wide, not date/agent-filtered) ──────────
        long activeProspectsCount = contactRepository.countActiveProspects(
                societeId,
                List.of(ContactStatus.PROSPECT, ContactStatus.QUALIFIED_PROSPECT));

        // ─ Conversion rate ─────────────────────────────────────────────────────
        BigDecimal conversionRate = null;
        if (depositsCount > 0) {
            conversionRate = BigDecimal.valueOf(salesCount)
                    .divide(BigDecimal.valueOf(depositsCount), 4, RoundingMode.HALF_UP);
        }

        // 12 ─ Discount totals (avg + max percent) ──────────────────────────────
        List<Object[]> discountRows = contractRepository.discountTotals(
                societeId, effectiveAgentId, projectId);
        BigDecimal avgDiscountPercent = null;
        BigDecimal maxDiscountPercent = null;
        if (!discountRows.isEmpty()) {
            Object[] dr = discountRows.get(0);
            avgDiscountPercent = dr[0] != null ? toBD(dr[0]).setScale(2, RoundingMode.HALF_UP) : null;
            maxDiscountPercent = dr[1] != null ? toBD(dr[1]).setScale(2, RoundingMode.HALF_UP) : null;
        }

        // 13 ─ Discount by agent (top 10) ───────────────────────────────────────
        List<DiscountByAgentRow> discountByAgent = contractRepository
                .discountByAgent(societeId, PageRequest.of(0, TOP_N))
                .stream()
                .map(r -> new DiscountByAgentRow(
                        (UUID)   r[0],
                        (String) r[1],
                        toBD(    r[2]).setScale(2, RoundingMode.HALF_UP),
                        toLong(  r[3])
                ))
                .toList();

        // 14 ─ Prospect source funnel ────────────────────────────────────────────
        List<ContactStatus> convertedStatuses = List.of(
                ContactStatus.CLIENT, ContactStatus.ACTIVE_CLIENT,
                ContactStatus.COMPLETED_CLIENT, ContactStatus.REFERRAL);
        List<ProspectSourceRow> prospectsBySource = contactRepository
                .prospectSourceFunnel(societeId, convertedStatuses)
                .stream()
                .map(r -> {
                    long total     = toLong(r[1]);
                    long converted = toLong(r[2]);
                    Double rate    = total > 0 ? (double) converted / total : null;
                    return new ProspectSourceRow((String) r[0], total, converted, rate);
                })
                .toList();

        // 15 ─ Property holds (ACTIVE property_reservation count) ─────────────
        long propertyHoldsCount = reservationRepository
                .countBySocieteIdAndStatus(societeId, ReservationStatus.ACTIVE);

        // 16 ─ Property holds expiring within 48 h ─────────────────────────────
        LocalDateTime now48 = LocalDateTime.now();
        long propertyHoldsExpiringSoon = reservationRepository
                .countExpiringBefore(societeId, now48, now48.plusHours(48));

        // 17 ─ Ventes par statut (active pipeline, non-terminal) ───────────────
        Map<String, Long> ventesParStatut = new java.util.LinkedHashMap<>();
        venteRepository.countByStatut(societeId, TERMINAL_STATUTS)
                .forEach(r -> ventesParStatut.put(r[0].toString(), (Long) r[1]));

        // 18 ─ CA pipeline actif ────────────────────────────────────────────────
        BigDecimal caActivePipeline = venteRepository.sumPrixVente(societeId, TERMINAL_STATUTS);
        if (caActivePipeline == null) caActivePipeline = BigDecimal.ZERO;

        // 19 ─ Taux d'absorption + stock commercialisé ─────────────────────────
        long biensActifs   = inventoryByStatus.getOrDefault("ACTIVE",   0L);
        long biensReserves = inventoryByStatus.getOrDefault("RESERVED", 0L);
        long biensVendus   = inventoryByStatus.getOrDefault("SOLD",     0L);
        long stockCommercialise = biensActifs + biensReserves + biensVendus;
        BigDecimal tauxAbsorption = null;
        if (stockCommercialise > 0) {
            tauxAbsorption = BigDecimal.valueOf(biensVendus)
                    .divide(BigDecimal.valueOf(stockCommercialise), 4, RoundingMode.HALF_UP)
                    .multiply(BigDecimal.valueOf(100))
                    .setScale(1, RoundingMode.HALF_UP);
        }

        return new CommercialDashboardSummaryDTO(
                from, to,
                LocalDateTime.now(),          // asOf
                salesCount, salesTotalAmount, avgSaleValue,
                depositsCount, depositsTotalAmount,
                activeReservationsCount, activeReservationsTotalAmount, avgReservationAgeDays,
                activeProspectsCount,
                salesByProject, salesByAgent,
                inventoryByStatus, inventoryByType,
                salesAmountByDay, depositsAmountByDay,
                conversionRate, avgDaysDepositToSale,
                avgDiscountPercent, maxDiscountPercent, discountByAgent,
                prospectsBySource,
                propertyHoldsCount, propertyHoldsExpiringSoon,
                ventesParStatut, caActivePipeline,
                tauxAbsorption, stockCommercialise
        );
    }

    // =========================================================================
    // Drill-down: paginated sales table
    // =========================================================================

    /**
     * Returns a paginated table of SIGNED contracts for the drill-down view.
     *
     * @param page 0-based page index
     * @param size page size (max 100)
     */
    public CommercialDashboardSalesDTO getSales(UUID societeId,
                                                LocalDateTime from,
                                                LocalDateTime to,
                                                UUID projectId,
                                                UUID effectiveAgentId,
                                                int page,
                                                int size) {
        int safeSize = Math.min(size, 100);
        Page<Object[]> raw = contractRepository.salesForTable(
                societeId, from, to, projectId, effectiveAgentId,
                PageRequest.of(page, safeSize));

        List<SalesTableRow> rows = raw.getContent().stream()
                .map(r -> new SalesTableRow(
                        (UUID)            r[0],
                        (LocalDateTime)   r[1],
                        (String)          r[2],
                        (String)          r[3],
                        (String)          r[4],
                        (String)          r[5],
                        toBD(             r[6])
                ))
                .toList();

        // Reuse total amount from summary totals (1 extra query) for the table header
        List<Object[]> totals = contractRepository.salesTotals(societeId, from, to, projectId, effectiveAgentId);
        BigDecimal totalAmount = totals.isEmpty() ? BigDecimal.ZERO : toBD(totals.get(0)[1]);

        return new CommercialDashboardSalesDTO(
                raw.getTotalElements(),
                totalAmount,
                raw.getNumber(),
                raw.getSize(),
                raw.getTotalPages(),
                rows
        );
    }

    // =========================================================================
    // Parameter resolution (called from controller before caching)
    // =========================================================================

    /** Resolves defaults and validates date range. Does NOT cache — called before getSummary(). */
    public LocalDateTime[] resolveDateRange(LocalDateTime from, LocalDateTime to) {
        LocalDateTime resolvedTo   = (to   != null) ? to   : LocalDateTime.now();
        LocalDateTime resolvedFrom = (from != null) ? from : resolvedTo.minusDays(30);
        if (resolvedFrom.isAfter(resolvedTo)) {
            throw new InvalidPeriodException("'from' must not be after 'to'");
        }
        return new LocalDateTime[]{resolvedFrom, resolvedTo};
    }

    /**
     * Validates projectId belongs to the société.
     * @throws ProjectNotFoundException on mismatch
     */
    public void validateProject(UUID societeId, UUID projectId) {
        if (projectId != null) {
            projectRepository.findBySocieteIdAndId(societeId, projectId)
                    .orElseThrow(() -> new ProjectNotFoundException(projectId));
        }
    }

    /**
     * Resolves the effective agentId applying RBAC.
     * @throws UserNotFoundException if requestedAgentId is provided but not found
     */
    public UUID resolveEffectiveAgentId(UUID societeId, UUID requestedAgentId) {
        if (callerIsAgent()) {
            return SocieteContext.getUserId();
        }
        if (requestedAgentId != null) {
            userRepository.findById(requestedAgentId)
                    .orElseThrow(() -> new UserNotFoundException(requestedAgentId));
        }
        return requestedAgentId;
    }

    // =========================================================================
    // Private helpers
    // =========================================================================

    private boolean callerIsAgent() {
        return SecurityContextHolder.getContext().getAuthentication()
                .getAuthorities().stream()
                .anyMatch(a -> "ROLE_AGENT".equals(a.getAuthority()));
    }

    /** Average age in days from depositDate to today for each active reservation. */
    private BigDecimal computeAvgReservationAge(List<LocalDate> depositDates) {
        if (depositDates == null || depositDates.isEmpty()) return null;
        LocalDate today = LocalDate.now();
        List<LocalDate> nonNull = depositDates.stream().filter(d -> d != null).toList();
        if (nonNull.isEmpty()) return null;
        double totalDays = nonNull.stream()
                .mapToLong(d -> ChronoUnit.DAYS.between(d, today))
                .sum();
        return BigDecimal.valueOf(totalDays / nonNull.size()).setScale(1, RoundingMode.HALF_UP);
    }

    private BigDecimal computeAvgCycleTime(List<Object[]> pairs) {
        if (pairs.isEmpty()) return null;
        double totalDays = 0;
        int count = 0;
        for (Object[] row : pairs) {
            LocalDateTime signedAt    = (LocalDateTime) row[0];
            LocalDateTime confirmedAt = (LocalDateTime) row[1];
            if (signedAt != null && confirmedAt != null) {
                totalDays += ChronoUnit.HOURS.between(confirmedAt, signedAt) / 24.0;
                count++;
            }
        }
        return count == 0 ? null :
                BigDecimal.valueOf(totalDays / count).setScale(1, RoundingMode.HALF_UP);
    }

    private static long toLong(Object o) {
        if (o == null) return 0L;
        if (o instanceof Long l) return l;
        if (o instanceof Number n) return n.longValue();
        return 0L;
    }

    private static BigDecimal toBD(Object o) {
        if (o == null) return BigDecimal.ZERO;
        if (o instanceof BigDecimal bd) return bd;
        if (o instanceof Number n) return BigDecimal.valueOf(n.doubleValue());
        return BigDecimal.ZERO;
    }

    private static LocalDate toLocalDate(Object o) {
        if (o instanceof LocalDate ld) return ld;
        if (o instanceof java.sql.Date d) return d.toLocalDate();
        return LocalDate.now();
    }
}
