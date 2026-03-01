package com.yem.hlm.backend.payments.service;

import com.yem.hlm.backend.contact.service.CrossTenantAccessException;
import com.yem.hlm.backend.payments.api.dto.CashDashboardResponse;
import com.yem.hlm.backend.payments.domain.PaymentScheduleItem;
import com.yem.hlm.backend.payments.domain.PaymentScheduleStatus;
import com.yem.hlm.backend.payments.repo.PaymentScheduleItemRepository;
import com.yem.hlm.backend.payments.repo.SchedulePaymentRepository;
import com.yem.hlm.backend.tenant.context.TenantContext;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Computes cash-flow KPIs for a date window and aging analysis for overdue items.
 * Results are cached with a 60-second TTL (keyed on tenantId + from + to).
 */
@Service
public class CashDashboardService {

    private static final int NEXT_DUE_LIMIT = 10;

    private final PaymentScheduleItemRepository itemRepo;
    private final SchedulePaymentRepository     paymentRepo;

    public CashDashboardService(PaymentScheduleItemRepository itemRepo,
                                SchedulePaymentRepository paymentRepo) {
        this.itemRepo    = itemRepo;
        this.paymentRepo = paymentRepo;
    }

    /**
     * Returns the cash dashboard for the current tenant and given date window.
     * Results are cached for 60 seconds by the {@code cashDashboard} cache.
     */
    @Transactional(readOnly = true)
    @Cacheable(value = "cashDashboard",
               key = "#root.target.currentTenantId() + '_' + #from + '_' + #to")
    public CashDashboardResponse getSummary(LocalDate from, LocalDate to) {
        UUID tenantId = requireTenantId();
        return compute(tenantId, from, to);
    }

    /**
     * Called reflectively by the @Cacheable key SpEL to get tenant context.
     */
    public String currentTenantId() {
        UUID id = TenantContext.getTenantId();
        return id != null ? id.toString() : "UNKNOWN";
    }

    // =========================================================================
    // Internal computation
    // =========================================================================

    CashDashboardResponse compute(UUID tenantId, LocalDate from, LocalDate to) {
        // ── Headline aggregates ───────────────────────────────────────────────
        BigDecimal expected  = itemRepo.sumExpectedInPeriod(tenantId, from, to);
        BigDecimal issued    = itemRepo.sumIssuedInPeriod(tenantId, from, to);
        BigDecimal collected = paymentRepo.sumCollectedInPeriod(
                tenantId,
                from.atStartOfDay(),
                to.atTime(23, 59, 59));

        // ── Overdue / aging ───────────────────────────────────────────────────
        long overdueCount = itemRepo.countByTenant_IdAndStatus(tenantId, PaymentScheduleStatus.OVERDUE);
        LocalDate today      = LocalDate.now();
        LocalDate agingStart = today.minusDays(365 * 5L);

        List<PaymentScheduleItem> b0_30  = itemRepo.findOverdueInRange(tenantId, today.minusDays(30),  today);
        List<PaymentScheduleItem> b31_60 = itemRepo.findOverdueInRange(tenantId, today.minusDays(60),  today.minusDays(31));
        List<PaymentScheduleItem> b61_90 = itemRepo.findOverdueInRange(tenantId, today.minusDays(90),  today.minusDays(61));
        List<PaymentScheduleItem> b91p   = itemRepo.findOverdueInRange(tenantId, agingStart,           today.minusDays(91));

        List<UUID> allOverdueIds = new ArrayList<>();
        allOverdueIds.addAll(ids(b0_30));
        allOverdueIds.addAll(ids(b31_60));
        allOverdueIds.addAll(ids(b61_90));
        allOverdueIds.addAll(ids(b91p));

        Map<UUID, BigDecimal> paidByItem = batchPaid(tenantId, allOverdueIds);

        CashDashboardResponse.AgingBucket bucket0 = bucketOf("0-30 jours",  b0_30,  paidByItem);
        CashDashboardResponse.AgingBucket bucket1 = bucketOf("31-60 jours", b31_60, paidByItem);
        CashDashboardResponse.AgingBucket bucket2 = bucketOf("61-90 jours", b61_90, paidByItem);
        CashDashboardResponse.AgingBucket bucket3 = bucketOf("91+ jours",   b91p,   paidByItem);

        BigDecimal overdueAmount = bucket0.totalAmount()
                .add(bucket1.totalAmount())
                .add(bucket2.totalAmount())
                .add(bucket3.totalAmount());

        // ── Next due (upcoming ISSUED/SENT items) ─────────────────────────────
        List<PaymentScheduleItem> upcoming = itemRepo.findUpcomingDue(
                tenantId, today, PageRequest.of(0, NEXT_DUE_LIMIT));

        List<CashDashboardResponse.NextDueItem> nextDue = upcoming.stream()
                .map(i -> {
                    BigDecimal paid      = paidByItem.getOrDefault(i.getId(), BigDecimal.ZERO);
                    BigDecimal remaining = i.getAmount().subtract(paid).max(BigDecimal.ZERO);
                    return new CashDashboardResponse.NextDueItem(
                            i.getId(), i.getContractId(), i.getLabel(), i.getDueDate(), remaining);
                })
                .toList();

        return new CashDashboardResponse(
                from, to,
                expected, issued, collected,
                overdueAmount, overdueCount,
                List.of(bucket0, bucket1, bucket2, bucket3),
                nextDue
        );
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private List<UUID> ids(List<PaymentScheduleItem> items) {
        return items.stream().map(PaymentScheduleItem::getId).toList();
    }

    private Map<UUID, BigDecimal> batchPaid(UUID tenantId, List<UUID> itemIds) {
        if (itemIds.isEmpty()) return Map.of();
        return paymentRepo.sumPaidByItemIds(tenantId, itemIds).stream()
                .collect(Collectors.toMap(
                        row -> (UUID) row[0],
                        row -> (BigDecimal) row[1]
                ));
    }

    private CashDashboardResponse.AgingBucket bucketOf(String label,
                                                        List<PaymentScheduleItem> items,
                                                        Map<UUID, BigDecimal> paidByItem) {
        BigDecimal total = BigDecimal.ZERO;
        for (PaymentScheduleItem item : items) {
            BigDecimal paid      = paidByItem.getOrDefault(item.getId(), BigDecimal.ZERO);
            BigDecimal remaining = item.getAmount().subtract(paid).max(BigDecimal.ZERO);
            total = total.add(remaining);
        }
        return new CashDashboardResponse.AgingBucket(label, total, items.size());
    }

    private UUID requireTenantId() {
        UUID id = TenantContext.getTenantId();
        if (id == null) throw new CrossTenantAccessException("Missing tenant context");
        return id;
    }
}
