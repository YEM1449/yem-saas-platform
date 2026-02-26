package com.yem.hlm.backend.dashboard.service;

import com.yem.hlm.backend.auth.config.CacheConfig;
import com.yem.hlm.backend.contract.repo.SaleContractRepository;
import com.yem.hlm.backend.dashboard.api.dto.CommercialDashboardSalesDTO;
import com.yem.hlm.backend.dashboard.api.dto.CommercialDashboardSummaryDTO;
import com.yem.hlm.backend.dashboard.api.dto.DailySalesPoint;
import com.yem.hlm.backend.dashboard.api.dto.SalesByAgentRow;
import com.yem.hlm.backend.dashboard.api.dto.SalesByProjectRow;
import com.yem.hlm.backend.dashboard.api.dto.SalesTableRow;
import com.yem.hlm.backend.deposit.repo.DepositRepository;
import com.yem.hlm.backend.project.repo.ProjectRepository;
import com.yem.hlm.backend.project.service.ProjectNotFoundException;
import com.yem.hlm.backend.property.repo.PropertyRepository;
import com.yem.hlm.backend.property.service.InvalidPeriodException;
import com.yem.hlm.backend.tenant.context.TenantContext;
import com.yem.hlm.backend.user.repo.UserRepository;
import com.yem.hlm.backend.user.service.UserNotFoundException;
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
 * Key = tenantId + effectiveAgentId + from + to + projectId.
 *
 * <h3>Query budget (summary)</h3>
 * Up to 9 aggregate queries; no entity hydration loops.
 */
@Service
@Transactional(readOnly = true)
public class CommercialDashboardService {

    private static final int TOP_N = 10;

    private final SaleContractRepository contractRepository;
    private final DepositRepository      depositRepository;
    private final PropertyRepository     propertyRepository;
    private final ProjectRepository      projectRepository;
    private final UserRepository         userRepository;

    public CommercialDashboardService(
            SaleContractRepository contractRepository,
            DepositRepository depositRepository,
            PropertyRepository propertyRepository,
            ProjectRepository projectRepository,
            UserRepository userRepository) {
        this.contractRepository = contractRepository;
        this.depositRepository  = depositRepository;
        this.propertyRepository = propertyRepository;
        this.projectRepository  = projectRepository;
        this.userRepository     = userRepository;
    }

    // =========================================================================
    // Summary endpoint
    // =========================================================================

