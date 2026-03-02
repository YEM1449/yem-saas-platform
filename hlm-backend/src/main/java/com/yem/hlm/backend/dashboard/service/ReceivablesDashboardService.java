package com.yem.hlm.backend.dashboard.service;

import com.yem.hlm.backend.auth.config.CacheConfig;
import com.yem.hlm.backend.dashboard.api.dto.AgingBucketDTO;
import com.yem.hlm.backend.dashboard.api.dto.OverdueByProjectRow;
import com.yem.hlm.backend.dashboard.api.dto.ReceivablesDashboardDTO;
import com.yem.hlm.backend.dashboard.api.dto.RecentPaymentRow;
import com.yem.hlm.backend.payment.domain.PaymentMethod;
import com.yem.hlm.backend.payment.repo.PaymentCallRepository;
import com.yem.hlm.backend.payment.repo.PaymentRepository;
import com.yem.hlm.backend.tenant.context.TenantContext;
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
 * Key = tenantId + effectiveAgentId.
 *
 * <h3>Query budget</h3>
 * 5 aggregate queries; no entity hydration loops.
 */
@Service
@Transactional(readOnly = true)
public class ReceivablesDashboardService {

    private static final int TOP_N    = 10;
    private static final int RECENT_N = 10;

    private final PaymentCallRepository callRepository;
    private final PaymentRepository     paymentRepository;

    public ReceivablesDashboardService(PaymentCallRepository callRepository,
                                       PaymentRepository paymentRepository) {
        this.callRepository    = callRepository;
        this.paymentRepository = paymentRepository;
    }

    /**
     * Resolves the effective agentId applying RBAC (mirrors CommercialDashboardService pattern).
     * AGENT: forced to caller's own userId. ADMIN/MANAGER: uses provided value (null = all).
     */
    public UUID resolveEffectiveAgentId(UUID requestedAgentId) {
        if (callerIsAgent()) {
            return TenantContext.getUserId();
        }
        return requestedAgentId;
    }

    @Cacheable(
            value = CacheConfig.RECEIVABLES_DASHBOARD_CACHE,
            key   = "#tenantId + ':' + #effectiveAgentId"
    )
    public ReceivablesDashboardDTO getSummary(UUID tenantId, UUID effectiveAgentId) {
        return computeSummary(tenantId, effectiveAgentId);
    }

    private ReceivablesDashboardDTO computeSummary(UUID tenantId, UUID effectiveAgentId) {

        // 1 — Outstanding + overdue totals
        List<Object[]> totals = callRepository.receivablesTotals(tenantId, effectiveAgentId);
        BigDecimal totalOutstanding = BigDecimal.ZERO;
        BigDecimal totalOverdue     = BigDecimal.ZERO;
        if (!totals.isEmpty()) {
            Object[] row = totals.get(0);
            totalOutstanding = toBD(row[0]);
            totalOverdue     = toBD(row[1]);
        }

        // 2 — Collection rate: totalReceived / totalIssued * 100
        BigDecimal totalIssued   = callRepository.totalIssuedAmount(tenantId);
        BigDecimal totalReceived = paymentRepository.totalReceived(tenantId);
        BigDecimal collectionRate = null;
        if (totalIssued != null && totalIssued.compareTo(BigDecimal.ZERO) > 0) {
            collectionRate = totalReceived.divide(totalIssued, 4, RoundingMode.HALF_UP)
                    .multiply(BigDecimal.valueOf(100))
                    .setScale(2, RoundingMode.HALF_UP);
        }

        // 3 — Average days to payment
        BigDecimal avgDaysToPayment = computeAvgDaysToPayment(
                paymentRepository.issuedAndReceivedPairs(tenantId));

        // 4 — Aging buckets
        List<Object[]> agingRows = callRepository.outstandingCallsForAging(tenantId, effectiveAgentId);
        AgingBuckets buckets = buildAgingBuckets(agingRows, LocalDate.now());

        // 5 — Overdue by project (top 10)
        List<OverdueByProjectRow> overdueByProject = callRepository
                .overdueByProject(tenantId, PageRequest.of(0, TOP_N))
                .stream()
                .map(r -> new OverdueByProjectRow((UUID) r[0], (String) r[1], toBD(r[2])))
                .toList();

        // 6 — Recent payments (last 10)
        List<RecentPaymentRow> recentPayments = paymentRepository
                .recentPayments(tenantId, effectiveAgentId, PageRequest.of(0, RECENT_N))
                .stream()
                .map(r -> new RecentPaymentRow(
                        (UUID)       r[0],
                        toBD(        r[1]),
                        toLocalDate( r[2]),
                        r[3] instanceof PaymentMethod pm ? pm.name() : String.valueOf(r[3]),
                        (String)     r[4],
                        (String)     r[5],
                        (String)     r[6]
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
            LocalDateTime issuedAt  = (LocalDateTime) row[0];
            Object receivedAtRaw    = row[1];
            LocalDate receivedAt    = toLocalDate(receivedAtRaw);
            if (issuedAt != null && receivedAt != null) {
                total += ChronoUnit.DAYS.between(issuedAt.toLocalDate(), receivedAt);
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
