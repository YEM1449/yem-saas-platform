package com.yem.hlm.backend.dashboard.api.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Full commercial dashboard summary — returned by a single backend call.
 *
 * <h3>Query budget</h3>
 * Up to 10 aggregate queries per request (no entity hydration):
 * <ol>
 *   <li>Sales totals (count, sum, avg agreedPrice)</li>
 *   <li>Deposit totals in period (count, sum amount, filtered by confirmedAt)</li>
 *   <li>Active reservations snapshot (count, sum, avg age days — NOT date-filtered)</li>
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
 *   <li>{@code activeReservationsCount} / {@code activeReservationsTotalAmount} /
 *       {@code avgReservationAgeDays} — current snapshot of PENDING + CONFIRMED deposits
 *       (not date-filtered); optionally scoped by agentId.</li>
 *   <li>{@code asOf} — server timestamp when this DTO was assembled; use to display data freshness.</li>
 * </ul>
 */
public record CommercialDashboardSummaryDTO(
        // ── Date range applied ──────────────────────────────────────────────
        LocalDateTime from,
        LocalDateTime to,

        // ── Freshness timestamp ─────────────────────────────────────────────
        /** Instant at which this summary was computed (before caching). */
        LocalDateTime asOf,

        // ── Sales totals ────────────────────────────────────────────────────
        long salesCount,
        BigDecimal salesTotalAmount,
        BigDecimal avgSaleValue,

        // ── Deposit totals (period-filtered, CONFIRMED) ─────────────────────
        long depositsCount,
        BigDecimal depositsTotalAmount,

        // ── Active reservations snapshot (current, not date-filtered) ────────
        /** PENDING + CONFIRMED deposits currently open for this tenant (/ agent). */
        long activeReservationsCount,
        BigDecimal activeReservationsTotalAmount,
        /** Average age in days of open reservations; null when none. */
        BigDecimal avgReservationAgeDays,

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
