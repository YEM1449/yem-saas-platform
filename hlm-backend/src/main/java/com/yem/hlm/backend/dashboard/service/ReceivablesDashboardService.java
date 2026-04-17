package com.yem.hlm.backend.dashboard.service;

import com.yem.hlm.backend.auth.config.CacheConfig;
import com.yem.hlm.backend.dashboard.api.dto.AgingBucketDTO;
import com.yem.hlm.backend.dashboard.api.dto.OverdueByProjectRow;
import com.yem.hlm.backend.dashboard.api.dto.ReceivablesDashboardDTO;
import com.yem.hlm.backend.dashboard.api.dto.RecentPaymentRow;
import com.yem.hlm.backend.dashboard.api.dto.VenteReceivablesSummary;
import com.yem.hlm.backend.payments.repo.PaymentScheduleItemRepository;
import com.yem.hlm.backend.payments.repo.SchedulePaymentRepository;
import com.yem.hlm.backend.societe.SocieteContext;
import com.yem.hlm.backend.vente.repo.VenteEcheanceRepository;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

/**
 * Provides the receivables dashboard KPIs for GET /api/dashboard/receivables.
 *
 * <h3>RBAC scoping</h3>
 * ADMIN/MANAGER see all tenant data (optional agentId filter).
 * AGENT sees only their own contracts.
 *
 * <h3>Caching</h3>
 * Cache name: {@value CacheConfig#RECEIVABLES_DASHBOARD_CACHE}, TTL 30 s.
 * Key = societeId + effectiveAgentId.
 *
 * <h3>Query budget</h3>
 * 5 aggregate queries against v2 payment_schedule_item / schedule_payment tables;
 * no entity hydration loops.
 */
@Service
@Transactional(readOnly = true)
public class ReceivablesDashboardService {

    private static final int TOP_N    = 10;
    private static final int RECENT_N = 10;

    private final PaymentScheduleItemRepository itemRepository;
    private final SchedulePaymentRepository     paymentRepository;
    private final VenteEcheanceRepository       echeanceRepository;

    public ReceivablesDashboardService(PaymentScheduleItemRepository itemRepository,
                                       SchedulePaymentRepository paymentRepository,
                                       VenteEcheanceRepository echeanceRepository) {
        this.itemRepository     = itemRepository;
        this.paymentRepository  = paymentRepository;
        this.echeanceRepository = echeanceRepository;
    }

    /**
     * Resolves the effective agentId applying RBAC (mirrors CommercialDashboardService pattern).
     * AGENT: forced to caller's own userId. ADMIN/MANAGER: uses provided value (null = all).
     */
    public UUID resolveEffectiveAgentId(UUID requestedAgentId) {
        if (callerIsAgent()) {
            return SocieteContext.getUserId();
        }
        return requestedAgentId;
    }

    @Cacheable(
            value = CacheConfig.RECEIVABLES_DASHBOARD_CACHE,
            key   = "#societeId + ':' + #effectiveAgentId"
    )
    public ReceivablesDashboardDTO getSummary(UUID societeId, UUID effectiveAgentId) {
        return computeSummary(societeId, effectiveAgentId);
    }

    private ReceivablesDashboardDTO computeSummary(UUID societeId, UUID effectiveAgentId) {

        // 1 — Outstanding + overdue totals
        List<Object[]> totals = effectiveAgentId == null
                ? itemRepository.receivablesTotals(societeId)
                : itemRepository.receivablesTotalsForAgent(societeId, effectiveAgentId);
        BigDecimal totalOutstanding = BigDecimal.ZERO;
        BigDecimal totalOverdue     = BigDecimal.ZERO;
        if (!totals.isEmpty()) {
            Object[] row = totals.get(0);
            totalOutstanding = toBD(row[0]);
            totalOverdue     = toBD(row[1]);
        }

        // 2 — Collection rate: totalReceived / totalIssued * 100
        BigDecimal totalIssued   = itemRepository.totalIssuedAmount(societeId);
        BigDecimal totalReceived = paymentRepository.totalReceived(societeId);
        BigDecimal collectionRate = null;
        if (totalIssued != null && totalIssued.compareTo(BigDecimal.ZERO) > 0) {
            collectionRate = totalReceived.divide(totalIssued, 4, RoundingMode.HALF_UP)
                    .multiply(BigDecimal.valueOf(100))
                    .setScale(2, RoundingMode.HALF_UP);
        }

        // 3 — Average days to payment (DATE(issued_at) → DATE(paid_at) pairs)
        BigDecimal avgDaysToPayment = computeAvgDaysToPayment(
                paymentRepository.issuedAndReceivedPairs(societeId));

        // 4 — Aging buckets
        List<Object[]> agingRows = effectiveAgentId == null
                ? itemRepository.outstandingForAging(societeId)
                : itemRepository.outstandingForAgingByAgent(societeId, effectiveAgentId);
        AgingBuckets buckets = buildAgingBuckets(agingRows, LocalDate.now());

        // 5 — Overdue by project (top 10)
        List<OverdueByProjectRow> overdueByProject = itemRepository
                .overdueByProject(societeId, PageRequest.of(0, TOP_N))
                .stream()
                .map(r -> new OverdueByProjectRow((UUID) r[0], (String) r[1], toBD(r[2])))
                .toList();

        // 6 — Recent payments (last 10)
        List<RecentPaymentRow> recentPayments = (effectiveAgentId == null
                ? paymentRepository.recentPayments(societeId, PageRequest.of(0, RECENT_N))
                : paymentRepository.recentPaymentsByAgent(societeId, effectiveAgentId, PageRequest.of(0, RECENT_N)))
                .stream()
                .map(r -> new RecentPaymentRow(
                        (UUID)      r[0],
                        toBD(       r[1]),
                        toLocalDate(r[2]),
                        String.valueOf(r[3]),   // channel (String in v2)
                        (String)    r[4],
                        (String)    r[5],
                        (String)    r[6]
                ))
                .toList();

        return new ReceivablesDashboardDTO(
                LocalDateTime.now(),
                totalOutstanding, totalOverdue,
                collectionRate, avgDaysToPayment,
                buckets.current, buckets.days30, buckets.days60, buckets.days90, buckets.days90plus,
                overdueByProject, recentPayments
        );
    }

