package com.yem.hlm.backend.dashboard.api.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Receivables dashboard summary — returned by GET /api/dashboard/receivables.
 *
 * <h3>Field notes</h3>
 * <ul>
 *   <li>{@code totalOutstanding} — SUM(amountDue) for ISSUED + OVERDUE calls.</li>
 *   <li>{@code totalOverdue}     — SUM(amountDue) for OVERDUE calls only.</li>
 *   <li>{@code collectionRate}   — totalReceived / totalIssued * 100; null when none issued.</li>
 *   <li>{@code avgDaysToPayment} — average calendar days from call issuedAt to payment receivedAt;
 *       null when no data.</li>
 *   <li>{@code agingBuckets}     — current / days30 / days60 / days90 / days90plus buckets
 *       each with count + amount, based on days since dueDate.</li>
 *   <li>{@code overdueByProject} — top 10 projects by total overdue amount.</li>
 *   <li>{@code recentPayments}   — last 10 payments received.</li>
 * </ul>
 */
public record ReceivablesDashboardDTO(
        LocalDateTime asOf,

        BigDecimal totalOutstanding,
        BigDecimal totalOverdue,

        /** collection rate in percent (0–100), null when nothing has been issued. */
        BigDecimal collectionRate,

        /** average days from call issued_at to payment received_at; null when no data. */
        BigDecimal avgDaysToPayment,

        AgingBucketDTO current,
        AgingBucketDTO days30,
        AgingBucketDTO days60,
        AgingBucketDTO days90,
        AgingBucketDTO days90plus,

        List<OverdueByProjectRow> overdueByProject,
        List<RecentPaymentRow>    recentPayments
) {}
