package com.yem.hlm.backend.payments.api.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Cash KPI summary for the payments dashboard panel.
 *
 * <h3>KPI semantics</h3>
 * <ul>
 *   <li>{@code expectedInPeriod}   — sum of amounts for items with due_date in the window
 *       (status != DRAFT and != CANCELED)</li>
 *   <li>{@code issuedInPeriod}     — sum of amounts for items issued (issued_at) in the window</li>
 *   <li>{@code collectedInPeriod}  — sum of payments received (paid_at) in the window</li>
 *   <li>{@code overdueAmount}      — total remaining on OVERDUE items</li>
 *   <li>{@code overdueCount}       — number of OVERDUE items</li>
 *   <li>{@code agingBuckets}       — overdue items grouped by age (0-30d, 31-60d, 61-90d, 91d+)</li>
 * </ul>
 */
public record CashDashboardResponse(
        LocalDate from,
        LocalDate to,

        BigDecimal expectedInPeriod,
        BigDecimal issuedInPeriod,
        BigDecimal collectedInPeriod,

        BigDecimal overdueAmount,
        long overdueCount,

        List<AgingBucket> agingBuckets,
        List<NextDueItem> nextDueItems
) {
    public record AgingBucket(
            String label,
            BigDecimal totalAmount,
            long itemCount
    ) {}

    public record NextDueItem(
            java.util.UUID itemId,
            java.util.UUID contractId,
            String itemLabel,
            LocalDate dueDate,
            BigDecimal amountRemaining
    ) {}
}