    // =========================================================================
    // Vente pipeline receivables (VenteEcheance-based)
    // =========================================================================

    public VenteReceivablesSummary getVenteReceivablesSummary(UUID societeId) {
        Object[] row = echeanceRepository.getVenteReceivablesAging(societeId);
        BigDecimal current   = toBD(row[0]);
        BigDecimal b1        = toBD(row[1]);
        BigDecimal b2        = toBD(row[2]);
        BigDecimal b3        = toBD(row[3]);
        BigDecimal b4        = toBD(row[4]);
        BigDecimal totalOut  = toBD(row[5]);
        BigDecimal overdue   = b1.add(b2).add(b3).add(b4);
        return new VenteReceivablesSummary(totalOut, overdue, current, b1, b2, b3, b4);
    }

    // =========================================================================
    // Private helpers
    // =========================================================================

    private boolean callerIsAgent() {
        return SecurityContextHolder.getContext().getAuthentication()
                .getAuthorities().stream()
                .anyMatch(a -> "ROLE_AGENT".equals(a.getAuthority()));
    }

    private BigDecimal computeAvgDaysToPayment(List<Object[]> pairs) {
        if (pairs == null || pairs.isEmpty()) return null;
        double total = 0;
        int count = 0;
        for (Object[] row : pairs) {
            LocalDate issuedDate = toLocalDate(row[0]);
            LocalDate receivedAt = toLocalDate(row[1]);
            if (issuedDate != null && receivedAt != null) {
                total += ChronoUnit.DAYS.between(issuedDate, receivedAt);
                count++;
            }
        }
        if (count == 0) return null;
        return BigDecimal.valueOf(total / count).setScale(1, RoundingMode.HALF_UP);
    }

    private AgingBuckets buildAgingBuckets(List<Object[]> rows, LocalDate today) {
        long currentCount = 0; BigDecimal currentAmt = BigDecimal.ZERO;
        long d30Count = 0;     BigDecimal d30Amt     = BigDecimal.ZERO;
        long d60Count = 0;     BigDecimal d60Amt     = BigDecimal.ZERO;
        long d90Count = 0;     BigDecimal d90Amt     = BigDecimal.ZERO;
        long d90pCount = 0;    BigDecimal d90pAmt    = BigDecimal.ZERO;

        for (Object[] row : rows) {
            BigDecimal amount  = toBD(row[0]);
            LocalDate dueDate  = toLocalDate(row[1]);
            if (dueDate == null) continue;
            long daysOverdue = ChronoUnit.DAYS.between(dueDate, today);
            if (daysOverdue <= 0) {
                currentCount++; currentAmt = currentAmt.add(amount);
            } else if (daysOverdue <= 30) {
                d30Count++;    d30Amt = d30Amt.add(amount);
            } else if (daysOverdue <= 60) {
                d60Count++;    d60Amt = d60Amt.add(amount);
            } else if (daysOverdue <= 90) {
                d90Count++;    d90Amt = d90Amt.add(amount);
            } else {
                d90pCount++;   d90pAmt = d90pAmt.add(amount);
            }
        }
        return new AgingBuckets(
                new AgingBucketDTO(currentCount, currentAmt),
                new AgingBucketDTO(d30Count, d30Amt),
                new AgingBucketDTO(d60Count, d60Amt),
                new AgingBucketDTO(d90Count, d90Amt),
                new AgingBucketDTO(d90pCount, d90pAmt)
        );
    }

    private record AgingBuckets(AgingBucketDTO current, AgingBucketDTO days30,
                                AgingBucketDTO days60, AgingBucketDTO days90,
                                AgingBucketDTO days90plus) {}

    private static BigDecimal toBD(Object o) {
        if (o == null) return BigDecimal.ZERO;
        if (o instanceof BigDecimal bd) return bd;
        if (o instanceof Number n) return BigDecimal.valueOf(n.doubleValue());
        return BigDecimal.ZERO;
    }

    private static LocalDate toLocalDate(Object o) {
        if (o instanceof LocalDate ld) return ld;
        if (o instanceof java.sql.Date d) return d.toLocalDate();
        return null;
    }
}