    /**
     * Builds the full commercial dashboard summary.
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
            key   = "#tenantId + ':' + #effectiveAgentId + ':' + #from + ':' + #to + ':' + #projectId"
    )
    public CommercialDashboardSummaryDTO getSummary(UUID tenantId,
                                                    LocalDateTime from,
                                                    LocalDateTime to,
                                                    UUID projectId,
                                                    UUID effectiveAgentId) {
        // 1 ─ Sales totals ──────────────────────────────────────────────────────
        List<Object[]> salesRows = contractRepository.salesTotals(tenantId, from, to, projectId, effectiveAgentId);
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
        List<Object[]> depRows = depositRepository.depositTotals(tenantId, from, to, effectiveAgentId);
        long depositsCount          = 0L;
        BigDecimal depositsTotalAmount = BigDecimal.ZERO;
        if (!depRows.isEmpty()) {
            Object[] row = depRows.get(0);
            depositsCount       = toLong(row[0]);
            depositsTotalAmount = toBD(row[1]);
        }

        // 3 ─ Sales by project (top 10) ─────────────────────────────────────────
        List<SalesByProjectRow> salesByProject = contractRepository
                .salesByProject(tenantId, from, to, effectiveAgentId, PageRequest.of(0, TOP_N))
                .stream()
                .map(r -> new SalesByProjectRow((UUID) r[0], (String) r[1], toLong(r[2]), toBD(r[3])))
                .toList();

        // 4 ─ Sales by agent (top 10) ───────────────────────────────────────────
        List<SalesByAgentRow> salesByAgent = contractRepository
                .salesByAgent(tenantId, from, to, projectId, PageRequest.of(0, TOP_N))
                .stream()
                .map(r -> new SalesByAgentRow((UUID) r[0], (String) r[1], toLong(r[2]), toBD(r[3])))
                .toList();

        // 5 ─ Inventory by status ───────────────────────────────────────────────
        Map<String, Long> inventoryByStatus = new HashMap<>();
        propertyRepository.inventoryByStatus(tenantId, projectId)
                .forEach(r -> inventoryByStatus.put(String.valueOf(r[0]), toLong(r[1])));

        // 6 ─ Inventory by type ─────────────────────────────────────────────────
        Map<String, Long> inventoryByType = new HashMap<>();
        propertyRepository.inventoryByType(tenantId, projectId)
                .forEach(r -> inventoryByType.put(String.valueOf(r[0]), toLong(r[1])));

        // 7 ─ Sales trend (daily) ───────────────────────────────────────────────
        List<DailySalesPoint> salesAmountByDay = contractRepository
                .salesAmountByDay(tenantId, from, to, projectId, effectiveAgentId)
                .stream()
                .map(r -> new DailySalesPoint(toLocalDate(r[0]), toBD(r[1])))
                .toList();

        // 8 ─ Deposits trend (daily) ────────────────────────────────────────────
        List<DailySalesPoint> depositsAmountByDay = depositRepository
                .depositsAmountByDay(tenantId, from, to, effectiveAgentId)
                .stream()
                .map(r -> new DailySalesPoint(toLocalDate(r[0]), toBD(r[1])))
                .toList();

        // 9 ─ Cycle time (avgDaysDepositToSale) ────────────────────────────────
        BigDecimal avgDaysDepositToSale = computeAvgCycleTime(
                contractRepository.cycleTimePairs(tenantId, from, to, effectiveAgentId));

        // ─ Conversion rate ─────────────────────────────────────────────────────
        BigDecimal conversionRate = null;
        if (depositsCount > 0) {
            conversionRate = BigDecimal.valueOf(salesCount)
                    .divide(BigDecimal.valueOf(depositsCount), 4, RoundingMode.HALF_UP);
        }

        return new CommercialDashboardSummaryDTO(
                from, to,
                salesCount, salesTotalAmount, avgSaleValue,
                depositsCount, depositsTotalAmount,
                salesByProject, salesByAgent,
                inventoryByStatus, inventoryByType,
                salesAmountByDay, depositsAmountByDay,
                conversionRate, avgDaysDepositToSale
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
    public CommercialDashboardSalesDTO getSales(UUID tenantId,
                                                LocalDateTime from,
                                                LocalDateTime to,
                                                UUID projectId,
                                                UUID effectiveAgentId,
                                                int page,
                                                int size) {
        int safeSize = Math.min(size, 100);
        Page<Object[]> raw = contractRepository.salesForTable(
                tenantId, from, to, projectId, effectiveAgentId,
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
        List<Object[]> totals = contractRepository.salesTotals(tenantId, from, to, projectId, effectiveAgentId);
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
     * Validates projectId belongs to the tenant.
     * @throws ProjectNotFoundException on mismatch
     */
    public void validateProject(UUID tenantId, UUID projectId) {
        if (projectId != null) {
            projectRepository.findByTenant_IdAndId(tenantId, projectId)
                    .orElseThrow(() -> new ProjectNotFoundException(projectId));
        }
    }

    /**
     * Resolves the effective agentId applying RBAC:
     * <ul>
     *   <li>AGENT callers → forced to their own userId (ignores {@code requestedAgentId}).</li>
     *   <li>ADMIN/MANAGER → {@code requestedAgentId} accepted (validated if not null).</li>
     * </ul>
     * @throws UserNotFoundException if requestedAgentId is provided but not found in tenant
     */
    public UUID resolveEffectiveAgentId(UUID tenantId, UUID requestedAgentId) {
        if (callerIsAgent()) {
            return TenantContext.getUserId();
        }
        if (requestedAgentId != null) {
            userRepository.findByTenant_IdAndId(tenantId, requestedAgentId)
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
