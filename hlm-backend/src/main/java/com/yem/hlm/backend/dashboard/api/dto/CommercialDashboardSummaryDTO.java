package com.yem.hlm.backend.dashboard.api.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Full commercial dashboard summary — returned by a single backend call.
 *
 * <h3>Query budget</h3>
 * Up to 9 aggregate queries per request (no entity hydration):
 * <ol>
 *   <li>Sales totals (count, sum, avg agreedPrice)</li>
 *   <li>Deposit totals (count, sum amount)</li>
 *   <li>Sales by project — top 10</li>
 *   <li>Sales by agent — top 10</li>
 *   <li>Inventory by property status (current state)</li>
 *   <li>Inventory by property type (current state)</li>
 *   <li>Sales amount by day (trend)</li>
 *   <li>Deposits amount by day (trend)</li>
 *   <li>Contract cycle-time pairs (signedAt, confirmedAt) for avgDaysDepositToSale</li>
 * </ol>
 *
 * <h3>Field notes</h3>
 * <ul>
 *   <li>{@code inventoryByStatus} / {@code inventoryByType} reflect current property state,
 *       optionally filtered by {@code projectId} but NOT by date range.</li>
 *   <li>{@code conversionDepositToSaleRate} = salesCount / depositsCount; null if depositsCount = 0.</li>
 *   <li>{@code avgDaysDepositToSale} = average calendar days from deposit.confirmedAt to contract.signedAt
 *       for contracts that originated from a confirmed deposit (sourceDepositId present); null if none.</li>
 * </ul>
 */
public record CommercialDashboardSummaryDTO(
        // ── Date range applied ──────────────────────────────────────────────
        LocalDateTime from,
        LocalDateTime to,

        // ── Sales totals ────────────────────────────────────────────────────
        long salesCount,
        BigDecimal salesTotalAmount,
        BigDecimal avgSaleValue,

        // ── Deposit totals ──────────────────────────────────────────────────
        long depositsCount,
        BigDecimal depositsTotalAmount,

        // ── Breakdowns (top 10) ─────────────────────────────────────────────
        List<SalesByProjectRow> salesByProject,
        List<SalesByAgentRow>   salesByAgent,

        // ── Inventory (current state, optional projectId filter) ────────────
        Map<String, Long> inventoryByStatus,
        Map<String, Long> inventoryByType,

        // ── Trend series (daily buckets) ────────────────────────────────────
        List<DailySalesPoint> salesAmountByDay,
        List<DailySalesPoint> depositsAmountByDay,

        // ── Conversion (null when no data) ──────────────────────────────────
        BigDecimal conversionDepositToSaleRate,
        BigDecimal avgDaysDepositToSale
) {}
